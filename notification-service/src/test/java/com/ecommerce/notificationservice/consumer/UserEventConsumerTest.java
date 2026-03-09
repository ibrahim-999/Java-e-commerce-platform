package com.ecommerce.notificationservice.consumer;

import com.ecommerce.notificationservice.event.UserRegisteredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.*;

// Unit tests for UserEventConsumer.
//
// These tests verify the consumer logic WITHOUT needing a real Kafka broker.
// We use Mockito to:
//   - @Spy the ObjectMapper (real JSON parsing, so we test actual deserialization)
//   - @Mock the KafkaTemplate (verify DLT publishing without real Kafka)
//
// The consumer receives raw JSON strings from Kafka, so we test with JSON input.

@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventConsumer Unit Tests")
class UserEventConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private UserEventConsumer consumer;

    @Test
    @DisplayName("should process valid user registered event")
    void shouldProcessValidEvent() throws Exception {
        // Arrange: create a valid JSON event
        UserRegisteredEvent event = new UserRegisteredEvent(1L, "alice@test.com", "Alice", "Smith");
        String json = objectMapper.writeValueAsString(event);

        // Act: call the consumer method directly (simulating Kafka delivering the message)
        consumer.handleUserRegistered(json);

        // Assert: the message was deserialized — no DLT publish
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("should send invalid JSON to Dead Letter Topic")
    void shouldSendInvalidJsonToDlt() {
        String invalidJson = "this is not json";

        consumer.handleUserRegistered(invalidJson);

        // Verify the invalid message was sent to the Dead Letter Topic
        verify(kafkaTemplate).send("user-events-dlt", invalidJson);
    }

    @Test
    @DisplayName("should send unprocessable event to Dead Letter Topic")
    void shouldSendUnprocessableEventToDlt() {
        // Valid JSON but all fields become null — causes processing failure
        String malformedJson = "{\"unknownField\": \"value\"}";

        consumer.handleUserRegistered(malformedJson);

        // The consumer fails when trying to process null fields, so it goes to DLT
        verify(kafkaTemplate).send("user-events-dlt", malformedJson);
    }
}
