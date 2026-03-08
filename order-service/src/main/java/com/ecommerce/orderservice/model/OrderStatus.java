package com.ecommerce.orderservice.model;

public enum OrderStatus {
    PENDING,      // order created, awaiting payment
    CONFIRMED,    // payment received, preparing to ship
    SHIPPED,      // on its way to the customer
    DELIVERED,    // customer received the order
    CANCELLED     // order was cancelled (stock restored)
}
