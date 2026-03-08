package com.ecommerce.paymentservice.dto;

import com.ecommerce.paymentservice.model.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String type,
        String status,
        String gatewayName,
        String gatewayTransactionId,
        BigDecimal amount,
        String message,
        int attemptNumber,
        LocalDateTime createdAt
) {
    public static TransactionResponse fromEntity(PaymentTransaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getType().name(),
                tx.getStatus().name(),
                tx.getGatewayName(),
                tx.getGatewayTransactionId(),
                tx.getAmount(),
                tx.getMessage(),
                tx.getAttemptNumber(),
                tx.getCreatedAt()
        );
    }
}
