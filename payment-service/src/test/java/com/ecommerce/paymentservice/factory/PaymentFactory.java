package com.ecommerce.paymentservice.factory;

import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.model.PaymentMethod;
import com.ecommerce.paymentservice.model.PaymentStatus;

import java.math.BigDecimal;

// Factory for creating test objects.
// Keeps test code clean — one place to define default values.
public class PaymentFactory {

    public static Payment createPayment() {
        return Payment.builder()
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("99.99"))
                .status(PaymentStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .transactionId("txn_card_abc123")
                .build();
    }

    public static Payment createPaymentWithId(Long id) {
        Payment payment = createPayment();
        payment.setId(id);
        return payment;
    }

    public static Payment createPendingPayment() {
        return Payment.builder()
                .orderId(2L)
                .userId(1L)
                .amount(new BigDecimal("49.99"))
                .status(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.PAYPAL)
                .build();
    }

    public static CreatePaymentRequest createPaymentRequest() {
        return CreatePaymentRequest.builder()
                .orderId(1L)
                .userId(1L)
                .amount(new BigDecimal("99.99"))
                .paymentMethod("CREDIT_CARD")
                .build();
    }

    public static CreatePaymentRequest createPaymentRequest(Long orderId, Long userId) {
        return CreatePaymentRequest.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(new BigDecimal("99.99"))
                .paymentMethod("CREDIT_CARD")
                .build();
    }
}
