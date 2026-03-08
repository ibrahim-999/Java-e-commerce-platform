# Payment Architecture & Production Concerns

> Real-world payment problems we identified and solved in this project.
> Each section explains the problem, why it matters, and how we solved it — from our scale to Stripe/PayPal scale.

---

## 1. Retry with Exponential Backoff (Gateway Failures)

### The Problem

Payment gateways (Stripe, PayPal, bank APIs) are external services. They fail:

```
Your Server  ──HTTP──►  Stripe API
                           │
                    ┌──────┴──────┐
                    │  Timeout    │  ← network blip (1-2 seconds)
                    │  503 Error  │  ← Stripe is overloaded
                    │  Connection │  ← DNS resolution failed
                    │  Refused    │
                    └─────────────┘
```

If you give up immediately, the customer sees "Payment failed" even though
Stripe would have worked 2 seconds later. That's a lost sale.

### The Naive Fix (and why it's bad)

```java
// BAD — fixed-interval retry
for (int i = 0; i < 3; i++) {
    try { return gateway.charge(amount); }
    catch (Exception e) { Thread.sleep(1000); }  // 1s, 1s, 1s
}
```

Problem: if Stripe is overloaded, hammering it every second makes things worse.
You're adding load to an already struggling service. This is called a **thundering herd**.

### The Solution: Exponential Backoff

Wait longer between each retry: 1s → 2s → 4s.

```
Attempt 1: call gateway → FAIL
   wait 1 second
Attempt 2: call gateway → FAIL
   wait 2 seconds (doubled)
Attempt 3: call gateway → SUCCESS ✅
```

Why exponential? If the gateway needs time to recover, you give it progressively
more breathing room. By the 3rd attempt, it's likely recovered.

### What We Implemented

```java
public GatewayResponse chargeWithRetry(PaymentGateway gateway, ...) {
    int maxAttempts = 3;
    long waitMs = 1000;  // start at 1 second

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        GatewayResponse response = gateway.charge(orderId, amount);

        // Log EVERY attempt (success or failure) for audit trail
        logTransaction(paymentId, type, status, gatewayName, ...);

        if (response.success()) return response;

        // Exponential backoff: 1s → 2s → 4s
        Thread.sleep(waitMs);
        waitMs *= 2;
    }
    return GatewayResponse.failure("All payment attempts failed");
}
```

Key decisions:
- **Manual retry loop** instead of `@Retry` annotation — because we need to LOG each attempt
- **Each attempt creates a `PaymentTransaction`** — audit trail shows every gateway interaction
- **Payment marked `FAILED`** after exhausting all retries — NOT thrown away, stays in DB for investigation
- **Resilience4j config** in `application.properties`: `maxAttempts=3`, `exponentialBackoffMultiplier=2`

### At Stripe/PayPal Scale

- **Idempotency keys**: Stripe accepts an `Idempotency-Key` header. If you retry with the same key, Stripe returns the same result without double-charging. Critical for network timeouts where you don't know if the first attempt succeeded.
- **Circuit breaker + retry**: If Stripe is down for 5+ minutes, stop retrying (circuit breaker opens). When it comes back, allow test requests (half-open).
- **Webhook fallback**: If your retry fails, Stripe can notify you later via webhook when payment completes asynchronously.

---

## 2. Strategy + Factory Pattern for Multiple Gateways

### The Problem

Different payment methods need different gateway APIs:

```
CREDIT_CARD  → Stripe API   → returns "pi_3abc..."
PAYPAL       → PayPal API   → returns "PAY-1AB..."
BANK_TRANSFER → Bank API    → returns "ACH-XY12..."
```

Without a pattern, this becomes an unmaintainable if/else chain:

```java
// BAD — every new gateway adds another if/else
if (method == CREDIT_CARD) {
    callStripe(amount);
} else if (method == PAYPAL) {
    callPayPal(amount);
} else if (method == BANK_TRANSFER) {
    callBank(amount);
} else if (method == APPLE_PAY) {   // ← new gateway = edit this method
    callApplePay(amount);
}
```

This violates the **Open/Closed Principle** — every new gateway modifies existing code.

### The Solution: Strategy Pattern + Factory Pattern

Two patterns working together:

```
PaymentGateway (interface)          ← Strategy: defines WHAT to do
├── StripePaymentGateway            ← Strategy: decides HOW (Stripe way)
├── PayPalPaymentGateway            ← Strategy: decides HOW (PayPal way)
└── BankTransferGateway             ← Strategy: decides HOW (bank way)

PaymentGatewayFactory               ← Factory: decides WHICH strategy
└── Map<PaymentMethod, PaymentGateway>
```

### What We Implemented

**Strategy Interface** — one method to charge, one to refund:

