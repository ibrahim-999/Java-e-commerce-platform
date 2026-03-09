package com.ecommerce.notificationservice.consumer;

import com.ecommerce.notificationservice.event.OrderPlacedEvent;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer Unit Tests")
class OrderEventConsumerTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OrderEventConsumer consumer;

    @Test
    @DisplayName("should process valid order placed event")
    void shouldProcessValidEvent() throws Exception {
        OrderPlacedEvent event = new OrderPlacedEvent(
                1L, 10L, new BigDecimal("199.98"), "CONFIRMED", 2);
        String json = objectMapper.writeValueAsString(event);

        consumer.handleOrderPlaced(json);

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("should process order with PENDING status")
    void shouldProcessPendingOrder() throws Exception {
        OrderPlacedEvent event = new OrderPlacedEvent(
                2L, 10L, new BigDecimal("500.00"), "PENDING", 1);
        String json = objectMapper.writeValueAsString(event);

        consumer.handleOrderPlaced(json);

        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("should send invalid JSON to Dead Letter Topic")
    void shouldSendInvalidJsonToDlt() {
        String invalidJson = "not-valid-json";

        consumer.handleOrderPlaced(invalidJson);

        verify(kafkaTemplate).send("order-events-dlt", invalidJson);
    }
}
