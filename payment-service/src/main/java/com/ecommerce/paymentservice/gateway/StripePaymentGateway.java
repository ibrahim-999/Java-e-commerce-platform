package com.ecommerce.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

// Simulates Stripe's payment gateway.
//
// In production, this would use the Stripe Java SDK:
//   Stripe.apiKey = "sk_live_...";
//   PaymentIntent intent = PaymentIntent.create(params);
//   return intent.getId();  // "pi_3MtwBwLkdIJust..."
//
// Stripe handles: CREDIT_CARD and DEBIT_CARD payments.
// They charge ~2.9% + 30¢ per transaction.

@Component
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    @Override
    public GatewayResponse charge(Long orderId, BigDecimal amount) {
        log.info("[Stripe] Processing card payment for order {}, amount: ${}", orderId, amount);

        // Simulate Stripe API call
        // In production: PaymentIntent.create(PaymentIntentCreateParams.builder()
        //     .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue())  // Stripe uses cents
        //     .setCurrency("usd")
        //     .build());
        String transactionId = "pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        log.info("[Stripe] Payment successful: {}", transactionId);
        return GatewayResponse.success(transactionId);
    }

    @Override
    public GatewayResponse refund(String transactionId, BigDecimal amount) {
        log.info("[Stripe] Refunding {} for transaction: {}", amount, transactionId);

        // In production: Refund.create(RefundCreateParams.builder()
        //     .setPaymentIntent(transactionId)
        //     .build());
        String refundId = "re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        log.info("[Stripe] Refund successful: {}", refundId);
        return GatewayResponse.success(refundId);
    }

    @Override
    public String gatewayName() {
        return "STRIPE";
    }
}
