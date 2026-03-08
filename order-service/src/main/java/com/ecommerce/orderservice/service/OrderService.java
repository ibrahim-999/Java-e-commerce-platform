package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.exception.ResourceNotFoundException;
import com.ecommerce.orderservice.exception.ServiceUnavailableException;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// OrderService — the most complex service because it orchestrates multiple microservices.
//
// When a user places an order:
//   1. Validate the user exists (call user-service)
//   2. For each item: validate the product exists, get its price (call product-service)
//   3. For each item: reduce stock (call product-service)
//   4. Create the order with snapshotted prices
//
// If any step fails, we need to restore stock for items already reserved.
// This is called a "compensating transaction" — the microservices equivalent of a rollback.
//
// @Slf4j — Lombok generates a `log` field. Equivalent to:
//   private static final Logger log = LoggerFactory.getLogger(OrderService.class);

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient userServiceClient;
    private final WebClient productServiceClient;

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

        return orderRepository.save(order);
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

        // Restore stock for each item
        for (OrderItem item : order.getItems()) {
            try {
                restoreProductStock(item.getProductId(), item.getQuantity());
            } catch (Exception e) {
                log.error("Failed to restore stock for product {} during cancellation: {}",
                        item.getProductId(), e.getMessage());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
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
}
