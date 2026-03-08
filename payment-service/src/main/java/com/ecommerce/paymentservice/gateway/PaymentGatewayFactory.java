package com.ecommerce.paymentservice.gateway;

import com.ecommerce.paymentservice.model.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.Map;

// Factory that selects the correct payment gateway based on payment method.
//
// This combines TWO patterns:
//   1. Strategy Pattern — each gateway is a strategy (different algorithm, same interface)
//   2. Factory Pattern  — this class decides WHICH strategy to use
//
// The Map-based approach (same as our UserSearchService) means:
//   - No if/else or switch chains that grow with each new gateway
//   - Adding Apple Pay = create ApplePayGateway + add one line here
//   - Easy to test — just inject the factory and verify the right gateway is returned
//
// Why not just use a switch in PaymentService?
//   - Single Responsibility: PaymentService handles business logic, Factory handles routing
//   - Open/Closed Principle: adding a gateway doesn't modify existing code
//   - Testability: you can mock the factory to return a test gateway

@Component
public class PaymentGatewayFactory {

    private final Map<PaymentMethod, PaymentGateway> gateways;

    // Spring injects all PaymentGateway @Component beans automatically.
    // We build a lookup map: PaymentMethod → Gateway.
    public PaymentGatewayFactory(
            StripePaymentGateway stripeGateway,
            PayPalPaymentGateway paypalGateway,
            BankTransferGateway bankTransferGateway) {

        // Stripe handles both credit and debit cards
        this.gateways = Map.of(
                PaymentMethod.CREDIT_CARD, stripeGateway,
                PaymentMethod.DEBIT_CARD, stripeGateway,
                PaymentMethod.PAYPAL, paypalGateway,
                PaymentMethod.BANK_TRANSFER, bankTransferGateway
        );
    }

    // Get the gateway for a payment method.
    // Throws if no gateway is configured (shouldn't happen if all methods are mapped).
    public PaymentGateway getGateway(PaymentMethod method) {
        PaymentGateway gateway = gateways.get(method);
        if (gateway == null) {
            throw new IllegalArgumentException("No payment gateway configured for method: " + method);
        }
        return gateway;
    }
}
