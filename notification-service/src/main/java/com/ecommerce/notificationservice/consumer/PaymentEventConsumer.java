package com.ecommerce.notificationservice.consumer;

import com.ecommerce.notificationservice.event.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Kafka Consumer for payment-related events.
//
// Listens on the "payment-events" topic. When payment-service processes a charge,
// refund, or failure, it publishes a PaymentProcessedEvent.

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "payment-events", groupId = "notification-group")
    public void handlePaymentProcessed(String message) {
        try {
            PaymentProcessedEvent event = objectMapper.readValue(message, PaymentProcessedEvent.class);

            switch (event.getStatus()) {
                case "COMPLETED" -> {
                    log.info("===== NOTIFICATION: Payment Successful =====");
                    log.info("To: User #{}", event.getUserId());
                    log.info("Subject: Payment of ${} confirmed!", event.getAmount());
                    log.info("Body: Your {} payment for order #{} was successful. Transaction: {}",
                            event.getPaymentMethod(), event.getOrderId(), event.getTransactionId());
                    log.info("==============================================");
                }
                case "FAILED" -> {
                    log.info("===== NOTIFICATION: Payment Failed =====");
                    log.info("To: User #{}", event.getUserId());
                    log.info("Subject: Payment of ${} failed", event.getAmount());
                    log.info("Body: Your {} payment for order #{} could not be processed. Please try again.",
                            event.getPaymentMethod(), event.getOrderId());
                    log.info("==========================================");
                }
                case "REFUNDED" -> {
                    log.info("===== NOTIFICATION: Refund Processed =====");
                    log.info("To: User #{}", event.getUserId());
                    log.info("Subject: Refund of ${} processed!", event.getAmount());
                    log.info("Body: Your refund for order #{} has been processed via {}.",
                            event.getOrderId(), event.getPaymentMethod());
                    log.info("============================================");
                }
                default -> log.warn("Unknown payment status: {}", event.getStatus());
            }

        } catch (Exception e) {
            log.error("Failed to process payment event: {}. Sending to DLT.", e.getMessage());
            kafkaTemplate.send("payment-events-dlt", message);
        }
    }
}
