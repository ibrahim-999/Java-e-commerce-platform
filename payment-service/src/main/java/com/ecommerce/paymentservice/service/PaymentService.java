package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.exception.PaymentException;
import com.ecommerce.paymentservice.exception.ResourceNotFoundException;
import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.model.PaymentMethod;
import com.ecommerce.paymentservice.model.PaymentStatus;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;

    // Process a new payment for an order.
    //
    // Real-world flow: validate → call payment gateway (Stripe, PayPal) → save result.
    // Our simulated flow: validate → generate fake transaction ID → save as COMPLETED.
    //
    // Key business rule: ONE successful payment per order.
    // If a COMPLETED payment already exists for this orderId, we reject the duplicate.
    // This prevents double-charging — a critical concern in payment systems.
    @Transactional
    public Payment processPayment(CreatePaymentRequest request) {
        // Check for duplicate payment — never charge an order twice
        if (paymentRepository.existsByOrderIdAndStatus(request.getOrderId(), PaymentStatus.COMPLETED)) {
            throw new PaymentException(
                    "A completed payment already exists for order: " + request.getOrderId());
        }

        // Parse the payment method from the string
        PaymentMethod method = parsePaymentMethod(request.getPaymentMethod());

        // Build the payment entity
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .paymentMethod(method)
                .status(PaymentStatus.PENDING)
                .build();

        // Save first as PENDING — in a real system, we'd call the gateway here
        payment = paymentRepository.save(payment);
        log.info("Payment created with PENDING status for order: {}", request.getOrderId());

        // Simulate payment gateway processing.
        // In production, this would be an API call to Stripe/PayPal that could take seconds.
        // The gateway would return a transaction ID (e.g., "pi_3abc..." for Stripe).
        String transactionId = simulatePaymentGateway(payment);

        // Update with the result
        payment.setTransactionId(transactionId);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment = paymentRepository.save(payment);

        log.info("Payment completed for order: {}, transactionId: {}",
                request.getOrderId(), transactionId);

        return payment;
    }

    // Get payment by ID
    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));
    }

    // Get payment by order ID
    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));
    }

    // Get all payments for a user
    public List<Payment> getPaymentsByUserId(Long userId) {
        return paymentRepository.findByUserId(userId);
    }

    // Refund a payment.
    //
    // Business rules:
    // - Only COMPLETED payments can be refunded
    // - A payment can only be refunded once (no double-refunds)
    // In a real system, this would call the gateway's refund API.
    @Transactional
    public Payment refundPayment(Long id) {
        Payment payment = getPaymentById(id);

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new PaymentException("Payment has already been refunded");
        }

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentException(
                    "Only completed payments can be refunded. Current status: " + payment.getStatus());
        }

        // In production: call gateway refund API (Stripe: stripe.refunds.create())
        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);

        log.info("Payment refunded: id={}, orderId={}, amount={}",
                payment.getId(), payment.getOrderId(), payment.getAmount());

        return payment;
    }

    // Simulate a payment gateway call.
    //
    // Real payment gateways (Stripe, PayPal, etc.) return a unique transaction ID
    // after successfully charging the customer. We generate a UUID to simulate this.
    //
    // In production, this method would be replaced by actual gateway SDK calls:
    //   Stripe:  PaymentIntent.create(params) → returns pi_3MtwBwLkdI...
    //   PayPal:  ordersClient.execute(request) → returns PAY-1AB23456CD...
    private String simulatePaymentGateway(Payment payment) {
        // Generate a transaction ID that looks like a real gateway response
        String prefix = switch (payment.getPaymentMethod()) {
            case CREDIT_CARD, DEBIT_CARD -> "txn_card_";
            case PAYPAL -> "txn_pp_";
            case BANK_TRANSFER -> "txn_bt_";
        };
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    // Parse the payment method string to enum.
    // Throws IllegalArgumentException if the method is not supported.
    private PaymentMethod parsePaymentMethod(String method) {
        try {
            return PaymentMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid payment method: " + method +
                    ". Supported methods: CREDIT_CARD, DEBIT_CARD, PAYPAL, BANK_TRANSFER");
        }
    }
}
