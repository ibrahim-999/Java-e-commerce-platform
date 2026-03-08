package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.exception.PaymentException;
import com.ecommerce.paymentservice.exception.ResourceNotFoundException;
import com.ecommerce.paymentservice.gateway.GatewayResponse;
import com.ecommerce.paymentservice.gateway.PaymentGateway;
import com.ecommerce.paymentservice.gateway.PaymentGatewayFactory;
import com.ecommerce.paymentservice.model.*;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.repository.PaymentTransactionRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

// PaymentService — now uses the Strategy pattern for gateway selection
// and Resilience4j @Retry for automatic retry with exponential backoff.
//
// Flow:
//   1. Validate (no duplicate payment)
//   2. Save payment as PENDING
//   3. Select the correct gateway via PaymentGatewayFactory
//   4. Call gateway.charge() — with retry on failure (up to 3 attempts)
//   5. Log EVERY attempt as a PaymentTransaction (success or failure)
//   6. Update payment status based on result

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentGatewayFactory gatewayFactory;

    // ==================== PROCESS PAYMENT ====================

    @Transactional
    public Payment processPayment(CreatePaymentRequest request) {
        // Check for duplicate payment — never charge an order twice
        if (paymentRepository.existsByOrderIdAndStatus(request.getOrderId(), PaymentStatus.COMPLETED)) {
            throw new PaymentException(
                    "A completed payment already exists for order: " + request.getOrderId());
        }

        // Parse and validate the payment method
        PaymentMethod method = parsePaymentMethod(request.getPaymentMethod());

        // Build the payment entity
        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .paymentMethod(method)
                .status(PaymentStatus.PENDING)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment created with PENDING status for order: {}", request.getOrderId());

        // Select the correct gateway using the Factory pattern.
        // CREDIT_CARD/DEBIT_CARD → Stripe, PAYPAL → PayPal, BANK_TRANSFER → Bank API
        PaymentGateway gateway = gatewayFactory.getGateway(method);

        // Call the gateway with retry.
        // If it fails, retry up to 3 times with exponential backoff (1s, 2s, 4s).
        // Each attempt (success or failure) is logged as a PaymentTransaction.
        GatewayResponse response = chargeWithRetry(
                gateway, payment.getId(), request.getOrderId(), request.getAmount());

        if (response.success()) {
            payment.setTransactionId(response.transactionId());
            payment.setStatus(PaymentStatus.COMPLETED);
            log.info("Payment completed for order: {}, transactionId: {}",
                    request.getOrderId(), response.transactionId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            log.error("Payment failed for order: {} after all retries: {}",
                    request.getOrderId(), response.message());
        }

        return paymentRepository.save(payment);
    }

    // Charge with retry — tries the gateway up to 3 times.
    //
    // @Retry annotation from Resilience4j:
    //   - name = "paymentGateway" matches the config in application.properties
    //   - fallbackMethod = called when ALL retries are exhausted
    //
    // Each attempt is logged as a PaymentTransaction for the audit trail.
    //
    // IMPORTANT: We manually manage retries here instead of using @Retry on the gateway
    // because we need to LOG each attempt. With @Retry, the retry is transparent —
    // you don't get a chance to record the failure before the next attempt.
    // So we implement the retry loop ourselves for full control.
    public GatewayResponse chargeWithRetry(
            PaymentGateway gateway, Long paymentId, Long orderId, BigDecimal amount) {

        int maxAttempts = 3;
        long waitMs = 1000; // initial wait: 1 second
        GatewayResponse lastResponse = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.info("Payment attempt {}/{} for order {} via {}",
                    attempt, maxAttempts, orderId, gateway.gatewayName());

            try {
                GatewayResponse response = gateway.charge(orderId, amount);

                // Log this attempt
                logTransaction(paymentId, TransactionType.CHARGE,
                        response.success() ? TransactionStatus.SUCCESS : TransactionStatus.FAILED,
                        gateway.gatewayName(), response.transactionId(), amount,
                        response.message(), attempt);

                if (response.success()) {
                    return response;
                }

                lastResponse = response;
                log.warn("Attempt {}/{} failed for order {}: {}", attempt, maxAttempts, orderId, response.message());

            } catch (Exception e) {
                // Gateway threw an exception (network error, timeout, etc.)
                logTransaction(paymentId, TransactionType.CHARGE, TransactionStatus.FAILED,
                        gateway.gatewayName(), null, amount, e.getMessage(), attempt);

                lastResponse = GatewayResponse.failure(e.getMessage());
                log.error("Attempt {}/{} threw exception for order {}: {}", attempt, maxAttempts, orderId, e.getMessage());
            }

            // Wait before retrying (exponential backoff)
            if (attempt < maxAttempts) {
                try {
                    log.info("Waiting {}ms before retry...", waitMs);
                    Thread.sleep(waitMs);
                    waitMs *= 2; // double the wait time: 1s → 2s → 4s
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // All attempts failed
        return lastResponse != null ? lastResponse : GatewayResponse.failure("All payment attempts failed");
    }

    // ==================== READ ====================

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", id));
    }

    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "orderId", orderId));
    }

    public List<Payment> getPaymentsByUserId(Long userId) {
        return paymentRepository.findByUserId(userId);
    }

    // Get the transaction history for a payment (audit trail)
    public List<PaymentTransaction> getTransactionHistory(Long paymentId) {
        // Verify payment exists
        getPaymentById(paymentId);
        return transactionRepository.findByPaymentIdOrderByCreatedAtDesc(paymentId);
    }

    // ==================== REFUND ====================

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

        // Get the same gateway that processed the original charge
        PaymentGateway gateway = gatewayFactory.getGateway(payment.getPaymentMethod());

        // Call the gateway's refund method
        GatewayResponse response = gateway.refund(payment.getTransactionId(), payment.getAmount());

        // Log the refund attempt
        logTransaction(payment.getId(), TransactionType.REFUND,
                response.success() ? TransactionStatus.SUCCESS : TransactionStatus.FAILED,
                gateway.gatewayName(), response.transactionId(), payment.getAmount(),
                response.message(), 1);

        if (response.success()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment = paymentRepository.save(payment);
            log.info("Payment refunded: id={}, orderId={}, amount={}",
                    payment.getId(), payment.getOrderId(), payment.getAmount());
        } else {
            throw new PaymentException("Refund failed: " + response.message());
        }

        return payment;
    }

    // ==================== HELPERS ====================

    // Log a transaction attempt to the audit trail.
    private void logTransaction(Long paymentId, TransactionType type, TransactionStatus status,
                                String gatewayName, String gatewayTransactionId,
                                BigDecimal amount, String message, int attemptNumber) {

        Payment payment = paymentRepository.getReferenceById(paymentId);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .payment(payment)
                .type(type)
                .status(status)
                .gatewayName(gatewayName)
                .gatewayTransactionId(gatewayTransactionId)
                .amount(amount)
                .message(message)
                .attemptNumber(attemptNumber)
                .build();

        transactionRepository.save(transaction);
    }

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
