package com.ecommerce.paymentservice.gateway;

// Immutable result from a payment gateway call.
//
// Why a record instead of just returning a String transactionId?
// Because real gateways return more than just an ID:
//   - success/failure status
//   - error messages (e.g., "Card declined", "Insufficient funds")
//   - the transaction ID (only if successful)
//
// This gives us a clean way to handle both success and failure
// without relying on exceptions for control flow.

public record GatewayResponse(
        boolean success,
        String transactionId,
        String message
) {
    public static GatewayResponse success(String transactionId) {
        return new GatewayResponse(true, transactionId, "Payment processed successfully");
    }

    public static GatewayResponse failure(String message) {
        return new GatewayResponse(false, null, message);
    }
}
