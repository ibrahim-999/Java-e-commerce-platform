package com.ecommerce.orderservice.consumer;

import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Kafka Consumer for ASYNC SAGA COMPENSATION.
//
// WHY does this exist?
//   The createOrder() flow calls payment-service synchronously. But what if:
//     1. The HTTP call TIMES OUT, but payment-service actually processed the payment?
//     2. Payment-service was DOWN, processed the payment later, and published an event?
//
//   In both cases, the order is stuck in PAYMENT_FAILED, but the payment actually succeeded.
//   This consumer listens for payment-events and reconciles the order status.
//
// HOW it works:
//   payment-service publishes to "payment-events" topic after every payment attempt.
//   This consumer reads those events with consumer group "order-saga-group".
//   (notification-service also reads them with group "notification-group" — both get all messages.)
//
//   On COMPLETED event: if order is PENDING/PAYMENT_FAILED → mark CONFIRMED
//   On FAILED event:    if order is PENDING → restore stock, mark PAYMENT_FAILED
//
// This makes the saga EVENTUALLY CONSISTENT — even if the synchronous call failed,
// the system self-heals when the event arrives.

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(topics = "payment-events", groupId = "order-saga-group")
    @Transactional
    public void handlePaymentEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);

            Long orderId = event.get("orderId").asLong();
            String paymentStatus = event.get("status").asText();
            Long paymentId = event.get("paymentId").asLong();

            Optional<Order> optionalOrder = orderRepository.findById(orderId);
            if (optionalOrder.isEmpty()) {
                log.warn("Saga event: Received payment event for unknown order {}", orderId);
                return;
            }

            Order order = optionalOrder.get();

            switch (paymentStatus) {
                case "COMPLETED" -> handlePaymentCompleted(order, paymentId);
                case "FAILED" -> handlePaymentFailed(order);
                case "REFUNDED" -> handlePaymentRefunded(order);
                default -> log.warn("Saga event: Unknown payment status '{}' for order {}", paymentStatus, orderId);
            }

        } catch (Exception e) {
            log.error("Saga event: Failed to process payment event: {}. Sending to DLT.", e.getMessage());
            kafkaTemplate.send("payment-events-dlt", message);
        }
    }

    private void handlePaymentCompleted(Order order, Long paymentId) {
        // If order is PENDING or PAYMENT_FAILED, the synchronous call missed the result.
        // The payment actually went through — confirm the order.
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            order.setPaymentId(paymentId);
            order.changeStatus(OrderStatus.CONFIRMED,
                    "Saga async reconciliation: payment " + paymentId + " confirmed via event");
            orderRepository.save(order);
            log.info("Saga ASYNC RECONCILIATION: Order {} confirmed via payment event (paymentId: {})",
                    order.getId(), paymentId);
        }
        // If already CONFIRMED, this is a duplicate event — safe to ignore
    }

    private void handlePaymentFailed(Order order) {
        // If order is still PENDING (synchronous call didn't handle the failure),
        // trigger compensation: restore stock and mark PAYMENT_FAILED
        if (order.getStatus() == OrderStatus.PENDING) {
            log.info("Saga ASYNC COMPENSATION: Payment failed for order {}, restoring stock", order.getId());
            for (var item : order.getItems()) {
                try {
                    orderService.restoreProductStock(item.getProductId(), item.getQuantity());
                } catch (Exception e) {
                    log.error("CRITICAL: Async compensation failed to restore stock for product {}: {}",
                            item.getProductId(), e.getMessage());
                }
            }
            order.changeStatus(OrderStatus.PAYMENT_FAILED,
                    "Saga async compensation: payment failed, stock restored via event");
            orderRepository.save(order);
        }
        // If already PAYMENT_FAILED, synchronous handler already compensated — ignore
    }

    private void handlePaymentRefunded(Order order) {
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            // Payment was refunded — restore stock and cancel the order
            log.info("Saga REFUND: Payment refunded for order {}, restoring stock", order.getId());
            for (var item : order.getItems()) {
                try {
                    orderService.restoreProductStock(item.getProductId(), item.getQuantity());
                } catch (Exception e) {
                    log.error("CRITICAL: Refund compensation failed to restore stock for product {}: {}",
                            item.getProductId(), e.getMessage());
                }
            }
            order.changeStatus(OrderStatus.CANCELLED,
                    "Payment refunded, stock restored via event");
            orderRepository.save(order);
        }
    }
}
