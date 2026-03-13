package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.dto.ApiResponse;
import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderResponse;
import com.ecommerce.orderservice.dto.OrderStatusHistoryResponse;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    // POST /api/orders — create a new order
    // This triggers calls to user-service (validate user) and product-service (get prices, reduce stock)
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        Order order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order created successfully", OrderResponse.fromEntity(order)));
    }

    // GET /api/orders/{id} — get order by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderById(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        return ResponseEntity.ok(ApiResponse.success(OrderResponse.fromEntity(order)));
    }

    // GET /api/orders/user/{userId} — get all orders for a user
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<OrderResponse>> getOrdersByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderResponse> orders = orderService.getOrdersByUserId(userId, pageable)
                .map(OrderResponse::fromEntity);

        return ResponseEntity.ok(orders);
    }

    // GET /api/orders/{id}/history — get status change history (audit trail)
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> getOrderHistory(
            @PathVariable Long id) {

        List<OrderStatusHistoryResponse> history = orderService.getOrderHistory(id).stream()
                .map(OrderStatusHistoryResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    // POST /api/orders/{id}/retry-payment — retry payment for a PAYMENT_FAILED order.
    // Part of the Saga pattern: when payment fails, stock is restored and the order
    // is marked PAYMENT_FAILED. This endpoint re-reserves stock and retries payment.
    //
    // Race condition handled: the Kafka PaymentEventConsumer might confirm the order
    // before this method's save completes, causing an optimistic lock exception.
    // In that case, reload the order — it's already in the correct state.
    @PostMapping("/{id}/retry-payment")
    public ResponseEntity<ApiResponse<OrderResponse>> retryPayment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "CREDIT_CARD") String paymentMethod) {

        Order order;
        try {
            order = orderService.retryPayment(id, paymentMethod);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // The Kafka consumer already confirmed this order — reload the current state
            log.info("Optimistic lock on order {} during retry — Kafka consumer already updated it", id);
            order = orderService.getOrderById(id);
        }
        String message = order.getStatus() == OrderStatus.CONFIRMED
                ? "Payment retry successful — order confirmed"
                : "Payment retry failed — order marked as PAYMENT_FAILED, stock restored";
        return ResponseEntity.ok(ApiResponse.success(message, OrderResponse.fromEntity(order)));
    }

    // PUT /api/orders/{id}/cancel — cancel an order (restores stock)
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long id) {
        Order order = orderService.cancelOrder(id);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", OrderResponse.fromEntity(order)));
    }
}
