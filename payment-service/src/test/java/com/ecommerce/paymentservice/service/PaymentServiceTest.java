package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.exception.PaymentException;
import com.ecommerce.paymentservice.exception.ResourceNotFoundException;
import com.ecommerce.paymentservice.factory.PaymentFactory;
import com.ecommerce.paymentservice.model.Payment;
import com.ecommerce.paymentservice.model.PaymentMethod;
import com.ecommerce.paymentservice.model.PaymentStatus;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    // ========== Process Payment ==========

    @Nested
    @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        @DisplayName("should process payment successfully and generate transaction ID")
        void processPayment_success() {
            CreatePaymentRequest request = PaymentFactory.createPaymentRequest();

            // No existing completed payment for this order
            when(paymentRepository.existsByOrderIdAndStatus(
                    request.getOrderId(), PaymentStatus.COMPLETED)).thenReturn(false);

            // save() returns the payment with an ID (called twice: PENDING then COMPLETED)
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                p.setId(1L);
                return p;
            });

            Payment result = paymentService.processPayment(request);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(result.getTransactionId()).startsWith("txn_card_");
            assertThat(result.getOrderId()).isEqualTo(1L);
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);

            // Saved twice: once as PENDING, once as COMPLETED
            verify(paymentRepository, times(2)).save(any(Payment.class));
        }

        @Test
        @DisplayName("should reject duplicate payment for same order")
        void processPayment_duplicatePayment() {
            CreatePaymentRequest request = PaymentFactory.createPaymentRequest();

            // A completed payment already exists
            when(paymentRepository.existsByOrderIdAndStatus(
                    request.getOrderId(), PaymentStatus.COMPLETED)).thenReturn(true);

            assertThatThrownBy(() -> paymentService.processPayment(request))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("completed payment already exists");

            // Should never attempt to save
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject invalid payment method")
        void processPayment_invalidMethod() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(1L).userId(1L)
                    .amount(new BigDecimal("50.00"))
                    .paymentMethod("BITCOIN")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .thenReturn(false);

            assertThatThrownBy(() -> paymentService.processPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid payment method")
                    .hasMessageContaining("BITCOIN");
        }

        @Test
        @DisplayName("should generate PayPal transaction ID for PayPal payments")
        void processPayment_paypalMethod() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(2L).userId(1L)
                    .amount(new BigDecimal("75.00"))
                    .paymentMethod("PAYPAL")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(2L, PaymentStatus.COMPLETED))
                    .thenReturn(false);
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                p.setId(2L);
                return p;
            });

            Payment result = paymentService.processPayment(request);

            assertThat(result.getTransactionId()).startsWith("txn_pp_");
            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.PAYPAL);
        }
    }

    // ========== Get Payment ==========

    @Nested
    @DisplayName("getPayment")
    class GetPayment {

        @Test
        @DisplayName("should return payment by ID")
        void getPaymentById_found() {
            Payment payment = PaymentFactory.createPaymentWithId(1L);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentService.getPaymentById(1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getTransactionId()).isEqualTo("txn_card_abc123");
        }

        @Test
        @DisplayName("should throw when payment not found by ID")
        void getPaymentById_notFound() {
            when(paymentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment")
                    .hasMessageContaining("99");
        }

        @Test
        @DisplayName("should return payment by order ID")
        void getPaymentByOrderId_found() {
            Payment payment = PaymentFactory.createPaymentWithId(1L);
            when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(payment));

            Payment result = paymentService.getPaymentByOrderId(1L);

            assertThat(result.getOrderId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should return all payments for a user")
        void getPaymentsByUserId() {
            Payment p1 = PaymentFactory.createPaymentWithId(1L);
            Payment p2 = PaymentFactory.createPaymentWithId(2L);
            p2.setOrderId(2L);

            when(paymentRepository.findByUserId(1L)).thenReturn(List.of(p1, p2));

            List<Payment> results = paymentService.getPaymentsByUserId(1L);

            assertThat(results).hasSize(2);
        }
    }

    // ========== Refund Payment ==========

    @Nested
    @DisplayName("refundPayment")
    class RefundPayment {

        @Test
        @DisplayName("should refund a completed payment")
        void refundPayment_success() {
            Payment payment = PaymentFactory.createPaymentWithId(1L);
            // payment is COMPLETED by default from factory

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.refundPayment(1L);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            verify(paymentRepository).save(payment);
        }

        @Test
        @DisplayName("should reject refund of already-refunded payment")
        void refundPayment_alreadyRefunded() {
            Payment payment = PaymentFactory.createPaymentWithId(1L);
            payment.setStatus(PaymentStatus.REFUNDED);

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(1L))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("already been refunded");

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject refund of pending payment")
        void refundPayment_pendingPayment() {
            Payment payment = PaymentFactory.createPaymentWithId(1L);
            payment.setStatus(PaymentStatus.PENDING);

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(1L))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Only completed payments can be refunded")
                    .hasMessageContaining("PENDING");

            verify(paymentRepository, never()).save(any());
        }
    }
}
