package com.ecommerce.productservice.exception;

// Custom exception for when someone tries to order more than available stock.
// This is specific to product-service — user-service doesn't need this.

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku, int requested, int available) {
        super(String.format("Insufficient stock for product %s: requested %d, available %d",
                sku, requested, available));
    }
}
