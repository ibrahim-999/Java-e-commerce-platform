package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.BaseIntegrationTest;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.exception.ServiceUnavailableException;
import com.ecommerce.orderservice.factory.OrderFactory;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.model.OrderStatusHistory;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("OrderController Integration Tests")
class OrderControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        // Build a fully populated test Order with items and status history
        testOrder = Order.builder()
                .id(1L)
                .userId(100L)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(1999.98))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .productId(10L)
                .quantity(2)
                .priceAtPurchase(BigDecimal.valueOf(999.99))
                .productNameSnapshot("iPhone 16")
                .build();
        testOrder.addItem(item);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .id(1L)
                .order(testOrder)
                .fromStatus(null)
                .toStatus(OrderStatus.PENDING)
                .reason("Order placed")
                .changedAt(LocalDateTime.now())
                .build();
        testOrder.getStatusHistory().add(history);
    }

    @Nested
    @DisplayName("POST /api/orders")
    class CreateOrder {

        @Test
        @DisplayName("should create order successfully and return 201")
        void shouldCreateOrderSuccessfully() throws Exception {
            when(orderService.createOrder(any(CreateOrderRequest.class))).thenReturn(testOrder);

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(100L)
                    .items(List.of(new OrderItemRequest(10L, 2)))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Order created successfully"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.userId").value(100))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.totalAmount").value(1999.98))
                    .andExpect(jsonPath("$.data.items").isArray())
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].productId").value(10))
                    .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                    .andExpect(jsonPath("$.data.items[0].productName").value("iPhone 16"))
                    .andExpect(jsonPath("$.data.items[0].priceAtPurchase").value(999.99));

            verify(orderService).createOrder(any(CreateOrderRequest.class));
        }

        @Test
        @DisplayName("should return 400 when userId is null")
        void shouldReturn400WhenUserIdIsNull() throws Exception {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(null)
                    .items(List.of(new OrderItemRequest(10L, 2)))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation failed"));

            verify(orderService, never()).createOrder(any());
        }

        @Test
        @DisplayName("should return 400 when items list is empty")
        void shouldReturn400WhenItemsEmpty() throws Exception {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(100L)
                    .items(List.of())
                    .paymentMethod("CREDIT_CARD")
                    .build();

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation failed"));

            verify(orderService, never()).createOrder(any());
        }

        @Test
        @DisplayName("should return 400 when items list is null")
        void shouldReturn400WhenItemsNull() throws Exception {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(100L)
                    .items(null)
                    .paymentMethod("CREDIT_CARD")
                    .build();

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            verify(orderService, never()).createOrder(any());
        }

        @Test
        @DisplayName("should return 400 when paymentMethod is blank")
        void shouldReturn400WhenPaymentMethodBlank() throws Exception {
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(100L)
                    .items(List.of(new OrderItemRequest(10L, 2)))
                    .paymentMethod("")
                    .build();

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Validation failed"));

            verify(orderService, never()).createOrder(any());
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{id}")
    class GetOrderById {

        @Test
        @DisplayName("should return order when found")
        void shouldReturnOrderWhenFound() throws Exception {
            when(orderService.getOrderById(1L)).thenReturn(testOrder);

            mockMvc.perform(get("/api/orders/{id}", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.userId").value(100))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.totalAmount").value(1999.98))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].productName").value("iPhone 16"));

            verify(orderService).getOrderById(1L);
        }

        @Test
        @DisplayName("should return 404 when order not found")
        void shouldReturn404WhenOrderNotFound() throws Exception {
            when(orderService.getOrderById(99L))
                    .thenThrow(new ResourceNotFoundException("Order", "id", 99L));

            mockMvc.perform(get("/api/orders/{id}", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Order not found with id: 99"));

            verify(orderService).getOrderById(99L);
        }
    }

    @Nested
    @DisplayName("GET /api/orders/user/{userId}")
    class GetOrdersByUser {

        @Test
        @DisplayName("should return paginated orders for user")
        void shouldReturnPaginatedOrdersForUser() throws Exception {
            List<Order> orders = List.of(testOrder);
            Page<Order> orderPage = new PageImpl<>(orders, PageRequest.of(0, 10), 1);

            when(orderService.getOrdersByUserId(eq(100L), any(Pageable.class))).thenReturn(orderPage);

            mockMvc.perform(get("/api/orders/user/{userId}", 100L)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.content[0].userId").value(100))
                    .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));

            verify(orderService).getOrdersByUserId(eq(100L), any(Pageable.class));
        }

        @Test
        @DisplayName("should return empty page when user has no orders")
        void shouldReturnEmptyPageWhenNoOrders() throws Exception {
            Page<Order> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);

            when(orderService.getOrdersByUserId(eq(999L), any(Pageable.class))).thenReturn(emptyPage);

            mockMvc.perform(get("/api/orders/user/{userId}", 999L)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));

            verify(orderService).getOrdersByUserId(eq(999L), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{id}/history")
    class GetOrderHistory {

        @Test
        @DisplayName("should return order status history")
        void shouldReturnOrderStatusHistory() throws Exception {
            OrderStatusHistory history1 = OrderStatusHistory.builder()
                    .id(1L)
                    .order(testOrder)
                    .fromStatus(null)
                    .toStatus(OrderStatus.PENDING)
                    .reason("Order placed")
                    .changedAt(LocalDateTime.now())
                    .build();

            OrderStatusHistory history2 = OrderStatusHistory.builder()
                    .id(2L)
                    .order(testOrder)
                    .fromStatus(OrderStatus.PENDING)
                    .toStatus(OrderStatus.CONFIRMED)
                    .reason("Payment completed")
                    .changedAt(LocalDateTime.now())
                    .build();

            when(orderService.getOrderHistory(1L)).thenReturn(List.of(history1, history2));

            mockMvc.perform(get("/api/orders/{id}/history", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].fromStatus").doesNotExist())
                    .andExpect(jsonPath("$.data[0].toStatus").value("PENDING"))
                    .andExpect(jsonPath("$.data[0].reason").value("Order placed"))
                    .andExpect(jsonPath("$.data[1].fromStatus").value("PENDING"))
                    .andExpect(jsonPath("$.data[1].toStatus").value("CONFIRMED"))
                    .andExpect(jsonPath("$.data[1].reason").value("Payment completed"));

            verify(orderService).getOrderHistory(1L);
        }

        @Test
        @DisplayName("should return 404 when order not found for history")
        void shouldReturn404WhenOrderNotFoundForHistory() throws Exception {
            when(orderService.getOrderHistory(99L))
                    .thenThrow(new ResourceNotFoundException("Order", "id", 99L));

            mockMvc.perform(get("/api/orders/{id}/history", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Order not found with id: 99"));

            verify(orderService).getOrderHistory(99L);
        }
    }

    @Nested
    @DisplayName("PUT /api/orders/{id}/cancel")
    class CancelOrder {

        @Test
        @DisplayName("should cancel order successfully")
        void shouldCancelOrderSuccessfully() throws Exception {
            Order cancelledOrder = Order.builder()
                    .id(1L)
                    .userId(100L)
                    .status(OrderStatus.CANCELLED)
                    .totalAmount(BigDecimal.valueOf(1999.98))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            OrderItem item = OrderItem.builder()
                    .id(1L)
                    .productId(10L)
                    .quantity(2)
                    .priceAtPurchase(BigDecimal.valueOf(999.99))
                    .productNameSnapshot("iPhone 16")
                    .build();
            cancelledOrder.addItem(item);

            when(orderService.cancelOrder(1L)).thenReturn(cancelledOrder);

            mockMvc.perform(put("/api/orders/{id}/cancel", 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Order cancelled successfully"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));

            verify(orderService).cancelOrder(1L);
        }

        @Test
        @DisplayName("should return 404 when cancelling non-existent order")
        void shouldReturn404WhenCancellingNonExistentOrder() throws Exception {
            when(orderService.cancelOrder(99L))
                    .thenThrow(new ResourceNotFoundException("Order", "id", 99L));

            mockMvc.perform(put("/api/orders/{id}/cancel", 99L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Order not found with id: 99"));

            verify(orderService).cancelOrder(99L);
        }

        @Test
        @DisplayName("should return 400 when cancelling already cancelled order")
        void shouldReturn400WhenCancellingAlreadyCancelledOrder() throws Exception {
            when(orderService.cancelOrder(1L))
                    .thenThrow(new IllegalArgumentException("Order is already cancelled"));

            mockMvc.perform(put("/api/orders/{id}/cancel", 1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Order is already cancelled"));

            verify(orderService).cancelOrder(1L);
        }

        @Test
        @DisplayName("should return 400 when cancelling delivered order")
        void shouldReturn400WhenCancellingDeliveredOrder() throws Exception {
            when(orderService.cancelOrder(1L))
                    .thenThrow(new IllegalArgumentException("Cannot cancel a delivered order"));

            mockMvc.perform(put("/api/orders/{id}/cancel", 1L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Cannot cancel a delivered order"));

            verify(orderService).cancelOrder(1L);
        }
    }

    @Nested
    @DisplayName("Exception Handler Coverage")
    class ExceptionHandlerCoverage {

        @Test
        @DisplayName("should return 503 when service is unavailable")
        void shouldReturn503WhenServiceUnavailable() throws Exception {
            when(orderService.createOrder(any(CreateOrderRequest.class)))
                    .thenThrow(new ServiceUnavailableException("Product service"));

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .userId(100L)
                    .items(List.of(new OrderItemRequest(10L, 2)))
                    .paymentMethod("CREDIT_CARD")
                    .build();

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Product service is currently unavailable. Please try again later."));
        }

        @Test
        @DisplayName("should return 409 on optimistic lock exception")
        void shouldReturn409OnOptimisticLockException() throws Exception {
            when(orderService.cancelOrder(1L))
                    .thenThrow(new jakarta.persistence.OptimisticLockException("Concurrent modification"));

            mockMvc.perform(put("/api/orders/{id}/cancel", 1L))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("This order was modified by another request. Please retry."));
        }
    }
}
