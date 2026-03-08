package com.ecommerce.paymentservice.model;

// The type of operation a payment transaction represents.
public enum TransactionType {
    CHARGE,    // attempt to charge the customer
    REFUND     // attempt to refund a previous charge
}
