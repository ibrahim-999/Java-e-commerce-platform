package com.ecommerce.notificationservice.consumer;

import com.ecommerce.notificationservice.event.UserRegisteredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Kafka Consumer for user-related events.
//
// How @KafkaListener works:
//   1. Spring Kafka creates a background thread (the "consumer poll loop")
//   2. The thread continuously polls Kafka for new messages on the specified topic
//   3. When a message arrives, Spring calls this method with the message content
//   4. After the method returns successfully, Kafka marks the message as "consumed"
//      (commits the offset) so it won't be delivered again
//
// If the method THROWS an exception:
//   - Kafka does NOT commit the offset
//   - The message will be retried (default: 10 times)
//   - If all retries fail, we send it to a Dead Letter Topic (DLT) for manual investigation

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    // topics: which Kafka topic to listen to
    // groupId: consumer group — ensures only one instance processes each message
    @KafkaListener(topics = "user-events", groupId = "notification-group")
    public void handleUserRegistered(String message) {
        try {
            // Deserialize JSON string into our event object
            UserRegisteredEvent event = objectMapper.readValue(message, UserRegisteredEvent.class);

            // In production: send a welcome email via SendGrid, AWS SES, etc.
            // For now: log it as proof the event was consumed
            log.info("===== NOTIFICATION: Welcome Email =====");
            log.info("To: {}", event.getEmail());
            log.info("Subject: Welcome to our platform, {}!", event.getFirstName());
            log.info("Body: Hi {} {}, your account has been created successfully. User ID: {}",
                    event.getFirstName(), event.getLastName(), event.getUserId());
            log.info("========================================");

        } catch (Exception e) {
            // If deserialization or processing fails, send to Dead Letter Topic.
            // A Dead Letter Topic (DLT) holds messages that couldn't be processed.
            // Operations team can investigate and replay them later.
            log.error("Failed to process user event: {}. Sending to DLT.", e.getMessage());
            kafkaTemplate.send("user-events-dlt", message);
        }
    }
}
