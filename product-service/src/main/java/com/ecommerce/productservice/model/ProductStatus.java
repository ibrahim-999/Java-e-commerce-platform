package com.ecommerce.productservice.model;

public enum ProductStatus {
    ACTIVE,       // available for purchase
    INACTIVE,     // hidden from catalog (draft or disabled)
    OUT_OF_STOCK  // stock quantity is zero
}
