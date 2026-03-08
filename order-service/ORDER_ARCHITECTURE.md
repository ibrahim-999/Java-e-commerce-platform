# Order Architecture & Production Concerns

> Real-world order orchestration problems we identified and solved in this project.
> Each section explains the problem, why it matters, and how we solved it.

---

## 1. Order Status History (Audit Trail)

### The Problem

An order moves through multiple states over its lifetime:

```
PENDING → CONFIRMED → SHIPPED → DELIVERED
                    → CANCELLED
```

Without a history table, you only know the CURRENT status. You lose critical information:
- When did this order ship?
- How long from order to delivery?
- Was this order confirmed before being cancelled?
- Who/what triggered each status change?

Customer calls support: "When did my order ship?" — without history, you can't answer.

### The Solution: `OrderStatusHistory` entity

Every status change is permanently recorded:

```
Order #15 (user #3, $1,999.98)
├── null      → PENDING   | "Order placed"                      | 2024-03-08 10:00:00
├── PENDING   → CONFIRMED | "Payment completed, paymentId: 42"  | 2024-03-08 10:00:03
├── CONFIRMED → SHIPPED   | "Shipped via FedEx #123456"         | 2024-03-09 14:30:00
└── SHIPPED   → DELIVERED | "Package delivered"                  | 2024-03-12 09:15:00
```

### What We Implemented

**Helper method replaces direct `setStatus()` calls:**

```java
// Every status change goes through this — guarantees an audit entry
public void changeStatus(OrderStatus newStatus, String reason) {
    OrderStatus oldStatus = this.status;
    this.status = newStatus;

    OrderStatusHistory history = OrderStatusHistory.builder()
        .order(this).fromStatus(oldStatus)
        .toStatus(newStatus).reason(reason).build();
    statusHistory.add(history);  // cascade saves it automatically
}
```

**All status changes in the code now use `changeStatus()`:**

```java
// Creating a new order
order.changeStatus(OrderStatus.PENDING, "Order placed");

// After successful payment
order.changeStatus(OrderStatus.CONFIRMED, "Payment completed, paymentId: " + paymentId);

// User cancels
order.changeStatus(OrderStatus.CANCELLED, "Order cancelled by user");
```

**Endpoint:**

```
GET /api/orders/{id}/history → returns chronological status changes
```

---

## 2. Compensating Transactions (Distributed Rollback)

### The Problem

In a monolith, if something fails, you roll back the database transaction. Done.

In microservices, an order creation touches THREE services:

```
Step 1: user-service     → validate user exists
Step 2: product-service  → get prices + reduce stock (for each item)
Step 3: payment-service  → charge the customer
```

If Step 2 fails on the second item, the first item's stock was already reduced.
There's no single database transaction to roll back — each service has its own database.

```
Product A: stock 10 → 9 ✅ (reduced)
Product B: stock 0 → FAIL (out of stock)
Product A: stock is still 9 ← WRONG, should be 10
```

### The Solution: Compensating Transactions

If something fails, undo what was already done by calling the reverse operations:

```java
try {
    for (OrderItemRequest item : request.getItems()) {
        getProduct(item.productId());          // validate
        reduceProductStock(item.productId());   // reserve stock
        reservedProductIds.add(item.productId());
    }
} catch (Exception e) {
    // COMPENSATING TRANSACTION — undo all reserved stock
    for (int i = 0; i < reservedProductIds.size(); i++) {
        restoreProductStock(reservedProductIds.get(i), reservedQuantities.get(i));
    }
    throw e;
}
```

### What We Implemented

- Track which products had stock reduced (`reservedProductIds` + `reservedQuantities`)
- On failure: loop through and call `restoreProductStock()` for each
- If `restoreProductStock()` itself fails: log it as CRITICAL for manual intervention
- The original exception is re-thrown so the caller knows the order failed

### At Amazon/Alibaba Scale

- **Saga Pattern**: Each step publishes an event. If a step fails, previous steps listen for the failure event and undo their work. This is done asynchronously via Kafka, not synchronously via HTTP calls.
- **Choreography**: Each service knows how to undo itself when it receives a rollback event.
- **Orchestration**: A central "saga orchestrator" coordinates the steps and rollbacks.

