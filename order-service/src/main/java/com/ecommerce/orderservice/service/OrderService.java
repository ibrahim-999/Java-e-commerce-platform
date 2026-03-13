package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.exception.ServiceUnavailableException;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.model.OrderStatusHistory;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.repository.OrderStatusHistoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// OrderService — the SAGA ORCHESTRATOR for order creation.
//
// This service implements the Orchestration Saga Pattern:
//   In a monolith, you'd use a database transaction to ensure consistency.
//   In microservices, each service has its own database — no shared transactions.
//   The Saga pattern solves this by breaking a distributed operation into steps,
//   where each step has a COMPENSATING TRANSACTION that undoes it on failure.
//
// Saga steps for order creation:
//   Step 1: Validate user exists           → Compensation: none (read-only)
//   Step 2: Reserve stock for each item    → Compensation: restoreProductStock()
//   Step 3: Create order (PENDING)         → Compensation: mark PAYMENT_FAILED
//   Step 4: Process payment                → Compensation: restore stock + mark PAYMENT_FAILED
//
// If payment fails after stock is reserved, the saga automatically:
//   1. Restores all reserved stock (compensating transaction)
//   2. Marks the order as PAYMENT_FAILED
//   3. Publishes events so notification-service can inform the user
//   4. The user can retry payment via POST /api/orders/{id}/retry-payment
//
// The saga also listens to Kafka payment-events for ASYNC compensation:
//   If the synchronous payment call timed out but payment-service processed it later,
//   the PaymentEventConsumer picks up the result and updates the order accordingly.
//
// @Slf4j — Lombok generates a `log` field. Equivalent to:
//   private static final Logger log = LoggerFactory.getLogger(OrderService.class);

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository statusHistoryRepository;
    private final WebClient userServiceClient;
    private final WebClient productServiceClient;
    private final WebClient paymentServiceClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // ==================== CREATE ORDER ====================

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        // Step 1: Validate the user exists
        validateUser(request.getUserId());

        // Step 2 & 3: Validate products, get prices, and reserve stock
        List<OrderItem> items = new ArrayList<>();
        List<Long> reservedProductIds = new ArrayList<>();  // track for rollback
        List<Integer> reservedQuantities = new ArrayList<>();

        try {
            for (OrderItemRequest itemRequest : request.getItems()) {
                // Fetch product details from product-service
                JsonNode product = getProduct(itemRequest.productId());

                // Snapshot the price and name at this moment
                BigDecimal price = new BigDecimal(product.get("data").get("price").asText());
                String productName = product.get("data").get("name").asText();

                // Reserve stock (atomic operation in product-service)
                reduceProductStock(itemRequest.productId(), itemRequest.quantity());
                reservedProductIds.add(itemRequest.productId());
                reservedQuantities.add(itemRequest.quantity());

                // Build the order item with snapshotted data
                OrderItem item = OrderItem.builder()
                        .productId(itemRequest.productId())
                        .quantity(itemRequest.quantity())
                        .priceAtPurchase(price)
                        .productNameSnapshot(productName)
                        .build();

                items.add(item);
            }
        } catch (Exception e) {
            // COMPENSATING TRANSACTION: if anything fails, restore all reserved stock
            log.error("Order creation failed, restoring stock for {} items", reservedProductIds.size());
            for (int i = 0; i < reservedProductIds.size(); i++) {
                try {
                    restoreProductStock(reservedProductIds.get(i), reservedQuantities.get(i));
                } catch (Exception rollbackEx) {
                    log.error("Failed to restore stock for product {}: {}",
                            reservedProductIds.get(i), rollbackEx.getMessage());
                }
            }
            throw e;  // re-throw the original exception
        }

        // Step 4: Calculate total and create the order
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .userId(request.getUserId())
                .status(OrderStatus.PENDING)
                .totalAmount(totalAmount)
                .build();

        // Add items with back-reference
        items.forEach(order::addItem);

        // Record the initial status in the audit trail
        order.changeStatus(OrderStatus.PENDING, "Order placed");

        order = orderRepository.save(order);

        // Step 5: Initiate payment via payment-service (SAGA STEP 4).
        //
        // This is where the Saga pattern shines. Three outcomes:
        //   A) Payment COMPLETED → order confirmed, everyone is happy
        //   B) Payment FAILED   → COMPENSATE: restore stock, mark PAYMENT_FAILED
        //   C) Service DOWN     → COMPENSATE: restore stock, mark PAYMENT_FAILED
        //
        // In case B/C, the user can retry via POST /api/orders/{id}/retry-payment.
        // The Kafka PaymentEventConsumer also handles async results if the payment
        // was actually processed but the synchronous call timed out.
        try {
            JsonNode paymentResult = initiatePayment(
                    order.getId(), request.getUserId(), totalAmount, request.getPaymentMethod());

            Long paymentId = paymentResult.get("data").get("id").asLong();
            String paymentStatus = paymentResult.get("data").get("status").asText();

            order.setPaymentId(paymentId);

            if ("COMPLETED".equals(paymentStatus)) {
                order.changeStatus(OrderStatus.CONFIRMED, "Payment completed, paymentId: " + paymentId);
                log.info("Saga SUCCESS: Order {} confirmed, paymentId: {}", order.getId(), paymentId);
            } else {
                // Payment returned but not COMPLETED (e.g., FAILED after retries)
                // SAGA COMPENSATION: restore stock and mark payment failed
                log.warn("Saga COMPENSATING: Payment {} for order {}", paymentStatus, order.getId());
                restoreStockForOrder(order);
                order.changeStatus(OrderStatus.PAYMENT_FAILED,
                        "Payment " + paymentStatus + ", stock restored. Retry with POST /api/orders/"
                                + order.getId() + "/retry-payment");
            }

            order = orderRepository.save(order);
        } catch (Exception e) {
            // Payment service unavailable or threw an error
            // SAGA COMPENSATION: restore stock and mark payment failed
            log.error("Saga COMPENSATING: Payment failed for order {}: {}", order.getId(), e.getMessage());
            restoreStockForOrder(order);
            order.changeStatus(OrderStatus.PAYMENT_FAILED,
                    "Payment service error: " + e.getMessage() + ". Stock restored.");
            order = orderRepository.save(order);
        }

        // Publish OrderPlacedEvent to Kafka — notification-service will pick it up
        publishOrderPlacedEvent(order);

        return order;
    }

    private void publishOrderPlacedEvent(Order order) {
        try {
            Map<String, Object> event = Map.of(
                    "orderId", order.getId(),
                    "userId", order.getUserId(),
                    "totalAmount", order.getTotalAmount(),
                    "status", order.getStatus().name(),
                    "itemCount", order.getItems().size()
            );
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("order-events", "order-" + order.getId(), eventJson);
            log.info("Published OrderPlacedEvent for order {} to Kafka", order.getId());
        } catch (Exception e) {
            log.error("Failed to publish OrderPlacedEvent for order {}: {}", order.getId(), e.getMessage());
        }
    }

    // ==================== READ ====================

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }

    @Transactional(readOnly = true)
    public Page<Order> getOrdersByUserId(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable);
    }

    // Get the status change history for an order (audit trail)
    @Transactional(readOnly = true)
    public List<OrderStatusHistory> getOrderHistory(Long orderId) {
        getOrderById(orderId); // verify order exists
        return statusHistoryRepository.findByOrderIdOrderByChangedAtAsc(orderId);
    }

    // ==================== RETRY PAYMENT (Saga Recovery) ====================
    //
    // When payment fails, the order is marked PAYMENT_FAILED and stock is restored.
    // This endpoint lets the user try again: re-reserve stock, re-initiate payment.
    // If it fails again, stock is restored again — the saga is fully idempotent.

    @Transactional
    public Order retryPayment(Long orderId, String paymentMethod) {
        Order order = getOrderById(orderId);

        if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            throw new IllegalArgumentException(
                    "Can only retry payment for orders with PAYMENT_FAILED status. Current: " + order.getStatus());
        }

        // Re-reserve stock (it was restored when payment failed)
        log.info("Saga RETRY: Re-reserving stock for order {}", orderId);
        List<OrderItem> items = order.getItems();
        List<Long> reservedProductIds = new ArrayList<>();
        List<Integer> reservedQuantities = new ArrayList<>();

        try {
            for (OrderItem item : items) {
                reduceProductStock(item.getProductId(), item.getQuantity());
                reservedProductIds.add(item.getProductId());
                reservedQuantities.add(item.getQuantity());
            }
        } catch (Exception e) {
            // Stock reservation failed — restore any partially reserved items
            log.error("Saga RETRY failed at stock reservation for order {}: {}", orderId, e.getMessage());
            for (int i = 0; i < reservedProductIds.size(); i++) {
                try {
                    restoreProductStock(reservedProductIds.get(i), reservedQuantities.get(i));
                } catch (Exception rollbackEx) {
                    log.error("Failed to restore stock for product {}: {}",
                            reservedProductIds.get(i), rollbackEx.getMessage());
                }
            }
            throw e;
        }

        // Re-attempt payment
        order.changeStatus(OrderStatus.PENDING, "Payment retry initiated");

        try {
            JsonNode paymentResult = initiatePayment(
                    orderId, order.getUserId(), order.getTotalAmount(), paymentMethod);

            Long paymentId = paymentResult.get("data").get("id").asLong();
            String paymentStatus = paymentResult.get("data").get("status").asText();

            order.setPaymentId(paymentId);

            if ("COMPLETED".equals(paymentStatus)) {
                order.changeStatus(OrderStatus.CONFIRMED, "Payment completed on retry, paymentId: " + paymentId);
                log.info("Saga RETRY SUCCESS: Order {} confirmed, paymentId: {}", orderId, paymentId);
            } else {
                log.warn("Saga RETRY COMPENSATING: Payment {} for order {}", paymentStatus, orderId);
                restoreStockForOrder(order);
                order.changeStatus(OrderStatus.PAYMENT_FAILED,
                        "Payment " + paymentStatus + " on retry, stock restored");
            }
        } catch (Exception e) {
            log.error("Saga RETRY COMPENSATING: Payment failed for order {}: {}", orderId, e.getMessage());
            restoreStockForOrder(order);
            order.changeStatus(OrderStatus.PAYMENT_FAILED,
                    "Payment retry failed: " + e.getMessage() + ". Stock restored.");
        }

        publishOrderPlacedEvent(order);
        return orderRepository.save(order);
    }

    // Helper: restore stock for all items in an order (saga compensation step)
    private void restoreStockForOrder(Order order) {
        for (OrderItem item : order.getItems()) {
            try {
                restoreProductStock(item.getProductId(), item.getQuantity());
                log.info("Saga COMPENSATION: Restored {} units of product {}",
                        item.getQuantity(), item.getProductId());
            } catch (Exception e) {
                log.error("CRITICAL: Failed to restore stock for product {} during saga compensation: {}",
                        item.getProductId(), e.getMessage());
            }
        }
    }

    // ==================== CANCEL ====================

    @Transactional
    public Order cancelOrder(Long id) {
        Order order = getOrderById(id);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Order is already cancelled");
        }
        if (order.getStatus() == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Cannot cancel a delivered order");
        }

        // PAYMENT_FAILED orders already had their stock restored — no need to restore again.
        // Only restore stock for PENDING, CONFIRMED, or SHIPPED orders.
        if (order.getStatus() != OrderStatus.PAYMENT_FAILED) {
            restoreStockForOrder(order);
        }

        order.changeStatus(OrderStatus.CANCELLED, "Order cancelled by user");
        return order;
    }

    // ==================== INTER-SERVICE COMMUNICATION ====================
    //
    // @CircuitBreaker — Resilience4j annotation.
    //
    // Circuit Breaker has 3 states:
    //   CLOSED   — everything is fine, calls go through normally
    //   OPEN     — too many failures, calls are blocked immediately (returns fallback)
    //   HALF_OPEN — after waiting, a few test calls are allowed through
    //
    // Think of it like an electrical circuit breaker in your house:
    //   - If too many appliances draw too much current (= too many failures),
    //     the breaker trips (OPEN) to prevent a fire (= prevent cascading failures)
    //   - After some time, you flip it back on (HALF_OPEN) to test if the problem is fixed

    @CircuitBreaker(name = "userService", fallbackMethod = "validateUserFallback")
    public void validateUser(Long userId) {
        log.info("Validating user {} with user-service", userId);

        // .block() converts the async WebClient call to synchronous
        // In a fully reactive app, you'd chain the operations without blocking
        userServiceClient.get()
                .uri("/api/users/{id}", userId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    // Fallback method — called when the circuit is OPEN or the call fails.
    // The method signature must match the original + an extra Throwable parameter.
    public void validateUserFallback(Long userId, Throwable throwable) {
        log.error("User-service is unavailable. Cannot validate user {}: {}", userId, throwable.getMessage());

        // If it's a 404 (user not found), propagate that — it's a valid business error
        if (throwable instanceof WebClientResponseException.NotFound) {
            throw new ResourceNotFoundException("User", "id", userId);
        }
        // For any other failure (service down, timeout, etc.), throw ServiceUnavailableException
        throw new ServiceUnavailableException("User service");
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    public JsonNode getProduct(Long productId) {
        log.info("Fetching product {} from product-service", productId);

        return productServiceClient.get()
                .uri("/api/products/{id}", productId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    public JsonNode getProductFallback(Long productId, Throwable throwable) {
        log.error("Product-service is unavailable. Cannot fetch product {}: {}", productId, throwable.getMessage());

        if (throwable instanceof WebClientResponseException.NotFound) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
        throw new ServiceUnavailableException("Product service");
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "reduceProductStockFallback")
    public void reduceProductStock(Long productId, int quantity) {
        log.info("Reducing stock for product {} by {}", productId, quantity);

        productServiceClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/products/{id}/stock/reduce")
                        .queryParam("quantity", quantity)
                        .build(productId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    public void reduceProductStockFallback(Long productId, int quantity, Throwable throwable) {
        log.error("Failed to reduce stock for product {}: {}", productId, throwable.getMessage());

        if (throwable instanceof WebClientResponseException.BadRequest) {
            throw new IllegalArgumentException("Insufficient stock for product " + productId);
        }
        throw new ServiceUnavailableException("Product service");
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "restoreProductStockFallback")
    public void restoreProductStock(Long productId, int quantity) {
        log.info("Restoring stock for product {} by {}", productId, quantity);

        productServiceClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/products/{id}/stock/restore")
                        .queryParam("quantity", quantity)
                        .build(productId))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    public void restoreProductStockFallback(Long productId, int quantity, Throwable throwable) {
        log.error("CRITICAL: Failed to restore stock for product {} by {}. Manual intervention needed: {}",
                productId, quantity, throwable.getMessage());
        // Don't throw here — we're already in a compensating transaction
        // Log for manual resolution by operations team
    }

    // ==================== PAYMENT SERVICE ====================

    @CircuitBreaker(name = "paymentService", fallbackMethod = "initiatePaymentFallback")
    public JsonNode initiatePayment(Long orderId, Long userId, BigDecimal amount, String paymentMethod) {
        log.info("Initiating payment for order {}, amount: {}, method: {}", orderId, amount, paymentMethod);

        // Build the request body matching payment-service's CreatePaymentRequest
        var paymentRequest = java.util.Map.of(
                "orderId", orderId,
                "userId", userId,
                "amount", amount,
                "paymentMethod", paymentMethod
        );

        return paymentServiceClient.post()
                .uri("/api/payments")
                .bodyValue(paymentRequest)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    public JsonNode initiatePaymentFallback(Long orderId, Long userId, BigDecimal amount,
                                            String paymentMethod, Throwable throwable) {
        log.error("Payment service unavailable for order {}: {}", orderId, throwable.getMessage());
        throw new ServiceUnavailableException("Payment service");
    }
}
