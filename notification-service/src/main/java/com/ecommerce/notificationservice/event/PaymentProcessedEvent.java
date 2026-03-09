package com.ecommerce.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Represents the event: "A payment was processed (success or failure)."
//
// Published by: payment-service (after charge attempt completes)
// Consumed by: notification-service (to send payment confirmation or failure notice)

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentProcessedEvent {
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String status;         // COMPLETED, FAILED, or REFUNDED
    private String paymentMethod;  // CREDIT_CARD, PAYPAL, BANK_TRANSFER
    private String transactionId;  // gateway transaction ID (null if failed)
}
