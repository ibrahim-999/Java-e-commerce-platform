package com.ecommerce.notificationservice.consumer;

import com.ecommerce.notificationservice.event.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

// Tests all 3 payment status branches (COMPLETED, FAILED, REFUNDED)
// plus the unknown-status and invalid-JSON error paths.

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer Unit Tests")
class PaymentEventConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private PaymentEventConsumer consumer;

    @Test
    @DisplayName("should process COMPLETED payment event")
    void shouldProcessCompletedPayment() throws Exception {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                1L, 10L, 5L, new BigDecimal("99.99"),
                "COMPLETED", "CREDIT_CARD", "pi_abc123");
        String json = objectMapper.writeValueAsString(event);

        consumer.handlePaymentProcessed(json);

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("should process FAILED payment event")
    void shouldProcessFailedPayment() throws Exception {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                2L, 11L, 5L, new BigDecimal("250.00"),
                "FAILED", "PAYPAL", null);
        String json = objectMapper.writeValueAsString(event);

        consumer.handlePaymentProcessed(json);

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("should process REFUNDED payment event")
    void shouldProcessRefundedPayment() throws Exception {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                3L, 12L, 5L, new BigDecimal("75.00"),
                "REFUNDED", "BANK_TRANSFER", "ACH-xyz789");
        String json = objectMapper.writeValueAsString(event);

        consumer.handlePaymentProcessed(json);

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("should handle unknown payment status without error")
    void shouldHandleUnknownStatus() throws Exception {
        PaymentProcessedEvent event = new PaymentProcessedEvent(
                4L, 13L, 5L, new BigDecimal("50.00"),
                "CANCELLED", "CREDIT_CARD", null);
        String json = objectMapper.writeValueAsString(event);

        // Should not throw, just log a warning
        consumer.handlePaymentProcessed(json);

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("should send invalid JSON to Dead Letter Topic")
    void shouldSendInvalidJsonToDlt() {
        String invalidJson = "{broken json";

        consumer.handlePaymentProcessed(invalidJson);

        verify(kafkaTemplate).send("payment-events-dlt", invalidJson);
    }
}
