package com.ecommerce.paymentservice.model;

// The result of a single gateway attempt.
public enum TransactionStatus {
    SUCCESS,   // gateway accepted the charge/refund
    FAILED     // gateway rejected (declined, timeout, error)
}
