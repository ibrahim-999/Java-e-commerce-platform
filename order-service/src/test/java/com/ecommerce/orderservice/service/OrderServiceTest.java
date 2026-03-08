package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Unit tests for OrderService.
// We mock the WebClient calls (validateUser, getProduct, etc.)
// because unit tests should NOT make real HTTP calls.

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    // @Spy lets us mock SOME methods while keeping real ones.
    // We mock the inter-service methods (validateUser, getProduct, etc.)
    // but keep the business logic methods (createOrder, cancelOrder) real.
    @Spy
    @InjectMocks
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("should create order with snapshotted prices")
        void shouldCreateOrderWithSnapshottedPrices() throws Exception {
            // Arrange: mock inter-service calls
            doNothing().when(orderService).validateUser(1L);

            // Mock product response from product-service
            JsonNode productResponse = objectMapper.readTree("""
                    {
                        "success": true,
                        "data": {
                            "id": 10,
                            "name": "iPhone 16",
                            "price": "999.99",
                            "stockQuantity": 50
                        }
                    }
                    """);
            doReturn(productResponse).when(orderService).getProduct(10L);
            doNothing().when(orderService).reduceProductStock(10L, 2);

            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(1L)
                    .items(List.of(new OrderItemRequest(10L, 2)))
                    .build();

            // Act
            Order result = orderService.createOrder(request);

            // Assert
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.getItems()).hasSize(1);

            OrderItem item = result.getItems().get(0);
            assertThat(item.getProductId()).isEqualTo(10L);
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getPriceAtPurchase()).isEqualByComparingTo(BigDecimal.valueOf(999.99));
            assertThat(item.getProductNameSnapshot()).isEqualTo("iPhone 16");

            // Total = 999.99 * 2 = 1999.98
            assertThat(result.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(1999.98));

            verify(orderService).validateUser(1L);
            verify(orderService).getProduct(10L);
            verify(orderService).reduceProductStock(10L, 2);
        }

        @Test
        @DisplayName("should restore stock if product validation fails mid-order")
        void shouldRestoreStockOnFailure() throws Exception {
            doNothing().when(orderService).validateUser(1L);

            // First product succeeds
            JsonNode product1 = objectMapper.readTree("""
                    {"success": true, "data": {"id": 10, "name": "iPhone", "price": "999.99"}}
                    """);
            doReturn(product1).when(orderService).getProduct(10L);
            doNothing().when(orderService).reduceProductStock(10L, 1);

            // Second product fails (not found)
            doThrow(new ResourceNotFoundException("Product", "id", 20L))
                    .when(orderService).getProduct(20L);

            // Restore stock should be called for the first product
            doNothing().when(orderService).restoreProductStock(10L, 1);

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(1L)
                    .items(List.of(
                            new OrderItemRequest(10L, 1),
                            new OrderItemRequest(20L, 1)
                    ))
                    .build();

            assertThatThrownBy(() -> orderService.createOrder(request))
                    .isInstanceOf(ResourceNotFoundException.class);

            // Verify compensating transaction: stock was restored for product 10
            verify(orderService).restoreProductStock(10L, 1);
        }
    }

    @Nested
    @DisplayName("getOrderById")
    class GetOrderById {

        @Test
        @DisplayName("should return order when found")
        void shouldReturnOrderWhenFound() {
            Order order = Order.builder().id(1L).userId(1L).status(OrderStatus.PENDING)
                    .totalAmount(BigDecimal.valueOf(100)).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            Order result = orderService.getOrderById(1L);

            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw when order not found")
        void shouldThrowWhenNotFound() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found with id: 99");
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("should cancel order and restore stock")
        void shouldCancelOrderAndRestoreStock() {
            OrderItem item = OrderItem.builder()
                    .productId(10L).quantity(2)
                    .priceAtPurchase(BigDecimal.TEN).productNameSnapshot("Test").build();

            Order order = Order.builder().id(1L).userId(1L).status(OrderStatus.PENDING)
                    .totalAmount(BigDecimal.valueOf(20)).build();
            order.addItem(item);

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            doNothing().when(orderService).restoreProductStock(10L, 2);

            Order result = orderService.cancelOrder(1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            verify(orderService).restoreProductStock(10L, 2);
        }

        @Test
        @DisplayName("should throw when cancelling already cancelled order")
        void shouldThrowWhenAlreadyCancelled() {
            Order order = Order.builder().id(1L).status(OrderStatus.CANCELLED)
                    .totalAmount(BigDecimal.ZERO).build();

            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already cancelled");
        }
    }
}
