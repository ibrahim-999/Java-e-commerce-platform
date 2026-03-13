package com.ecommerce.orderservice.consumer;

import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer Unit Tests")
class PaymentEventConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentEventConsumer consumer;

    private Order pendingOrder;
    private Order paymentFailedOrder;
    private Order confirmedOrder;

    @BeforeEach
    void setUp() {
        // Build a PENDING order with 2 items
        pendingOrder = Order.builder()
                .id(1L)
                .userId(10L)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(299.98))
                .build();
        pendingOrder.addItem(OrderItem.builder()
                .id(1L).productId(100L).quantity(2)
                .priceAtPurchase(BigDecimal.valueOf(99.99))
                .productNameSnapshot("Product A")
                .build());
        pendingOrder.addItem(OrderItem.builder()
                .id(2L).productId(200L).quantity(1)
                .priceAtPurchase(BigDecimal.valueOf(100.00))
                .productNameSnapshot("Product B")
                .build());

        // Build a PAYMENT_FAILED order with 1 item
        paymentFailedOrder = Order.builder()
                .id(2L)
                .userId(10L)
                .status(OrderStatus.PAYMENT_FAILED)
                .totalAmount(BigDecimal.valueOf(199.98))
                .build();
        paymentFailedOrder.addItem(OrderItem.builder()
                .id(3L).productId(100L).quantity(2)
                .priceAtPurchase(BigDecimal.valueOf(99.99))
                .productNameSnapshot("Product A")
                .build());

        // Build a CONFIRMED order with 1 item
        confirmedOrder = Order.builder()
                .id(3L)
                .userId(10L)
                .status(OrderStatus.CONFIRMED)
                .paymentId(50L)
                .totalAmount(BigDecimal.valueOf(99.99))
                .build();
        confirmedOrder.addItem(OrderItem.builder()
                .id(4L).productId(300L).quantity(1)
                .priceAtPurchase(BigDecimal.valueOf(99.99))
                .productNameSnapshot("Product C")
                .build());
    }

    // ==================== handlePaymentCompleted ====================

    @Nested
    @DisplayName("handlePaymentCompleted")
    class HandlePaymentCompleted {

        @Test
        @DisplayName("should confirm PENDING order when payment completed")
        void shouldConfirmPendingOrder() {
            String event = """
                    {"orderId": 1, "status": "COMPLETED", "paymentId": 42}
                    """;

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            consumer.handlePaymentEvent(event);

            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(pendingOrder.getPaymentId()).isEqualTo(42L);
            verify(orderRepository).save(pendingOrder);
        }

        @Test
        @DisplayName("should confirm PAYMENT_FAILED order when payment completed (async reconciliation)")
        void shouldConfirmPaymentFailedOrder() {
            String event = """
                    {"orderId": 2, "status": "COMPLETED", "paymentId": 43}
                    """;

            when(orderRepository.findById(2L)).thenReturn(Optional.of(paymentFailedOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            consumer.handlePaymentEvent(event);

            assertThat(paymentFailedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(paymentFailedOrder.getPaymentId()).isEqualTo(43L);
            verify(orderRepository).save(paymentFailedOrder);
        }

        @Test
        @DisplayName("should ignore duplicate COMPLETED event for already confirmed order")
        void shouldIgnoreDuplicateCompletedEvent() {
            String event = """
                    {"orderId": 3, "status": "COMPLETED", "paymentId": 50}
                    """;

            when(orderRepository.findById(3L)).thenReturn(Optional.of(confirmedOrder));

            consumer.handlePaymentEvent(event);

            // Already CONFIRMED — should not save again
            assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should record status change in audit trail")
        void shouldRecordStatusHistory() {
            String event = """
                    {"orderId": 1, "status": "COMPLETED", "paymentId": 42}
                    """;

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            consumer.handlePaymentEvent(event);

            assertThat(pendingOrder.getStatusHistory()).hasSize(1);
            assertThat(pendingOrder.getStatusHistory().get(0).getFromStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(pendingOrder.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(pendingOrder.getStatusHistory().get(0).getReason())
                    .contains("Saga async reconciliation");
        }
    }

    // ==================== handlePaymentFailed ====================

    @Nested
    @DisplayName("handlePaymentFailed")
    class HandlePaymentFailed {

        @Test
        @DisplayName("should restore stock and mark PAYMENT_FAILED for PENDING order")
        void shouldCompensatePendingOrder() {
            String event = """
                    {"orderId": 1, "status": "FAILED", "paymentId": 44}
                    """;

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            consumer.handlePaymentEvent(event);

            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            // Verify stock restored for both items
            verify(orderService).restoreProductStock(100L, 2);
            verify(orderService).restoreProductStock(200L, 1);
            verify(orderRepository).save(pendingOrder);
        }

        @Test
        @DisplayName("should ignore FAILED event for already PAYMENT_FAILED order")
        void shouldIgnoreDuplicateFailedEvent() {
            String event = """
                    {"orderId": 2, "status": "FAILED", "paymentId": 44}
                    """;

            when(orderRepository.findById(2L)).thenReturn(Optional.of(paymentFailedOrder));

            consumer.handlePaymentEvent(event);

            // Already compensated — should not restore stock or save again
            verify(orderService, never()).restoreProductStock(anyLong(), anyInt());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should continue compensation even if one stock restore fails")
        void shouldContinueIfStockRestoreFails() {
            String event = """
                    {"orderId": 1, "status": "FAILED", "paymentId": 44}
                    """;

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            // First item restore fails, second should still be attempted
            doThrow(new RuntimeException("Product service down"))
                    .when(orderService).restoreProductStock(100L, 2);

            consumer.handlePaymentEvent(event);

            // Both restores attempted despite first one failing
            verify(orderService).restoreProductStock(100L, 2);
            verify(orderService).restoreProductStock(200L, 1);
            // Order still transitions to PAYMENT_FAILED
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            verify(orderRepository).save(pendingOrder);
        }

        @Test
        @DisplayName("should record compensation in audit trail")
        void shouldRecordCompensationHistory() {
            String event = """
                    {"orderId": 1, "status": "FAILED", "paymentId": 44}
                    """;

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            consumer.handlePaymentEvent(event);

            assertThat(pendingOrder.getStatusHistory()).hasSize(1);
            assertThat(pendingOrder.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            assertThat(pendingOrder.getStatusHistory().get(0).getReason())
                    .contains("Saga async compensation");
        }
    }

    // ==================== handlePaymentRefunded ====================

    @Nested
    @DisplayName("handlePaymentRefunded")
    class HandlePaymentRefunded {

        @Test
        @DisplayName("should restore stock and cancel CONFIRMED order on refund")
        void shouldCancelConfirmedOrderOnRefund() {
            String event = """
                    {"orderId": 3, "status": "REFUNDED", "paymentId": 50}
                    """;

            when(orderRepository.findById(3L)).thenReturn(Optional.of(confirmedOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            consumer.handlePaymentEvent(event);

            assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderService).restoreProductStock(300L, 1);
            verify(orderRepository).save(confirmedOrder);
        }

        @Test
        @DisplayName("should ignore refund for non-CONFIRMED order")
        void shouldIgnoreRefundForNonConfirmedOrder() {
            String event = """
                    {"orderId": 1, "status": "REFUNDED", "paymentId": 50}
                    """;

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

            consumer.handlePaymentEvent(event);

            // PENDING order can't be refunded — ignore
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderService, never()).restoreProductStock(anyLong(), anyInt());
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should continue compensation even if stock restore fails during refund")
        void shouldContinueIfStockRestoreFailsDuringRefund() {
            // Add a second item to confirmed order for this test
            confirmedOrder.addItem(OrderItem.builder()
                    .id(5L).productId(400L).quantity(3)
                    .priceAtPurchase(BigDecimal.valueOf(50.00))
                    .productNameSnapshot("Product D")
                    .build());

            String event = """
                    {"orderId": 3, "status": "REFUNDED", "paymentId": 50}
                    """;

            when(orderRepository.findById(3L)).thenReturn(Optional.of(confirmedOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            // First item restore fails
            doThrow(new RuntimeException("Product service down"))
                    .when(orderService).restoreProductStock(300L, 1);

            consumer.handlePaymentEvent(event);

            // Both restores attempted
            verify(orderService).restoreProductStock(300L, 1);
            verify(orderService).restoreProductStock(400L, 3);
            // Order still cancelled
            assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("should record refund cancellation in audit trail")
        void shouldRecordRefundHistory() {
            String event = """
                    {"orderId": 3, "status": "REFUNDED", "paymentId": 50}
                    """;

            when(orderRepository.findById(3L)).thenReturn(Optional.of(confirmedOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            consumer.handlePaymentEvent(event);

            assertThat(confirmedOrder.getStatusHistory()).hasSize(1);
            assertThat(confirmedOrder.getStatusHistory().get(0).getFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(confirmedOrder.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(confirmedOrder.getStatusHistory().get(0).getReason())
                    .contains("Payment refunded");
        }
    }

    // ==================== Edge cases ====================

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should skip event for unknown order")
        void shouldSkipUnknownOrder() {
            String event = """
                    {"orderId": 999, "status": "COMPLETED", "paymentId": 42}
                    """;

            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            consumer.handlePaymentEvent(event);

            verify(orderRepository, never()).save(any());
            verify(orderService, never()).restoreProductStock(anyLong(), anyInt());
        }

        @Test
        @DisplayName("should handle unknown payment status gracefully")
        void shouldHandleUnknownStatus() {
            String event = """
                    {"orderId": 1, "status": "PROCESSING", "paymentId": 42}
                    """;

            when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

            consumer.handlePaymentEvent(event);

            // Unknown status — no state change, no save
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should send malformed message to DLT")
        void shouldSendMalformedMessageToDlt() {
            String badMessage = "not valid json {{{";

            consumer.handlePaymentEvent(badMessage);

            verify(kafkaTemplate).send("payment-events-dlt", badMessage);
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("should send message with missing fields to DLT")
        void shouldSendIncompleteMessageToDlt() {
            // Missing paymentId field
            String event = """
                    {"orderId": 1, "status": "COMPLETED"}
                    """;

            consumer.handlePaymentEvent(event);

            verify(kafkaTemplate).send("payment-events-dlt", event);
        }
    }
}
