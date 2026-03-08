package com.ecommerce.paymentservice.gateway;

import java.math.BigDecimal;

// Strategy interface for payment gateways.
//
// Each payment gateway (Stripe, PayPal, bank API) implements this interface.
// The PaymentService doesn't know or care WHICH gateway processes the payment —
// it just calls gateway.charge() and gateway.refund().
//
// This is the Strategy Pattern:
//   - Interface defines WHAT to do (charge, refund)
//   - Each implementation decides HOW to do it
//   - The caller (PaymentService) picks the right strategy at runtime
//
// Adding a new gateway (e.g., Apple Pay) requires:
//   1. Create ApplePayGateway implements PaymentGateway
//   2. Register it in PaymentGatewayFactory
//   That's it — no changes to PaymentService.

public interface PaymentGateway {

    // Process a payment and return a transaction ID from the gateway.
    // In production, this would make an HTTP call to the gateway's API.
    //
    // @param orderId  — reference to the order being paid
    // @param amount   — how much to charge
    // @return the gateway's transaction ID (e.g., "pi_3abc..." for Stripe)
    // @throws PaymentGatewayException if the charge fails
    GatewayResponse charge(Long orderId, BigDecimal amount);

    // Refund a previously completed payment.
    //
    // @param transactionId — the original transaction ID to refund
    // @param amount        — how much to refund (could be partial in a real system)
    // @return the gateway's refund confirmation
    GatewayResponse refund(String transactionId, BigDecimal amount);

    // Which payment methods this gateway supports.
    // Used by the factory to route payments to the correct gateway.
    String gatewayName();
}
