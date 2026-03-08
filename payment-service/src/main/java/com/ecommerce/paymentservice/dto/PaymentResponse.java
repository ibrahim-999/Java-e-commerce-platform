package com.ecommerce.paymentservice.dto;

import com.ecommerce.paymentservice.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long orderId,
        Long userId,
        BigDecimal amount,
        String status,
        String paymentMethod,
        String transactionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PaymentResponse fromEntity(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getPaymentMethod().name(),
                payment.getTransactionId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
