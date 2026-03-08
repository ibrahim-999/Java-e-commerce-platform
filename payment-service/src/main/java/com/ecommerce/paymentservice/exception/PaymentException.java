package com.ecommerce.paymentservice.exception;

// Thrown when a payment operation fails (duplicate payment, invalid refund, etc.)

public class PaymentException extends RuntimeException {
    public PaymentException(String message) {
        super(message);
    }
}
