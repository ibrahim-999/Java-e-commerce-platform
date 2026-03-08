package com.ecommerce.orderservice.exception;

// Thrown when a downstream service (user-service, product-service) is unreachable.
// The Circuit Breaker catches communication failures and throws this instead.

public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String serviceName) {
        super(String.format("%s is currently unavailable. Please try again later.", serviceName));
    }
}