```java
public interface PaymentGateway {
    GatewayResponse charge(Long orderId, BigDecimal amount);
    GatewayResponse refund(String transactionId, BigDecimal amount);
    String gatewayName();
}
```

**Three Implementations** — each simulates a real gateway:

| Gateway | Handles | Transaction ID Format | Real-world SDK |
|---------|---------|----------------------|----------------|
| `StripePaymentGateway` | CREDIT_CARD, DEBIT_CARD | `pi_...` | `stripe-java` |
| `PayPalPaymentGateway` | PAYPAL | `PAY-...` | `checkout-sdk` |
| `BankTransferGateway` | BANK_TRANSFER | `ACH-...` | `plaid-java` or `dwolla-java` |

**Factory** — maps payment methods to gateways:

```java
@Component
public class PaymentGatewayFactory {
    private final Map<PaymentMethod, PaymentGateway> gateways;

    public PaymentGatewayFactory(StripePaymentGateway stripe,
                                  PayPalPaymentGateway paypal,
                                  BankTransferGateway bank) {
        this.gateways = Map.of(
            CREDIT_CARD, stripe,
            DEBIT_CARD, stripe,    // same gateway, different method
            PAYPAL, paypal,
            BANK_TRANSFER, bank
        );
    }

    public PaymentGateway getGateway(PaymentMethod method) {
        return gateways.get(method);
    }
}
```

**Adding a new gateway** (e.g., Apple Pay) requires:
1. Create `ApplePayGateway implements PaymentGateway`
2. Add one line in `PaymentGatewayFactory`: `APPLE_PAY, applePayGateway`
3. **No changes to `PaymentService`** — it doesn't know or care which gateway runs

### GatewayResponse — Why not just return a String?

```java
public record GatewayResponse(
    boolean success,
    String transactionId,    // null if failed
    String message           // "Card declined", "Insufficient funds", etc.
) {}
```

Real gateways don't always succeed. A declined card isn't an exception — it's a normal
business outcome. Using `GatewayResponse` instead of exceptions means we handle
success/failure as data, not as control flow.

---

## 3. Transaction History (Audit Trail)

### The Problem

A customer contacts support: "I was charged twice!" or "My payment failed but my account was debited."

Without a transaction log, you can't answer these questions:
- How many times did we try to charge this customer?
- Which gateway processed it?
- What error message did the gateway return?
- When exactly did the refund happen?

### The Solution: `PaymentTransaction` entity

Every gateway interaction — success or failure — is recorded as a `PaymentTransaction`:

```
Payment #42 (order #10, $99.99, CREDIT_CARD)
├── Transaction 1: CHARGE | FAILED  | STRIPE | "Card declined"          | attempt 1
├── Transaction 2: CHARGE | FAILED  | STRIPE | "Insufficient funds"     | attempt 2
├── Transaction 3: CHARGE | SUCCESS | STRIPE | pi_3abc... | "Processed" | attempt 3
└── Transaction 4: REFUND | SUCCESS | STRIPE | re_7xyz... | "Refunded"  | attempt 1
```

### What We Implemented

**Entity:**

```java
@Entity
public class PaymentTransaction {
    private Payment payment;           // which payment this belongs to
    private TransactionType type;      // CHARGE or REFUND
    private TransactionStatus status;  // SUCCESS or FAILED
    private String gatewayName;        // "STRIPE", "PAYPAL", etc.
    private String gatewayTransactionId;  // null if failed
    private BigDecimal amount;
    private String message;            // error reason or success message
    private int attemptNumber;         // 1, 2, 3 (for retries)
    private LocalDateTime createdAt;
}
```

**Endpoint:**

```
GET /api/payments/{id}/transactions → returns full audit trail
```

**Logged automatically:**
- Every charge attempt (including retries)
- Every refund attempt
- Success AND failure outcomes

### Why This Matters

1. **Customer support**: "Was I charged?" → look at the transaction log
2. **Dispute resolution**: Card company asks for proof → show the log
3. **Debugging**: "Why did payment fail?" → see the exact gateway error
4. **Compliance**: PCI-DSS and SOX regulations require audit trails for financial transactions
5. **Analytics**: "What's our card decline rate?" → `SELECT COUNT(*) WHERE status='FAILED' AND type='CHARGE'`

---

## 4. Order Status History (Audit Trail)

### The Problem

An order goes through multiple states:

```
PENDING → CONFIRMED → SHIPPED → DELIVERED
                    → CANCELLED (at any point before delivery)
```

Without a history table, you only know the CURRENT status. You can't answer:
- When did this order ship?
- How long did it take from order to delivery?
- Who cancelled this order and when?
- Was this order ever confirmed before being cancelled?

### The Solution: `OrderStatusHistory` entity

Every status change is recorded with a timestamp and reason:

