package com.ecommerce.notificationservice.consumer;

import com.ecommerce.notificationservice.event.OrderPlacedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Kafka Consumer for order-related events.
//
// Listens on the "order-events" topic. When order-service creates an order,
// it publishes an OrderPlacedEvent. This consumer picks it up and simulates
// sending an order confirmation notification.

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "order-events", groupId = "notification-group")
    public void handleOrderPlaced(String message) {
        try {
            OrderPlacedEvent event = objectMapper.readValue(message, OrderPlacedEvent.class);

            log.info("===== NOTIFICATION: Order Confirmation =====");
            log.info("To: User #{}", event.getUserId());
            log.info("Subject: Order #{} {}!", event.getOrderId(), event.getStatus());
            log.info("Body: Your order of {} item(s) totaling ${} has been {}.",
                    event.getItemCount(), event.getTotalAmount(), event.getStatus().toLowerCase());
            log.info("=============================================");

        } catch (Exception e) {
            log.error("Failed to process order event: {}. Sending to DLT.", e.getMessage());
            kafkaTemplate.send("order-events-dlt", message);
        }
    }
}