---

## 3. Payment Integration (Order ↔ Payment Service)

### The Problem

Order creation and payment processing are separate concerns:

```
Create Order → Reserve Stock → Charge Customer
                                    ↓
                              What if this fails?
```

Two approaches, each with tradeoffs:

| Approach | If payment fails... | Risk |
|----------|-------------------|------|
| Atomic (all or nothing) | Cancel order, restore stock | Lost sale — user must start over |
| Separate (order first) | Keep order as PENDING | Stock is reserved but not paid for |

### Our Solution: Order First, Payment Second

```java
// Save order as PENDING first
order = orderRepository.save(order);

try {
    // Initiate payment — may succeed or fail
    JsonNode result = initiatePayment(orderId, userId, amount, method);

    if ("COMPLETED".equals(result.status)) {
        order.changeStatus(CONFIRMED, "Payment completed");
    }
} catch (Exception e) {
    // Payment failed — order stays PENDING
    log.error("Payment failed for order {}: {}", order.getId(), e.getMessage());
}
```

**Why this approach:**
- Stock is reserved (no one else can buy it)
- The order exists in the database (not lost)
- The user can retry payment later (like Amazon's "Update payment method")
- An admin can investigate and resolve manually

### Circuit Breaker for Payment Service

```java
@CircuitBreaker(name = "paymentService", fallbackMethod = "initiatePaymentFallback")
public JsonNode initiatePayment(Long orderId, Long userId, BigDecimal amount, String method) {
    return paymentServiceClient.post()
        .uri("/api/payments")
        .bodyValue(paymentRequest)
        .retrieve()
        .bodyToMono(JsonNode.class)
        .block();
}
```

If payment-service is down:
- First 5 failures → circuit breaker stays CLOSED (calls go through)
- After 50% failure rate → circuit OPENS (calls are blocked, fallback runs immediately)
- After 10 seconds → HALF_OPEN (2 test calls allowed through)
- If test calls succeed → circuit CLOSES again

---

## 4. Price Snapshotting

### The Problem

User sees a product at $99. Adds to cart. Seller changes price to $129.
User checks out. What price do they pay?

Worse: a dispute happens 3 months later. The product price has changed 10 times since then.
You need to prove exactly what the customer paid.

### The Solution

```java
public class OrderItem {
    private Long productId;
    private BigDecimal priceAtPurchase;    // frozen at order time — NEVER changes
    private String productNameSnapshot;    // frozen — even if product is renamed
}
```

At checkout, the order-service:
1. Calls product-service to get the CURRENT price
2. Stores that price as `priceAtPurchase` in the order item
3. This snapshot is permanent — it doesn't change even if the product price changes later

---

## Summary Table

| Concern | Our Solution | Production Solution |
|---------|-------------|-------------------|
| Status tracking | `OrderStatusHistory` entity + `changeStatus()` helper | Event sourcing + CQRS |
| Distributed rollback | Manual compensating transactions (restore stock on failure) | Saga pattern (Kafka choreography/orchestration) |
| Payment failure | Order stays PENDING, user retries later | Saga + async payment webhooks |
| Service unavailability | Circuit Breaker (Resilience4j) — 3 states | Service mesh (Istio) + Circuit Breaker + bulkheads |
| Price consistency | `priceAtPurchase` snapshot in OrderItem | Same + Redis cache + price lock at checkout |

---

## File Reference

| File | What It Does |
|------|-------------|
| `model/OrderStatusHistory.java` | Audit trail entity — every status change |
| `model/Order.java` | `changeStatus()` helper records history automatically |
| `repository/OrderStatusHistoryRepository.java` | Query history by order ID |
| `dto/OrderStatusHistoryResponse.java` | Record for API responses |
| `service/OrderService.java` | Orchestrates: validate → reserve → pay → record history |
| `config/WebClientConfig.java` | WebClient beans for user, product, and payment services |
| `db/migration/V2__add_payment_id_to_orders.sql` | Links orders to payments |
| `db/migration/V3__create_order_status_history.sql` | Status history table + index |
