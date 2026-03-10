package com.ecommerce.notificationservice.consumer;

import com.ecommerce.notificationservice.event.OrderPlacedEvent;
import com.ecommerce.notificationservice.event.PaymentProcessedEvent;
import com.ecommerce.notificationservice.event.UserRegisteredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@DisplayName("Consumer Additional Edge Case Tests")
class ConsumerAdditionalTest {

    // ==================== UserEventConsumer ====================

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("UserEventConsumer additional tests")
    class UserEventConsumerAdditionalTests {

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        @Mock
        private KafkaTemplate<String, String> kafkaTemplate;

        @InjectMocks
        private UserEventConsumer consumer;

        @Test
        @DisplayName("should handle event with null firstName without throwing")
        void shouldHandleNullFirstName() throws Exception {
            UserRegisteredEvent event = new UserRegisteredEvent(1L, "alice@test.com", null, "Smith");
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handleUserRegistered(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("should handle event with empty email without throwing")
        void shouldHandleEmptyEmail() throws Exception {
            UserRegisteredEvent event = new UserRegisteredEvent(2L, "", "Bob", "Jones");
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handleUserRegistered(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("should handle empty JSON object without throwing")
        void shouldHandleEmptyJsonObject() {
            String emptyJson = "{}";

            assertDoesNotThrow(() -> consumer.handleUserRegistered(emptyJson));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("should handle event with all null fields without throwing")
        void shouldHandleAllNullFields() throws Exception {
            UserRegisteredEvent event = new UserRegisteredEvent(null, null, null, null);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handleUserRegistered(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }
    }

    // ==================== OrderEventConsumer ====================

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("OrderEventConsumer additional tests")
    class OrderEventConsumerAdditionalTests {

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        @Mock
        private KafkaTemplate<String, String> kafkaTemplate;

        @InjectMocks
        private OrderEventConsumer consumer;

        @Test
        @DisplayName("should process order with DELIVERED status")
        void shouldProcessDeliveredStatus() throws Exception {
            OrderPlacedEvent event = new OrderPlacedEvent(1L, 5L, new BigDecimal("120.00"), "DELIVERED", 3);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handleOrderPlaced(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("should process order with SHIPPED status")
        void shouldProcessShippedStatus() throws Exception {
            OrderPlacedEvent event = new OrderPlacedEvent(2L, 6L, new BigDecimal("85.50"), "SHIPPED", 1);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handleOrderPlaced(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("should send event with null status to DLT due to NullPointerException")
        void shouldSendNullStatusToDlt() throws Exception {
            // status is null -> event.getStatus().toLowerCase() throws NPE -> goes to DLT
            OrderPlacedEvent event = new OrderPlacedEvent(3L, 7L, new BigDecimal("50.00"), null, 2);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handleOrderPlaced(json));
            verify(kafkaTemplate).send(eq("order-events-dlt"), eq(json));
        }

        @Test
        @DisplayName("should send empty JSON object to DLT due to null status")
        void shouldSendEmptyJsonToDlt() {
            // Empty JSON -> all fields null -> getStatus().toLowerCase() throws NPE -> DLT
            String emptyJson = "{}";

            assertDoesNotThrow(() -> consumer.handleOrderPlaced(emptyJson));
            verify(kafkaTemplate).send(eq("order-events-dlt"), eq(emptyJson));
        }

        @Test
        @DisplayName("should process order with zero total amount")
        void shouldProcessZeroTotalAmount() throws Exception {
            OrderPlacedEvent event = new OrderPlacedEvent(4L, 8L, BigDecimal.ZERO, "CONFIRMED", 0);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handleOrderPlaced(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }
    }

    // ==================== PaymentEventConsumer ====================

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("PaymentEventConsumer additional tests")
    class PaymentEventConsumerAdditionalTests {

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        @Mock
        private KafkaTemplate<String, String> kafkaTemplate;

        @InjectMocks
        private PaymentEventConsumer consumer;

        @Test
        @DisplayName("should handle COMPLETED payment with null transactionId without throwing")
        void shouldHandleNullTransactionId() throws Exception {
            // COMPLETED payment with null transactionId — logs "null" but doesn't throw
            PaymentProcessedEvent event = new PaymentProcessedEvent(
                    1L, 10L, 5L, new BigDecimal("99.99"),
                    "COMPLETED", "CREDIT_CARD", null);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handlePaymentProcessed(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("should handle empty status string as unknown status")
        void shouldHandleEmptyStatusString() throws Exception {
            // Empty status -> hits default branch in switch -> logs warning, no DLT
            PaymentProcessedEvent event = new PaymentProcessedEvent(
                    2L, 11L, 5L, new BigDecimal("50.00"),
                    "", "PAYPAL", null);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handlePaymentProcessed(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("should send event with null status to DLT due to NullPointerException")
        void shouldSendNullStatusToDlt() throws Exception {
            // null status -> switch(null) throws NPE -> goes to DLT
            PaymentProcessedEvent event = new PaymentProcessedEvent(
                    3L, 12L, 5L, new BigDecimal("75.00"),
                    null, "BANK_TRANSFER", null);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handlePaymentProcessed(json));
            verify(kafkaTemplate).send(eq("payment-events-dlt"), eq(json));
        }

        @Test
        @DisplayName("should send empty JSON object to DLT due to null status")
        void shouldSendEmptyJsonToDlt() {
            // Empty JSON -> all fields null -> switch(null) throws NPE -> DLT
            String emptyJson = "{}";

            assertDoesNotThrow(() -> consumer.handlePaymentProcessed(emptyJson));
            verify(kafkaTemplate).send(eq("payment-events-dlt"), eq(emptyJson));
        }

        @Test
        @DisplayName("should handle REFUNDED payment with null paymentMethod without throwing")
        void shouldHandleRefundedWithNullPaymentMethod() throws Exception {
            PaymentProcessedEvent event = new PaymentProcessedEvent(
                    4L, 13L, 5L, new BigDecimal("30.00"),
                    "REFUNDED", null, null);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handlePaymentProcessed(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }

        @Test
        @DisplayName("should handle FAILED payment with all nullable fields null")
        void shouldHandleFailedWithNullFields() throws Exception {
            PaymentProcessedEvent event = new PaymentProcessedEvent(
                    5L, 14L, 5L, new BigDecimal("200.00"),
                    "FAILED", null, null);
            String json = objectMapper.writeValueAsString(event);

            assertDoesNotThrow(() -> consumer.handlePaymentProcessed(json));
            verify(kafkaTemplate, never()).send(anyString(), anyString());
        }
    }
}
