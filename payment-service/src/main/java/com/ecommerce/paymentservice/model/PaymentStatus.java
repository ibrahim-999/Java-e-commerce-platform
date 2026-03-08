package com.ecommerce.paymentservice.model;

public enum PaymentStatus {
    PENDING,     // payment initiated, awaiting processing
    COMPLETED,   // payment successfully processed
    FAILED,      // payment was rejected (insufficient funds, card declined, etc.)
    REFUNDED     // payment was refunded after completion
}
