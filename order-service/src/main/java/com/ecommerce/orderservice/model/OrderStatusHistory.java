package com.ecommerce.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// OrderStatusHistory — an audit trail of every status change for an order.
//
// Example timeline:
//   PENDING   → "Order placed"                 (2024-03-08 10:00:00)
//   CONFIRMED → "Payment completed"            (2024-03-08 10:00:03)
//   SHIPPED   → "Shipped via FedEx #12345"     (2024-03-09 14:30:00)
//   DELIVERED → "Package delivered"             (2024-03-12 09:15:00)
//
// Why do we need this?
//   - Customer support: "When did my order ship?"
//   - Dispute resolution: "Was this order really delivered?"
//   - Analytics: "How long does it take from order to delivery?"
//   - Compliance: audit trail for financial transactions

@Entity
@Table(name = "order_status_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // What status the order moved FROM (null for the initial creation)
    @Enumerated(EnumType.STRING)
    private OrderStatus fromStatus;

    // What status the order moved TO
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus toStatus;

    // Human-readable reason for the change
    private String reason;

    @CreationTimestamp
    private LocalDateTime changedAt;
}
