package com.ecommerce.orderservice.factory;

import com.ecommerce.orderservice.dto.CreateOrderRequest;
import com.ecommerce.orderservice.dto.OrderItemRequest;
import com.ecommerce.orderservice.model.Order;
import com.ecommerce.orderservice.model.OrderItem;
import com.ecommerce.orderservice.model.OrderStatus;
import com.ecommerce.orderservice.model.OrderStatusHistory;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Test factory for Order-related entities and DTOs.
// Generates realistic test data to keep tests clean and readable.
//
// Usage in tests:
//   Order order = OrderFactory.createOrder();
//   Order order = OrderFactory.createOrderWithId(1L);
//   CreateOrderRequest request = OrderFactory.createOrderRequest();
//   OrderItem item = OrderFactory.createOrderItem();

public class OrderFactory {

    private static final Faker faker = new Faker();

    // Creates an Order entity with one item and PENDING status
    public static Order createOrder() {
        Order order = Order.builder()
                .userId(faker.number().numberBetween(1L, 1000L))
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(999.99))
                .build();

        OrderItem item = createOrderItem();
        order.addItem(item);

        // Record initial status in audit trail
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .fromStatus(null)
                .toStatus(OrderStatus.PENDING)
                .reason("Order placed")
                .changedAt(LocalDateTime.now())
                .build();
        order.getStatusHistory().add(history);

        return order;
    }

    // Creates an Order entity with a specific ID
    public static Order createOrderWithId(Long id) {
        Order order = createOrder();
        order.setId(id);
        return order;
    }

    // Creates a CreateOrderRequest DTO for controller/integration tests
    public static CreateOrderRequest createOrderRequest() {
        return CreateOrderRequest.builder()
                .userId(faker.number().numberBetween(1L, 1000L))
                .items(List.of(
                        new OrderItemRequest(
                                faker.number().numberBetween(1L, 100L),
                                faker.number().numberBetween(1, 5)
                        )
                ))
                .paymentMethod("CREDIT_CARD")
                .build();
    }

    // Creates an OrderItem entity with realistic product data
    public static OrderItem createOrderItem() {
        return OrderItem.builder()
                .productId(faker.number().numberBetween(1L, 100L))
                .quantity(faker.number().numberBetween(1, 5))
                .priceAtPurchase(BigDecimal.valueOf(faker.number().randomDouble(2, 10, 999)))
                .productNameSnapshot(faker.commerce().productName())
                .build();
    }

    // Creates an OrderItem with a specific product ID and quantity
    public static OrderItem createOrderItem(Long productId, int quantity, BigDecimal price, String productName) {
        return OrderItem.builder()
                .productId(productId)
                .quantity(quantity)
                .priceAtPurchase(price)
                .productNameSnapshot(productName)
                .build();
    }

    // Creates an Order with multiple items
    public static Order createOrderWithItems(int itemCount) {
        Order order = Order.builder()
                .userId(faker.number().numberBetween(1L, 1000L))
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < itemCount; i++) {
            OrderItem item = createOrderItem();
            order.addItem(item);
            total = total.add(item.getSubtotal());
        }
        order.setTotalAmount(total);

        return order;
    }
}
