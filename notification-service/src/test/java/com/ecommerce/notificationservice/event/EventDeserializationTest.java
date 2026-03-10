package com.ecommerce.notificationservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Event Deserialization Tests")
class EventDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== UserRegisteredEvent ====================

    @Nested
    @DisplayName("UserRegisteredEvent")
    class UserRegisteredEventTests {

        @Test
        @DisplayName("should deserialize from valid JSON")
        void shouldDeserializeFromValidJson() throws Exception {
            String json = """
                    {"userId":1,"email":"alice@test.com","firstName":"Alice","lastName":"Smith"}
                    """;

            UserRegisteredEvent event = objectMapper.readValue(json, UserRegisteredEvent.class);

            assertThat(event).isNotNull();
            assertThat(event.getUserId()).isEqualTo(1L);
            assertThat(event.getEmail()).isEqualTo("alice@test.com");
            assertThat(event.getFirstName()).isEqualTo("Alice");
            assertThat(event.getLastName()).isEqualTo("Smith");
        }

        @Test
        @DisplayName("should create non-null object with default constructor")
        void shouldCreateNonNullObjectWithDefaultConstructor() {
            UserRegisteredEvent event = new UserRegisteredEvent();

            assertThat(event).isNotNull();
        }

        @Test
        @DisplayName("should create object with all-args constructor")
        void shouldCreateObjectWithAllArgsConstructor() {
            UserRegisteredEvent event = new UserRegisteredEvent(5L, "bob@test.com", "Bob", "Jones");

            assertThat(event.getUserId()).isEqualTo(5L);
            assertThat(event.getEmail()).isEqualTo("bob@test.com");
            assertThat(event.getFirstName()).isEqualTo("Bob");
            assertThat(event.getLastName()).isEqualTo("Jones");
        }

        @Test
        @DisplayName("getters and setters should work correctly")
        void gettersAndSettersShouldWork() {
            UserRegisteredEvent event = new UserRegisteredEvent();
            event.setUserId(10L);
            event.setEmail("test@example.com");
            event.setFirstName("Test");
            event.setLastName("User");

            assertThat(event.getUserId()).isEqualTo(10L);
            assertThat(event.getEmail()).isEqualTo("test@example.com");
            assertThat(event.getFirstName()).isEqualTo("Test");
            assertThat(event.getLastName()).isEqualTo("User");
        }

        @Test
        @DisplayName("should serialize and deserialize round-trip")
        void shouldSerializeAndDeserializeRoundTrip() throws Exception {
            UserRegisteredEvent original = new UserRegisteredEvent(1L, "alice@test.com", "Alice", "Smith");
            String json = objectMapper.writeValueAsString(original);
            UserRegisteredEvent deserialized = objectMapper.readValue(json, UserRegisteredEvent.class);

            assertThat(deserialized.getUserId()).isEqualTo(original.getUserId());
            assertThat(deserialized.getEmail()).isEqualTo(original.getEmail());
            assertThat(deserialized.getFirstName()).isEqualTo(original.getFirstName());
            assertThat(deserialized.getLastName()).isEqualTo(original.getLastName());
        }

        @Test
        @DisplayName("should handle partial JSON with only some fields")
        void shouldHandlePartialJson() throws Exception {
            String json = """
                    {"userId":1,"email":"a@b.com"}
                    """;

            UserRegisteredEvent event = objectMapper.readValue(json, UserRegisteredEvent.class);

            assertThat(event.getUserId()).isEqualTo(1L);
            assertThat(event.getEmail()).isEqualTo("a@b.com");
            assertThat(event.getFirstName()).isNull();
            assertThat(event.getLastName()).isNull();
        }
    }

    // ==================== OrderPlacedEvent ====================

    @Nested
    @DisplayName("OrderPlacedEvent")
    class OrderPlacedEventTests {

        @Test
        @DisplayName("should deserialize from valid JSON")
        void shouldDeserializeFromValidJson() throws Exception {
            String json = """
                    {"orderId":10,"userId":5,"totalAmount":199.98,"status":"CONFIRMED","itemCount":2}
                    """;

            OrderPlacedEvent event = objectMapper.readValue(json, OrderPlacedEvent.class);

            assertThat(event).isNotNull();
            assertThat(event.getOrderId()).isEqualTo(10L);
            assertThat(event.getUserId()).isEqualTo(5L);
            assertThat(event.getTotalAmount()).isEqualByComparingTo(new BigDecimal("199.98"));
            assertThat(event.getStatus()).isEqualTo("CONFIRMED");
            assertThat(event.getItemCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should create non-null object with default constructor")
        void shouldCreateNonNullObjectWithDefaultConstructor() {
            OrderPlacedEvent event = new OrderPlacedEvent();

            assertThat(event).isNotNull();
        }

        @Test
        @DisplayName("should create object with all-args constructor")
        void shouldCreateObjectWithAllArgsConstructor() {
            OrderPlacedEvent event = new OrderPlacedEvent(1L, 2L, new BigDecimal("50.00"), "PENDING", 3);

            assertThat(event.getOrderId()).isEqualTo(1L);
            assertThat(event.getUserId()).isEqualTo(2L);
            assertThat(event.getTotalAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(event.getStatus()).isEqualTo("PENDING");
            assertThat(event.getItemCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("getters and setters should work correctly")
        void gettersAndSettersShouldWork() {
            OrderPlacedEvent event = new OrderPlacedEvent();
            event.setOrderId(100L);
            event.setUserId(200L);
            event.setTotalAmount(new BigDecimal("75.50"));
            event.setStatus("SHIPPED");
            event.setItemCount(5);

            assertThat(event.getOrderId()).isEqualTo(100L);
            assertThat(event.getUserId()).isEqualTo(200L);
            assertThat(event.getTotalAmount()).isEqualByComparingTo(new BigDecimal("75.50"));
            assertThat(event.getStatus()).isEqualTo("SHIPPED");
            assertThat(event.getItemCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("should serialize and deserialize round-trip")
        void shouldSerializeAndDeserializeRoundTrip() throws Exception {
            OrderPlacedEvent original = new OrderPlacedEvent(1L, 2L, new BigDecimal("99.99"), "CONFIRMED", 1);
            String json = objectMapper.writeValueAsString(original);
            OrderPlacedEvent deserialized = objectMapper.readValue(json, OrderPlacedEvent.class);

            assertThat(deserialized.getOrderId()).isEqualTo(original.getOrderId());
            assertThat(deserialized.getUserId()).isEqualTo(original.getUserId());
            assertThat(deserialized.getTotalAmount()).isEqualByComparingTo(original.getTotalAmount());
            assertThat(deserialized.getStatus()).isEqualTo(original.getStatus());
            assertThat(deserialized.getItemCount()).isEqualTo(original.getItemCount());
        }
    }

    // ==================== PaymentProcessedEvent ====================

    @Nested
    @DisplayName("PaymentProcessedEvent")
    class PaymentProcessedEventTests {

        @Test
        @DisplayName("should deserialize from valid JSON")
        void shouldDeserializeFromValidJson() throws Exception {
            String json = """
                    {"paymentId":1,"orderId":10,"userId":5,"amount":99.99,"status":"COMPLETED","paymentMethod":"CREDIT_CARD","transactionId":"pi_abc123"}
                    """;

            PaymentProcessedEvent event = objectMapper.readValue(json, PaymentProcessedEvent.class);

            assertThat(event).isNotNull();
            assertThat(event.getPaymentId()).isEqualTo(1L);
            assertThat(event.getOrderId()).isEqualTo(10L);
            assertThat(event.getUserId()).isEqualTo(5L);
            assertThat(event.getAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
            assertThat(event.getStatus()).isEqualTo("COMPLETED");
            assertThat(event.getPaymentMethod()).isEqualTo("CREDIT_CARD");
            assertThat(event.getTransactionId()).isEqualTo("pi_abc123");
        }

        @Test
        @DisplayName("should deserialize with null transactionId")
        void shouldDeserializeWithNullTransactionId() throws Exception {
            String json = """
                    {"paymentId":2,"orderId":11,"userId":5,"amount":250.00,"status":"FAILED","paymentMethod":"PAYPAL","transactionId":null}
                    """;

            PaymentProcessedEvent event = objectMapper.readValue(json, PaymentProcessedEvent.class);

            assertThat(event).isNotNull();
            assertThat(event.getTransactionId()).isNull();
            assertThat(event.getStatus()).isEqualTo("FAILED");
        }

        @Test
        @DisplayName("should create non-null object with default constructor")
        void shouldCreateNonNullObjectWithDefaultConstructor() {
            PaymentProcessedEvent event = new PaymentProcessedEvent();

            assertThat(event).isNotNull();
        }

        @Test
        @DisplayName("should create object with all-args constructor")
        void shouldCreateObjectWithAllArgsConstructor() {
            PaymentProcessedEvent event = new PaymentProcessedEvent(
                    1L, 2L, 3L, new BigDecimal("100.00"), "COMPLETED", "CREDIT_CARD", "tx-123");

            assertThat(event.getPaymentId()).isEqualTo(1L);
            assertThat(event.getOrderId()).isEqualTo(2L);
            assertThat(event.getUserId()).isEqualTo(3L);
            assertThat(event.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(event.getStatus()).isEqualTo("COMPLETED");
            assertThat(event.getPaymentMethod()).isEqualTo("CREDIT_CARD");
            assertThat(event.getTransactionId()).isEqualTo("tx-123");
        }

        @Test
        @DisplayName("getters and setters should work correctly")
        void gettersAndSettersShouldWork() {
            PaymentProcessedEvent event = new PaymentProcessedEvent();
            event.setPaymentId(50L);
            event.setOrderId(60L);
            event.setUserId(70L);
            event.setAmount(new BigDecimal("300.00"));
            event.setStatus("REFUNDED");
            event.setPaymentMethod("BANK_TRANSFER");
            event.setTransactionId("ACH-999");

            assertThat(event.getPaymentId()).isEqualTo(50L);
            assertThat(event.getOrderId()).isEqualTo(60L);
            assertThat(event.getUserId()).isEqualTo(70L);
            assertThat(event.getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));
            assertThat(event.getStatus()).isEqualTo("REFUNDED");
            assertThat(event.getPaymentMethod()).isEqualTo("BANK_TRANSFER");
            assertThat(event.getTransactionId()).isEqualTo("ACH-999");
        }

        @Test
        @DisplayName("should serialize and deserialize round-trip")
        void shouldSerializeAndDeserializeRoundTrip() throws Exception {
            PaymentProcessedEvent original = new PaymentProcessedEvent(
                    1L, 2L, 3L, new BigDecimal("55.55"), "COMPLETED", "PAYPAL", "pp-xyz");
            String json = objectMapper.writeValueAsString(original);
            PaymentProcessedEvent deserialized = objectMapper.readValue(json, PaymentProcessedEvent.class);

            assertThat(deserialized.getPaymentId()).isEqualTo(original.getPaymentId());
            assertThat(deserialized.getOrderId()).isEqualTo(original.getOrderId());
            assertThat(deserialized.getUserId()).isEqualTo(original.getUserId());
            assertThat(deserialized.getAmount()).isEqualByComparingTo(original.getAmount());
            assertThat(deserialized.getStatus()).isEqualTo(original.getStatus());
            assertThat(deserialized.getPaymentMethod()).isEqualTo(original.getPaymentMethod());
            assertThat(deserialized.getTransactionId()).isEqualTo(original.getTransactionId());
        }
    }
}
