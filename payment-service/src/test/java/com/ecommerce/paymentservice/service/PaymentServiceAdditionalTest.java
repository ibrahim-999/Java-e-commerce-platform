package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.dto.CreatePaymentRequest;
import com.ecommerce.paymentservice.exception.PaymentException;
import com.ecommerce.paymentservice.exception.ResourceNotFoundException;
import com.ecommerce.paymentservice.factory.PaymentFactory;
import com.ecommerce.paymentservice.gateway.GatewayResponse;
import com.ecommerce.paymentservice.gateway.PaymentGateway;
import com.ecommerce.paymentservice.gateway.PaymentGatewayFactory;
import com.ecommerce.paymentservice.gateway.StripePaymentGateway;
import com.ecommerce.paymentservice.model.*;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import com.ecommerce.paymentservice.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — Additional Tests")
class PaymentServiceAdditionalTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private PaymentGatewayFactory gatewayFactory;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentService paymentService;

    // ========== Transaction History ==========

    @Nested
    @DisplayName("getTransactionHistory")
    class GetTransactionHistory {

        @Test
        @DisplayName("should return ordered list of transactions for existing payment")
        void getTransactionHistory_returnsOrderedList() {
            Payment payment = PaymentFactory.createPaymentWithId(1L);
            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            PaymentTransaction tx1 = PaymentTransaction.builder()
                    .id(1L)
                    .payment(payment)
                    .type(TransactionType.CHARGE)
                    .status(TransactionStatus.FAILED)
                    .gatewayName("STRIPE")
                    .amount(new BigDecimal("99.99"))
                    .message("Card declined")
                    .attemptNumber(1)
                    .createdAt(LocalDateTime.now().minusMinutes(2))
                    .build();

            PaymentTransaction tx2 = PaymentTransaction.builder()
                    .id(2L)
                    .payment(payment)
                    .type(TransactionType.CHARGE)
                    .status(TransactionStatus.SUCCESS)
                    .gatewayName("STRIPE")
                    .gatewayTransactionId("pi_abc123")
                    .amount(new BigDecimal("99.99"))
                    .message("Payment processed successfully")
                    .attemptNumber(2)
                    .createdAt(LocalDateTime.now().minusMinutes(1))
                    .build();

            when(transactionRepository.findByPaymentIdOrderByCreatedAtDesc(1L))
                    .thenReturn(List.of(tx2, tx1));

            List<PaymentTransaction> result = paymentService.getTransactionHistory(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(result.get(1).getStatus()).isEqualTo(TransactionStatus.FAILED);

            verify(paymentRepository).findById(1L);
            verify(transactionRepository).findByPaymentIdOrderByCreatedAtDesc(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existent payment")
        void getTransactionHistory_nonExistentPayment_throwsException() {
            when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getTransactionHistory(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment")
                    .hasMessageContaining("999");

            verify(transactionRepository, never()).findByPaymentIdOrderByCreatedAtDesc(any());
        }
    }

    // ========== Refund — FAILED status ==========

    @Nested
    @DisplayName("refundPayment — additional cases")
    class RefundPaymentAdditional {

        @Test
        @DisplayName("should throw PaymentException when payment status is FAILED")
        void refundPayment_failedPayment_throwsException() {
            Payment payment = PaymentFactory.createPaymentWithId(1L);
            payment.setStatus(PaymentStatus.FAILED);

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.refundPayment(1L))
                    .isInstanceOf(PaymentException.class)
                    .hasMessageContaining("Only completed payments can be refunded")
                    .hasMessageContaining("FAILED");

            verify(paymentRepository, never()).save(any());
        }
    }

    // ========== Kafka Event Publishing ==========

    @Nested
    @DisplayName("processPayment — Kafka event publishing")
    class KafkaEventPublishing {

        @Test
        @DisplayName("should publish payment event to Kafka after successful payment")
        void processPayment_publishesKafkaEvent() {
            CreatePaymentRequest request = PaymentFactory.createPaymentRequest();

            when(paymentRepository.existsByOrderIdAndStatus(
                    request.getOrderId(), PaymentStatus.COMPLETED)).thenReturn(false);

            StripePaymentGateway stripeGateway = new StripePaymentGateway();
            when(gatewayFactory.getGateway(PaymentMethod.CREDIT_CARD)).thenReturn(stripeGateway);

            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) p.setId(1L);
                return p;
            });
            when(paymentRepository.getReferenceById(1L)).thenReturn(Payment.builder().id(1L).build());
            when(transactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            paymentService.processPayment(request);

            // Verify Kafka event was published
            verify(kafkaTemplate).send(eq("payment-events"), eq("payment-1"), anyString());
        }

        @Test
        @DisplayName("should publish payment event to Kafka after successful refund")
        void refundPayment_publishesKafkaEvent() {
            Payment payment = PaymentFactory.createPaymentWithId(1L);

            when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

            StripePaymentGateway stripeGateway = new StripePaymentGateway();
            when(gatewayFactory.getGateway(PaymentMethod.CREDIT_CARD)).thenReturn(stripeGateway);
            when(paymentRepository.getReferenceById(1L)).thenReturn(payment);
            when(transactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            paymentService.refundPayment(1L);

            // Verify Kafka event was published for refund
            verify(kafkaTemplate).send(eq("payment-events"), eq("payment-1"), anyString());
        }

        @Test
        @DisplayName("should not throw when Kafka publishing fails")
        void processPayment_kafkaFailure_doesNotThrow() {
            CreatePaymentRequest request = PaymentFactory.createPaymentRequest();

            when(paymentRepository.existsByOrderIdAndStatus(
                    request.getOrderId(), PaymentStatus.COMPLETED)).thenReturn(false);

            StripePaymentGateway stripeGateway = new StripePaymentGateway();
            when(gatewayFactory.getGateway(PaymentMethod.CREDIT_CARD)).thenReturn(stripeGateway);

            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) p.setId(1L);
                return p;
            });
            when(paymentRepository.getReferenceById(1L)).thenReturn(Payment.builder().id(1L).build());
            when(transactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Make Kafka throw an exception
            when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Kafka broker unavailable"));

            // Should still complete without throwing
            Payment result = paymentService.processPayment(request);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }
    }

    // ========== parsePaymentMethod ==========

    @Nested
    @DisplayName("parsePaymentMethod — via processPayment")
    class ParsePaymentMethod {

        @Test
        @DisplayName("should parse CREDIT_CARD payment method")
        void parsePaymentMethod_creditCard() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(10L).userId(1L)
                    .amount(new BigDecimal("50.00"))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(10L, PaymentStatus.COMPLETED))
                    .thenReturn(false);
            when(gatewayFactory.getGateway(PaymentMethod.CREDIT_CARD))
                    .thenReturn(new StripePaymentGateway());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                if (p.getId() == null) p.setId(10L);
                return p;
            });
            when(paymentRepository.getReferenceById(10L))
                    .thenReturn(Payment.builder().id(10L).build());
            when(transactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.processPayment(request);

            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        }

        @Test
        @DisplayName("should parse DEBIT_CARD payment method")
        void parsePaymentMethod_debitCard() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(11L).userId(1L)
                    .amount(new BigDecimal("50.00"))
                    .paymentMethod("DEBIT_CARD")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(11L, PaymentStatus.COMPLETED))
                    .thenReturn(false);
            when(gatewayFactory.getGateway(PaymentMethod.DEBIT_CARD))
                    .thenReturn(new StripePaymentGateway());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                if (p.getId() == null) p.setId(11L);
                return p;
            });
            when(paymentRepository.getReferenceById(11L))
                    .thenReturn(Payment.builder().id(11L).build());
            when(transactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.processPayment(request);

            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.DEBIT_CARD);
        }

        @Test
        @DisplayName("should parse PAYPAL payment method")
        void parsePaymentMethod_paypal() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(12L).userId(1L)
                    .amount(new BigDecimal("50.00"))
                    .paymentMethod("PAYPAL")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(12L, PaymentStatus.COMPLETED))
                    .thenReturn(false);
            when(gatewayFactory.getGateway(PaymentMethod.PAYPAL))
                    .thenReturn(new com.ecommerce.paymentservice.gateway.PayPalPaymentGateway());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                if (p.getId() == null) p.setId(12L);
                return p;
            });
            when(paymentRepository.getReferenceById(12L))
                    .thenReturn(Payment.builder().id(12L).build());
            when(transactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.processPayment(request);

            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.PAYPAL);
        }

        @Test
        @DisplayName("should parse BANK_TRANSFER payment method")
        void parsePaymentMethod_bankTransfer() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(13L).userId(1L)
                    .amount(new BigDecimal("50.00"))
                    .paymentMethod("BANK_TRANSFER")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(13L, PaymentStatus.COMPLETED))
                    .thenReturn(false);
            when(gatewayFactory.getGateway(PaymentMethod.BANK_TRANSFER))
                    .thenReturn(new com.ecommerce.paymentservice.gateway.BankTransferGateway());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                if (p.getId() == null) p.setId(13L);
                return p;
            });
            when(paymentRepository.getReferenceById(13L))
                    .thenReturn(Payment.builder().id(13L).build());
            when(transactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.processPayment(request);

            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        }

        @Test
        @DisplayName("should parse lowercase payment method (case insensitive)")
        void parsePaymentMethod_lowercase() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(14L).userId(1L)
                    .amount(new BigDecimal("50.00"))
                    .paymentMethod("credit_card")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(14L, PaymentStatus.COMPLETED))
                    .thenReturn(false);
            when(gatewayFactory.getGateway(PaymentMethod.CREDIT_CARD))
                    .thenReturn(new StripePaymentGateway());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                if (p.getId() == null) p.setId(14L);
                return p;
            });
            when(paymentRepository.getReferenceById(14L))
                    .thenReturn(Payment.builder().id(14L).build());
            when(transactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.processPayment(request);

            assertThat(result.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid payment method")
        void parsePaymentMethod_invalid_throwsException() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .orderId(15L).userId(1L)
                    .amount(new BigDecimal("50.00"))
                    .paymentMethod("CRYPTOCURRENCY")
                    .build();

            when(paymentRepository.existsByOrderIdAndStatus(15L, PaymentStatus.COMPLETED))
                    .thenReturn(false);

            assertThatThrownBy(() -> paymentService.processPayment(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid payment method")
                    .hasMessageContaining("CRYPTOCURRENCY")
                    .hasMessageContaining("Supported methods");
        }
    }

    // ========== chargeWithRetry — exception handling ==========

    @Nested
    @DisplayName("chargeWithRetry — exception handling")
    class ChargeWithRetryExceptions {

        @Test
        @DisplayName("should handle gateway exception and log failed transaction")
        void chargeWithRetry_gatewayThrowsException() {
            PaymentGateway gateway = mock(PaymentGateway.class);
            when(gateway.gatewayName()).thenReturn("STRIPE");
            when(gateway.charge(any(), any()))
                    .thenThrow(new RuntimeException("Network timeout"));

            when(paymentRepository.getReferenceById(1L))
                    .thenReturn(Payment.builder().id(1L).build());
            when(transactionRepository.save(any(PaymentTransaction.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            GatewayResponse result = paymentService.chargeWithRetry(
                    gateway, 1L, 1L, new BigDecimal("100.00"));

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("Network timeout");

            verify(gateway, times(3)).charge(any(), any());
            verify(transactionRepository, times(3)).save(any(PaymentTransaction.class));
        }
    }
}
