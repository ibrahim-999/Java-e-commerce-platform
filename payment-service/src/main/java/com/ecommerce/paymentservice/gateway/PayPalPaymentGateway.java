package com.ecommerce.paymentservice.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

// Simulates PayPal's payment gateway.
//
// In production, this would use the PayPal Java SDK:
//   OrdersCreateRequest request = new OrdersCreateRequest();
//   request.requestBody(orderRequest);
//   HttpResponse<Order> response = client.execute(request);
//   return response.result().id();  // "PAY-1AB23456CD789012EF..."
//
// PayPal handles: PAYPAL payments (buyer logs into PayPal to authorize).
// They charge ~2.9% + 30¢ for US transactions, more for international.

@Component
@Slf4j
public class PayPalPaymentGateway implements PaymentGateway {

    @Override
    public GatewayResponse charge(Long orderId, BigDecimal amount) {
        log.info("[PayPal] Processing payment for order {}, amount: ${}", orderId, amount);

        // Simulate PayPal API call
        String transactionId = "PAY-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 20).toUpperCase();

        log.info("[PayPal] Payment successful: {}", transactionId);
        return GatewayResponse.success(transactionId);
    }

    @Override
    public GatewayResponse refund(String transactionId, BigDecimal amount) {
        log.info("[PayPal] Refunding {} for transaction: {}", amount, transactionId);

        String refundId = "PAYREF-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase();

        log.info("[PayPal] Refund successful: {}", refundId);
        return GatewayResponse.success(refundId);
    }

    @Override
    public String gatewayName() {
        return "PAYPAL";
    }
}
