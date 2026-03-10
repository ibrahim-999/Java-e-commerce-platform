package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.exception.ServiceUnavailableException;
import com.ecommerce.orderservice.factory.OrderFactory;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.model.OrderStatusHistory;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.repository.OrderStatusHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Additional unit tests for OrderService — covers methods not tested in OrderServiceTest:
// - getOrderById edge cases
// - getOrdersByUserId (paginated)
// - getOrderHistory (audit trail)
// - cancelOrder on DELIVERED orders
// - cancelOrder restoring stock for multiple items

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Additional Unit Tests")
class OrderServiceAdditionalTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStatusHistoryRepository statusHistoryRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Spy
    @InjectMocks
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("getOrderById")
    class GetOrderById {

        @Test
        @DisplayName("should return order with items when found")
        void shouldReturnOrderWithItemsWhenFound() {
            Order order = Order.builder()
                    .id(1L)
                    .userId(100L)
                    .status(OrderStatus.CONFIRMED)
                    .totalAmount(BigDecimal.valueOf(2499.97))
                    .build();

            OrderItem item1 = OrderItem.builder()
                    .id(1L)
                    .productId(10L)
                    .quantity(1)
                    .priceAtPurchase(BigDecimal.valueOf(999.99))
                    .productNameSnapshot("iPhone 16")
                    .build();
            OrderItem item2 = OrderItem.builder()
                    .id(2L)
                    .productId(20L)
                    .quantity(3)
                    .priceAtPurchase(BigDecimal.valueOf(499.99))
                    .productNameSnapshot("AirPods Pro")
                    .build();
            order.addItem(item1);
            order.addItem(item2);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            Order result = orderService.getOrderById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUserId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getItems().get(0).getProductNameSnapshot()).isEqualTo("iPhone 16");
            assertThat(result.getItems().get(1).getProductNameSnapshot()).isEqualTo("AirPods Pro");

            verify(orderRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order does not exist")
        void shouldThrowWhenOrderDoesNotExist() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found with id: 999");

            verify(orderRepository).findById(999L);
        }
    }

    @Nested
    @DisplayName("getOrdersByUserId")
    class GetOrdersByUserId {

        @Test
        @DisplayName("should return paginated orders for a user")
        void shouldReturnPaginatedOrdersForUser() {
            Order order1 = Order.builder()
                    .id(1L).userId(100L).status(OrderStatus.PENDING)
                    .totalAmount(BigDecimal.valueOf(500)).build();
            Order order2 = Order.builder()
                    .id(2L).userId(100L).status(OrderStatus.CONFIRMED)
                    .totalAmount(BigDecimal.valueOf(750)).build();

            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> orderPage = new PageImpl<>(List.of(order1, order2), pageable, 2);

            when(orderRepository.findByUserId(100L, pageable)).thenReturn(orderPage);

            Page<Order> result = orderService.getOrdersByUserId(100L, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
            assertThat(result.getContent().get(1).getId()).isEqualTo(2L);

            verify(orderRepository).findByUserId(100L, pageable);
        }

        @Test
        @DisplayName("should return empty page when user has no orders")
        void shouldReturnEmptyPageWhenNoOrders() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(orderRepository.findByUserId(999L, pageable)).thenReturn(emptyPage);

            Page<Order> result = orderService.getOrdersByUserId(999L, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();

            verify(orderRepository).findByUserId(999L, pageable);
        }

        @Test
        @DisplayName("should respect pagination parameters")
        void shouldRespectPaginationParameters() {
            Order order = Order.builder()
                    .id(3L).userId(100L).status(OrderStatus.SHIPPED)
                    .totalAmount(BigDecimal.valueOf(200)).build();

            Pageable pageable = PageRequest.of(1, 2);
            Page<Order> orderPage = new PageImpl<>(List.of(order), pageable, 3);

            when(orderRepository.findByUserId(100L, pageable)).thenReturn(orderPage);

            Page<Order> result = orderService.getOrdersByUserId(100L, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(3);
            assertThat(result.getTotalPages()).isEqualTo(2);
            assertThat(result.getNumber()).isEqualTo(1);

            verify(orderRepository).findByUserId(100L, pageable);
        }
    }

    @Nested
    @DisplayName("getOrderHistory")
    class GetOrderHistory {

        @Test
        @DisplayName("should return status history for an order")
        void shouldReturnStatusHistoryForOrder() {
            Order order = Order.builder()
                    .id(1L).userId(100L).status(OrderStatus.CONFIRMED)
                    .totalAmount(BigDecimal.valueOf(100)).build();

            OrderStatusHistory history1 = OrderStatusHistory.builder()
                    .id(1L)
                    .order(order)
                    .fromStatus(null)
                    .toStatus(OrderStatus.PENDING)
                    .reason("Order placed")
                    .changedAt(LocalDateTime.now().minusHours(2))
                    .build();
            OrderStatusHistory history2 = OrderStatusHistory.builder()
                    .id(2L)
                    .order(order)
                    .fromStatus(OrderStatus.PENDING)
                    .toStatus(OrderStatus.CONFIRMED)
                    .reason("Payment completed")
                    .changedAt(LocalDateTime.now().minusHours(1))
                    .build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(1L))
                    .thenReturn(List.of(history1, history2));

            List<OrderStatusHistory> result = orderService.getOrderHistory(1L);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getFromStatus()).isNull();
            assertThat(result.get(0).getToStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.get(0).getReason()).isEqualTo("Order placed");
            assertThat(result.get(1).getFromStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.get(1).getToStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(result.get(1).getReason()).isEqualTo("Payment completed");

            verify(orderRepository).findById(1L);
            verify(statusHistoryRepository).findByOrderIdOrderByChangedAtAsc(1L);
        }

        @Test
        @DisplayName("should return empty list when order has no history entries")
        void shouldReturnEmptyListWhenNoHistory() {
            Order order = Order.builder()
                    .id(1L).userId(100L).status(OrderStatus.PENDING)
                    .totalAmount(BigDecimal.valueOf(100)).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(1L))
                    .thenReturn(List.of());

            List<OrderStatusHistory> result = orderService.getOrderHistory(1L);

            assertThat(result).isEmpty();

            verify(orderRepository).findById(1L);
            verify(statusHistoryRepository).findByOrderIdOrderByChangedAtAsc(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when order does not exist")
        void shouldThrowWhenOrderDoesNotExistForHistory() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderHistory(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found with id: 99");

            verify(orderRepository).findById(99L);
            verify(statusHistoryRepository, never()).findByOrderIdOrderByChangedAtAsc(any());
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("should throw IllegalArgumentException when cancelling delivered order")
        void shouldThrowWhenCancellingDeliveredOrder() {
            Order deliveredOrder = Order.builder()
                    .id(1L)
                    .userId(100L)
                    .status(OrderStatus.DELIVERED)
                    .totalAmount(BigDecimal.valueOf(500))
                    .build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(deliveredOrder));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Cannot cancel a delivered order");

            verify(orderRepository).findById(1L);
        }

        @Test
        @DisplayName("should restore stock for multiple items on cancellation")
        void shouldRestoreStockForMultipleItemsOnCancellation() {
            Order order = Order.builder()
                    .id(1L)
                    .userId(100L)
                    .status(OrderStatus.CONFIRMED)
                    .totalAmount(BigDecimal.valueOf(3000))
                    .build();

            OrderItem item1 = OrderItem.builder()
                    .id(1L).productId(10L).quantity(2)
                    .priceAtPurchase(BigDecimal.valueOf(999.99))
                    .productNameSnapshot("iPhone 16")
                    .build();
            OrderItem item2 = OrderItem.builder()
                    .id(2L).productId(20L).quantity(3)
                    .priceAtPurchase(BigDecimal.valueOf(299.99))
                    .productNameSnapshot("AirPods Pro")
                    .build();
            OrderItem item3 = OrderItem.builder()
                    .id(3L).productId(30L).quantity(1)
                    .priceAtPurchase(BigDecimal.valueOf(49.99))
                    .productNameSnapshot("Phone Case")
                    .build();
            order.addItem(item1);
            order.addItem(item2);
            order.addItem(item3);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            doNothing().when(orderService).restoreProductStock(anyLong(), anyInt());

            Order result = orderService.cancelOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(result.getStatusHistory()).hasSize(1);
            assertThat(result.getStatusHistory().get(0).getFromStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(result.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.CANCELLED);

            // Verify stock was restored for all three items
            verify(orderService).restoreProductStock(10L, 2);
            verify(orderService).restoreProductStock(20L, 3);
            verify(orderService).restoreProductStock(30L, 1);
        }

        @Test
        @DisplayName("should cancel shipped order successfully")
        void shouldCancelShippedOrder() {
            Order shippedOrder = Order.builder()
                    .id(1L)
                    .userId(100L)
                    .status(OrderStatus.SHIPPED)
                    .totalAmount(BigDecimal.valueOf(500))
                    .build();

            OrderItem item = OrderItem.builder()
                    .id(1L).productId(10L).quantity(1)
                    .priceAtPurchase(BigDecimal.valueOf(500))
                    .productNameSnapshot("Laptop")
                    .build();
            shippedOrder.addItem(item);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(shippedOrder));
            doNothing().when(orderService).restoreProductStock(10L, 1);

            Order result = orderService.cancelOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(result.getStatusHistory()).hasSize(1);
            assertThat(result.getStatusHistory().get(0).getFromStatus()).isEqualTo(OrderStatus.SHIPPED);
            assertThat(result.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(result.getStatusHistory().get(0).getReason()).isEqualTo("Order cancelled by user");

            verify(orderService).restoreProductStock(10L, 1);
        }

        @Test
        @DisplayName("should still cancel order even if stock restoration fails for some items")
        void shouldStillCancelEvenIfStockRestorationFails() {
            Order order = Order.builder()
                    .id(1L)
                    .userId(100L)
                    .status(OrderStatus.PENDING)
                    .totalAmount(BigDecimal.valueOf(1500))
                    .build();

            OrderItem item1 = OrderItem.builder()
                    .id(1L).productId(10L).quantity(1)
                    .priceAtPurchase(BigDecimal.valueOf(1000))
                    .productNameSnapshot("Product A")
                    .build();
            OrderItem item2 = OrderItem.builder()
                    .id(2L).productId(20L).quantity(1)
                    .priceAtPurchase(BigDecimal.valueOf(500))
                    .productNameSnapshot("Product B")
                    .build();
            order.addItem(item1);
            order.addItem(item2);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            // First item stock restore succeeds, second fails
            doNothing().when(orderService).restoreProductStock(10L, 1);
            doThrow(new RuntimeException("Service unavailable"))
                    .when(orderService).restoreProductStock(20L, 1);

            // Should still cancel despite stock restore failure for one item
            Order result = orderService.cancelOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);

            verify(orderService).restoreProductStock(10L, 1);
            verify(orderService).restoreProductStock(20L, 1);
        }

        @Test
        @DisplayName("should throw when order not found for cancellation")
        void shouldThrowWhenOrderNotFoundForCancellation() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found with id: 99");

            verify(orderRepository).findById(99L);
        }
    }

    @Nested
    @DisplayName("Fallback Methods")
    class FallbackMethods {

        @Test
        @DisplayName("validateUserFallback should throw ResourceNotFoundException for 404")
        void validateUserFallbackShouldThrowNotFoundFor404() {
            WebClientResponseException.NotFound notFound = mock(WebClientResponseException.NotFound.class);
            when(notFound.getMessage()).thenReturn("Not Found");

            assertThatThrownBy(() -> orderService.validateUserFallback(1L, notFound))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User not found with id: 1");
        }

        @Test
        @DisplayName("validateUserFallback should throw ServiceUnavailableException for other errors")
        void validateUserFallbackShouldThrowServiceUnavailableForOtherErrors() {
            RuntimeException error = new RuntimeException("Connection refused");

            assertThatThrownBy(() -> orderService.validateUserFallback(1L, error))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("User service");
        }

        @Test
        @DisplayName("getProductFallback should throw ResourceNotFoundException for 404")
        void getProductFallbackShouldThrowNotFoundFor404() {
            WebClientResponseException.NotFound notFound = mock(WebClientResponseException.NotFound.class);
            when(notFound.getMessage()).thenReturn("Not Found");

            assertThatThrownBy(() -> orderService.getProductFallback(10L, notFound))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found with id: 10");
        }

        @Test
        @DisplayName("getProductFallback should throw ServiceUnavailableException for other errors")
        void getProductFallbackShouldThrowServiceUnavailableForOtherErrors() {
            RuntimeException error = new RuntimeException("Timeout");

            assertThatThrownBy(() -> orderService.getProductFallback(10L, error))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("Product service");
        }

        @Test
        @DisplayName("reduceProductStockFallback should throw IllegalArgumentException for 400")
        void reduceProductStockFallbackShouldThrowIllegalArgFor400() {
            WebClientResponseException.BadRequest badRequest = mock(WebClientResponseException.BadRequest.class);
            when(badRequest.getMessage()).thenReturn("Bad Request");

            assertThatThrownBy(() -> orderService.reduceProductStockFallback(10L, 5, badRequest))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Insufficient stock for product 10");
        }

        @Test
        @DisplayName("reduceProductStockFallback should throw ServiceUnavailableException for other errors")
        void reduceProductStockFallbackShouldThrowServiceUnavailableForOtherErrors() {
            RuntimeException error = new RuntimeException("Service down");

            assertThatThrownBy(() -> orderService.reduceProductStockFallback(10L, 5, error))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("Product service");
        }

        @Test
        @DisplayName("restoreProductStockFallback should log error but not throw")
        void restoreProductStockFallbackShouldNotThrow() {
            RuntimeException error = new RuntimeException("Service down");

            // Should not throw — just logs for manual resolution
            orderService.restoreProductStockFallback(10L, 5, error);
        }

        @Test
        @DisplayName("initiatePaymentFallback should throw ServiceUnavailableException")
        void initiatePaymentFallbackShouldThrowServiceUnavailable() {
            RuntimeException error = new RuntimeException("Payment service unreachable");

            assertThatThrownBy(() -> orderService.initiatePaymentFallback(
                    1L, 100L, BigDecimal.valueOf(500), "CREDIT_CARD", error))
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasMessageContaining("Payment service");
        }
    }

    @Nested
    @DisplayName("createOrder - additional paths")
    class CreateOrderAdditional {

        @Test
        @DisplayName("should keep order PENDING when payment status is not COMPLETED")
        void shouldKeepOrderPendingWhenPaymentNotCompleted() throws Exception {
            doNothing().when(orderService).validateUser(1L);

            JsonNode productResponse = objectMapper.readTree("""
                    {"success": true, "data": {"id": 10, "name": "Laptop", "price": "500.00"}}
                    """);
            doReturn(productResponse).when(orderService).getProduct(10L);
            doNothing().when(orderService).reduceProductStock(10L, 1);

            // Payment returns PENDING (not COMPLETED)
            JsonNode paymentResponse = objectMapper.readTree("""
                    {
                        "success": true,
                        "data": {
                            "id": 200,
                            "status": "PENDING",
                            "transactionId": "txn_pending_abc"
                        }
                    }
                    """);
            doReturn(paymentResponse).when(orderService)
                    .initiatePayment(any(), eq(1L), any(BigDecimal.class), eq("CREDIT_CARD"));

            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                if (saved.getId() == null) saved.setId(1L);
                return saved;
            });

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(1L)
                    .items(List.of(new OrderItemRequest(10L, 1)))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            Order result = orderService.createOrder(request);

            // Order stays PENDING because payment wasn't COMPLETED
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.getPaymentId()).isEqualTo(200L);
            // Status history should only have the initial PENDING entry
            assertThat(result.getStatusHistory()).hasSize(1);
            assertThat(result.getStatusHistory().get(0).getToStatus()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("should handle rollback exception during compensating transaction")
        void shouldHandleRollbackExceptionDuringCompensation() throws Exception {
            doNothing().when(orderService).validateUser(1L);

            // First product succeeds
            JsonNode product1 = objectMapper.readTree("""
                    {"success": true, "data": {"id": 10, "name": "Product A", "price": "100.00"}}
                    """);
            doReturn(product1).when(orderService).getProduct(10L);
            doNothing().when(orderService).reduceProductStock(10L, 1);

            // Second product fails
            doThrow(new ResourceNotFoundException("Product", "id", 20L))
                    .when(orderService).getProduct(20L);

            // Restoring stock also fails (simulates cascading failure)
            doThrow(new RuntimeException("Restore failed"))
                    .when(orderService).restoreProductStock(10L, 1);

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(1L)
                    .items(List.of(
                            new OrderItemRequest(10L, 1),
                            new OrderItemRequest(20L, 1)
                    ))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            // The original exception should still be thrown despite rollback failure
            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Verify restore was attempted
            verify(orderService).restoreProductStock(10L, 1);
        }

        @Test
        @DisplayName("should create order with multiple items and correct total")
        void shouldCreateOrderWithMultipleItemsAndCorrectTotal() throws Exception {
            doNothing().when(orderService).validateUser(1L);

            JsonNode product1 = objectMapper.readTree("""
                    {"success": true, "data": {"id": 10, "name": "Widget A", "price": "25.00"}}
                    """);
            JsonNode product2 = objectMapper.readTree("""
                    {"success": true, "data": {"id": 20, "name": "Widget B", "price": "50.00"}}
                    """);
            doReturn(product1).when(orderService).getProduct(10L);
            doReturn(product2).when(orderService).getProduct(20L);
            doNothing().when(orderService).reduceProductStock(anyLong(), anyInt());

            // Payment completes successfully
            JsonNode paymentResponse = objectMapper.readTree("""
                    {"success": true, "data": {"id": 300, "status": "COMPLETED", "transactionId": "txn_xyz"}}
                    """);
            doReturn(paymentResponse).when(orderService)
                    .initiatePayment(any(), eq(1L), any(BigDecimal.class), eq("DEBIT_CARD"));

            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                if (saved.getId() == null) saved.setId(1L);
                return saved;
            });

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(1L)
                    .items(List.of(
                            new OrderItemRequest(10L, 3),  // 3 x 25.00 = 75.00
                            new OrderItemRequest(20L, 2)   // 2 x 50.00 = 100.00
                    ))
                    .paymentMethod("DEBIT_CARD")
                    .build();

            Order result = orderService.createOrder(request);

            assertThat(result.getItems()).hasSize(2);
            // Total = 75.00 + 100.00 = 175.00
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(175.00));
            assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

            verify(orderService).reduceProductStock(10L, 3);
            verify(orderService).reduceProductStock(20L, 2);
        }
    }
}
