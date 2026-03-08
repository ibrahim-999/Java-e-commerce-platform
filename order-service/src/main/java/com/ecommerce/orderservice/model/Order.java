package com.ecommerce.orderservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Order entity — the core of the order-service.
//
// Key design decision: userId is a plain Long, NOT a JPA relationship.
// Why? Because the User entity lives in a DIFFERENT database (users_db).
// JPA relationships only work within the same database.
// In microservices, cross-service data is referenced by ID and validated via HTTP.

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // References a user in user-service — validated via HTTP call, not JPA
    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // Total amount — sum of all (priceAtPurchase * quantity) for each item
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // Cross-service reference to the payment in payment-service.
    // Nullable because the payment happens AFTER order creation.
    private Long paymentId;

    // @OneToMany: one order has many order items.
    // CascadeType.ALL: when we save/delete an order, its items are saved/deleted too.
    // orphanRemoval: if we remove an item from the list, it's deleted from the DB.
    // mappedBy: the OrderItem entity has the "order" field that owns the relationship.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    // Audit trail — every status change is recorded
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @Version
    private Long version;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Helper method: add an item and set the back-reference
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    // Helper: record a status change in the audit trail
    public void changeStatus(OrderStatus newStatus, String reason) {
        OrderStatus oldStatus = this.status;
        this.status = newStatus;

        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(this)
                .fromStatus(oldStatus)
                .toStatus(newStatus)
                .reason(reason)
                .build();
        statusHistory.add(history);
    }
}
