package com.ecommerce.paymentservice.controller;

import com.ecommerce.paymentservice.dto.ApiResponse;
import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.dto.PaymentResponse;
import com.ecommerce.paymentservice.dto.TransactionResponse;
import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // POST /api/payments — process a new payment
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @Valid @RequestBody CreatePaymentRequest request) {

        Payment payment = paymentService.processPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment processed successfully",
                        PaymentResponse.fromEntity(payment)));
    }

    // GET /api/payments/{id} — get payment by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(@PathVariable Long id) {
        Payment payment = paymentService.getPaymentById(id);
        return ResponseEntity.ok(ApiResponse.success(PaymentResponse.fromEntity(payment)));
    }

    // GET /api/payments/order/{orderId} — get payment for an order
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrderId(
            @PathVariable Long orderId) {

        Payment payment = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.success(PaymentResponse.fromEntity(payment)));
    }

    // GET /api/payments/user/{userId} — get all payments for a user
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByUserId(
            @PathVariable Long userId) {

        List<PaymentResponse> payments = paymentService.getPaymentsByUserId(userId).stream()
                .map(PaymentResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(payments));
    }

    // GET /api/payments/{id}/transactions — get transaction history (audit trail)
    @GetMapping("/{id}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionHistory(
            @PathVariable Long id) {

        List<TransactionResponse> transactions = paymentService.getTransactionHistory(id).stream()
                .map(TransactionResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(transactions));
    }

    // PUT /api/payments/{id}/refund — refund a payment
    @PutMapping("/{id}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(@PathVariable Long id) {
        Payment payment = paymentService.refundPayment(id);
        return ResponseEntity.ok(ApiResponse.success("Payment refunded successfully",
                PaymentResponse.fromEntity(payment)));
    }
}
