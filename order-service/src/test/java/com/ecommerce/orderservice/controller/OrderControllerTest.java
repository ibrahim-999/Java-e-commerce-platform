package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.ApiResponse;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController Unit Tests")
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @Nested
    @DisplayName("POST /api/orders/{id}/retry-payment — optimistic lock handling")
    class RetryPaymentOptimisticLock {

        @Test
        @DisplayName("should catch optimistic lock, reload order, and return success message when order is CONFIRMED")
        void shouldReturnSuccessMessageWhenReloadedOrderIsConfirmed() {
            // Arrange: retryPayment throws optimistic lock (Kafka consumer already confirmed the order)
            when(orderService.retryPayment(1L, "CREDIT_CARD"))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L));

            // The reloaded order is already CONFIRMED by the Kafka consumer
            Order confirmedOrder = buildOrder(1L, OrderStatus.CONFIRMED, 50L);
            when(orderService.getOrderById(1L)).thenReturn(confirmedOrder);

            // Act
            ResponseEntity<ApiResponse<OrderResponse>> response =
                    orderController.retryPayment(1L, "CREDIT_CARD");

            // Assert
            assertThat(response.getStatusCode().value()).isEqualTo(200);

            ApiResponse<OrderResponse> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.isSuccess()).isTrue();
            assertThat(body.getMessage()).isEqualTo("Payment retry successful — order confirmed");
            assertThat(body.getData().status()).isEqualTo("CONFIRMED");
            assertThat(body.getData().id()).isEqualTo(1L);

            verify(orderService).retryPayment(1L, "CREDIT_CARD");
            verify(orderService).getOrderById(1L);
        }

        @Test
        @DisplayName("should catch optimistic lock, reload order, and return failure message when order is still PAYMENT_FAILED")
        void shouldReturnFailureMessageWhenReloadedOrderIsPaymentFailed() {
            // Arrange: retryPayment throws optimistic lock
            when(orderService.retryPayment(1L, "PAYPAL"))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Order.class, 1L));

            // The reloaded order is still PAYMENT_FAILED (race condition but payment didn't succeed)
            Order failedOrder = buildOrder(1L, OrderStatus.PAYMENT_FAILED, null);
            when(orderService.getOrderById(1L)).thenReturn(failedOrder);

            // Act
            ResponseEntity<ApiResponse<OrderResponse>> response =
                    orderController.retryPayment(1L, "PAYPAL");

            // Assert
            assertThat(response.getStatusCode().value()).isEqualTo(200);

            ApiResponse<OrderResponse> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.isSuccess()).isTrue();
            assertThat(body.getMessage())
                    .isEqualTo("Payment retry failed — order marked as PAYMENT_FAILED, stock restored");
            assertThat(body.getData().status()).isEqualTo("PAYMENT_FAILED");
            assertThat(body.getData().id()).isEqualTo(1L);

            verify(orderService).retryPayment(1L, "PAYPAL");
            verify(orderService).getOrderById(1L);
        }
    }

    /**
     * Builds a minimal Order with one item, suitable for controller-level tests
     * where the controller only reads id, status, and converts via OrderResponse.fromEntity().
     */
    private Order buildOrder(Long id, OrderStatus status, Long paymentId) {
        Order order = Order.builder()
                .id(id)
                .userId(100L)
                .status(status)
                .paymentId(paymentId)
                .totalAmount(BigDecimal.valueOf(500))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        order.addItem(OrderItem.builder()
                .id(1L)
                .productId(10L)
                .quantity(1)
                .priceAtPurchase(BigDecimal.valueOf(500))
                .productNameSnapshot("Laptop")
                .build());
        return order;
    }
}