```
Order #15 (user #3, $1,999.98)
├── null      → PENDING   | "Order placed"                          | 2024-03-08 10:00:00
├── PENDING   → CONFIRMED | "Payment completed, paymentId: 42"     | 2024-03-08 10:00:03
├── CONFIRMED → SHIPPED   | "Shipped via FedEx #123456"            | 2024-03-09 14:30:00
└── SHIPPED   → DELIVERED | "Package delivered"                     | 2024-03-12 09:15:00
```

### What We Implemented

**Entity:**

```java
@Entity
public class OrderStatusHistory {
    private Order order;
    private OrderStatus fromStatus;  // null for initial creation
    private OrderStatus toStatus;
    private String reason;
    private LocalDateTime changedAt;
}
```

**Helper method on Order** — replaces direct `setStatus()` calls:

```java
public void changeStatus(OrderStatus newStatus, String reason) {
    OrderStatus oldStatus = this.status;
    this.status = newStatus;

    OrderStatusHistory history = OrderStatusHistory.builder()
        .order(this).fromStatus(oldStatus)
        .toStatus(newStatus).reason(reason).build();
    statusHistory.add(history);
}
```

**Endpoint:**

```
GET /api/orders/{id}/history → returns chronological status changes
```

**Every status change in the code now goes through `changeStatus()`:**
- `order.changeStatus(PENDING, "Order placed")`
- `order.changeStatus(CONFIRMED, "Payment completed, paymentId: " + id)`
- `order.changeStatus(CANCELLED, "Order cancelled by user")`

### Why This Matters

1. **Customer support**: "When did my order ship?" → query the history
2. **SLA tracking**: "Average time from order to delivery?" → `AVG(delivered.changedAt - pending.changedAt)`
3. **Dispute resolution**: "Was this delivered?" → history shows the exact timestamp
4. **Business intelligence**: "What percentage of orders get cancelled?" → analytics on history data
5. **Compliance**: Financial regulations require a full audit trail

---

## 5. Duplicate Payment Prevention

### The Problem

Network issues can cause duplicate payment submissions:

```
User clicks "Pay" → request to server → Stripe charges $99 → response lost (timeout)
User clicks "Pay" again → request to server → Stripe charges $99 AGAIN → $198 total ❌
```

### The Solution

```java
if (paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.COMPLETED)) {
    throw new PaymentException("A completed payment already exists for order: " + orderId);
}
```

Before processing any payment, we check if a `COMPLETED` payment already exists for that order.
One order = one successful payment. No double-charging.

### At Production Scale

- **Database-level uniqueness**: `UNIQUE INDEX ON payments(order_id) WHERE status = 'COMPLETED'` — partial unique index in PostgreSQL. Even if the application check fails, the database prevents duplicates.
- **Idempotency keys**: Client sends a unique key with each request. Server checks if that key was already processed. Same key = same result returned (no re-processing).

---

## Summary Table

| Concern | Our Solution (Learning Scale) | Production Solution (Stripe Scale) |
|---------|-------------------------------|-----------------------------------|
| Gateway failures | Manual retry loop with exponential backoff (1s→2s→4s), 3 attempts | Idempotency keys + @Retry + Circuit Breaker + webhook fallback |
| Multiple gateways | Strategy pattern + Factory (Stripe, PayPal, Bank) | Same pattern + gateway abstraction layer + failover between gateways |
| Transaction audit | `PaymentTransaction` entity, logs every attempt | Immutable event log (event sourcing) + compliance reporting |
| Order audit | `OrderStatusHistory` entity, tracks every status change | Event sourcing + CQRS + real-time dashboards |
| Duplicate payments | `existsByOrderIdAndStatus` check before processing | Partial unique index + idempotency keys + distributed locks |
| Payment + order sync | Order stays PENDING if payment fails, retry later | Saga pattern with choreography or orchestration |

---

## File Reference

| File | What It Does |
|------|-------------|
| `gateway/PaymentGateway.java` | Strategy interface — charge() and refund() |
| `gateway/StripePaymentGateway.java` | Simulates Stripe (cards) — returns `pi_...` |
| `gateway/PayPalPaymentGateway.java` | Simulates PayPal — returns `PAY-...` |
| `gateway/BankTransferGateway.java` | Simulates bank transfer — returns `ACH-...` |
| `gateway/PaymentGatewayFactory.java` | Maps PaymentMethod → correct gateway |
| `gateway/GatewayResponse.java` | Result record (success/failure + transactionId) |
| `model/PaymentTransaction.java` | Audit trail entity — every gateway attempt logged |
| `model/TransactionType.java` | CHARGE or REFUND |
| `model/TransactionStatus.java` | SUCCESS or FAILED |
| `service/PaymentService.java` | Orchestrates: validate → select gateway → retry → log |
| `db/migration/V2__create_payment_transactions_table.sql` | Transaction history table + indexes |
