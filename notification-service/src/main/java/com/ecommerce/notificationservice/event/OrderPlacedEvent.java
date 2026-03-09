package com.ecommerce.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Represents the event: "A new order was placed."
//
// Published by: order-service (after order is created and payment succeeds)
// Consumed by: notification-service (to send an order confirmation)

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderPlacedEvent {
    private Long orderId;
    private Long userId;
    private BigDecimal totalAmount;
    private String status;         // CONFIRMED or PENDING
    private int itemCount;         // how many items in the order
}
