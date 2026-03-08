package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.exception.PaymentException;
import com.ecommerce.paymentservice.exception.ResourceNotFoundException;
import com.ecommerce.paymentservice.factory.PaymentFactory;
import com.ecommerce.paymentservice.gateway.*;
import com.ecommerce.paymentservice.model.*;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.repository.PaymentTransactionRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private PaymentGatewayFactory gatewayFactory;

    @InjectMocks
    private PaymentService paymentService;

    // ========== Process Payment ==========

    @Nested
    @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        @DisplayName("should process payment via Stripe gateway for credit card")
        void processPayment_creditCard_usesStripe() {
            CreatePaymentRequest request = PaymentFactory.createPaymentRequest();

            when(paymentRepository.existsByOrderIdAndStatus(
                    request.getOrderId(), PaymentStatus.COMPLETED)).thenReturn(false);

            // Mock the gateway factory to return a Stripe gateway
            StripePaymentGateway stripeGateway = new StripePaymentGateway();
            when(gatewayFactory.getGateway(PaymentMethod.CREDIT_CARD)).thenReturn(stripeGateway);

            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) p.setId(1L);
                return p;
            });
            when(paymentRepository.getReferenceById(1L)).thenReturn(Payment.builder().id(1L).build());
            when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.processPayment(request);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(result.getTransactionId()).startsWith("pi_"); // Stripe format
            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);

            // Verify transaction was logged
            verify(transactionRepository, atLeastOnce()).save(any(PaymentTransaction.class));
        }

        @Test
        @DisplayName("should process payment via PayPal gateway")
        void processPayment_paypal_usesPayPal() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(2L).userId(1L)
                    .amount(new BigDecimal("75.00"))
                    .paymentMethod("PAYPAL")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(2L, PaymentStatus.COMPLETED)).thenReturn(false);

            PayPalPaymentGateway paypalGateway = new PayPalPaymentGateway();
            when(gatewayFactory.getGateway(PaymentMethod.PAYPAL)).thenReturn(paypalGateway);

            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) p.setId(2L);
                return p;
            });
            when(paymentRepository.getReferenceById(2L)).thenReturn(Payment.builder().id(2L).build());
            when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.processPayment(request);

            assertThat(result.getTransactionId()).startsWith("PAY-"); // PayPal format
            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.PAYPAL);
        }

        @Test
        @DisplayName("should process payment via Bank Transfer gateway")
        void processPayment_bankTransfer() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(3L).userId(1L)
                    .amount(new BigDecimal("1000.00"))
                    .paymentMethod("BANK_TRANSFER")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(3L, PaymentStatus.COMPLETED)).thenReturn(false);

            BankTransferGateway bankGateway = new BankTransferGateway();
            when(gatewayFactory.getGateway(PaymentMethod.BANK_TRANSFER)).thenReturn(bankGateway);

            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) p.setId(3L);
                return p;
            });
            when(paymentRepository.getReferenceById(3L)).thenReturn(Payment.builder().id(3L).build());
            when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.processPayment(request);

            assertThat(result.getTransactionId()).startsWith("ACH-"); // Bank transfer format
        }

        @Test
        @DisplayName("should reject duplicate payment for same order")
        void processPayment_duplicatePayment() {
            CreatePaymentRequest request = PaymentFactory.createPaymentRequest();

            when(paymentRepository.existsByOrderIdAndStatus(
                    request.getOrderId(), PaymentStatus.COMPLETED)).thenReturn(true);

            assertThatThrownBy(() -> paymentService.processPayment(request))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("completed payment already exists");

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
        @DisplayName("should mark payment as FAILED and log attempts when gateway fails")
        void processPayment_gatewayFails() {
            CreatePaymentRequest request = PaymentFactory.createPaymentRequest();

            when(paymentRepository.existsByOrderIdAndStatus(1L, PaymentStatus.COMPLETED)).thenReturn(false);

            // Mock a gateway that always fails
            PaymentGateway failingGateway = mock(PaymentGateway.class);
            when(failingGateway.charge(any(), any())).thenReturn(GatewayResponse.failure("Card declined"));
            when(failingGateway.gatewayName()).thenReturn("STRIPE");
            when(gatewayFactory.getGateway(PaymentMethod.CREDIT_CARD)).thenReturn(failingGateway);

            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) p.setId(1L);
                return p;
            });
            when(paymentRepository.getReferenceById(1L)).thenReturn(Payment.builder().id(1L).build());
            when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.processPayment(request);

            // Payment should be FAILED after all retries exhausted
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.getTransactionId()).isNull();

            // Should have logged 3 failed attempts (maxAttempts = 3)
            verify(transactionRepository, times(3)).save(any(PaymentTransaction.class));
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
        @DisplayName("should refund a completed payment via the correct gateway")
        void refundPayment_success() {
            Payment payment = PaymentFactory.createPaymentWithId(1L);

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            // Mock gateway for refund
            StripePaymentGateway stripeGateway = new StripePaymentGateway();
            when(gatewayFactory.getGateway(PaymentMethod.CREDIT_CARD)).thenReturn(stripeGateway);
            when(paymentRepository.getReferenceById(1L)).thenReturn(payment);
            when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.refundPayment(1L);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            // Verify refund transaction was logged
            verify(transactionRepository).save(any(PaymentTransaction.class));
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

    // ========== Retry Logic ==========

    @Nested
    @DisplayName("chargeWithRetry")
    class ChargeWithRetry {

        @Test
        @DisplayName("should succeed on second attempt after first failure")
        void retrySucceedsOnSecondAttempt() {
            PaymentGateway gateway = mock(PaymentGateway.class);
            when(gateway.gatewayName()).thenReturn("STRIPE");
            // First call fails, second succeeds
            when(gateway.charge(any(), any()))
                    .thenReturn(GatewayResponse.failure("Temporary error"))
                    .thenReturn(GatewayResponse.success("pi_retry_success"));

            when(paymentRepository.getReferenceById(1L)).thenReturn(Payment.builder().id(1L).build());
            when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

            GatewayResponse result = paymentService.chargeWithRetry(
                    gateway, 1L, 1L, new BigDecimal("50.00"));

            assertThat(result.success()).isTrue();
            assertThat(result.transactionId()).isEqualTo("pi_retry_success");

            // Gateway was called twice (1 fail + 1 success)
            verify(gateway, times(2)).charge(any(), any());
            // 2 transactions logged (1 FAILED + 1 SUCCESS)
            verify(transactionRepository, times(2)).save(any(PaymentTransaction.class));
        }

        @Test
        @DisplayName("should return failure after all 3 attempts fail")
        void allRetriesFail() {
            PaymentGateway gateway = mock(PaymentGateway.class);
            when(gateway.gatewayName()).thenReturn("PAYPAL");
            when(gateway.charge(any(), any()))
                    .thenReturn(GatewayResponse.failure("Service unavailable"));

            when(paymentRepository.getReferenceById(1L)).thenReturn(Payment.builder().id(1L).build());
            when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

            GatewayResponse result = paymentService.chargeWithRetry(
                    gateway, 1L, 1L, new BigDecimal("100.00"));

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("Service unavailable");

            // All 3 attempts were made
            verify(gateway, times(3)).charge(any(), any());
            // All 3 failures logged
            verify(transactionRepository, times(3)).save(any(PaymentTransaction.class));
        }
    }
}
