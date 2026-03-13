package com.ecommerce.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;

// OrderItem — one line in an order (e.g., "2x iPhone at $999 each").
//
// CRITICAL DESIGN: priceAtPurchase and productNameSnapshot.
// These are SNAPSHOTS — frozen copies of the product data at the time of purchase.
//
// Why? Because product prices and names change over time.
// If a customer orders an iPhone at $999, and Apple raises the price to $1099 next week,
// the customer's order should still show $999. If we only stored the product ID
// and looked up the current price, we'd show $1099 — which is wrong.
//
// This is called "price snapshotting" — every major e-commerce platform does this.

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Back-reference to the order
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // References a product in product-service — validated via HTTP call
    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    // SNAPSHOT: the price at the time of purchase — never changes after order creation.
    // Even if the product price changes tomorrow, this stays the same.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtPurchase;

    // SNAPSHOT: the product name at the time of purchase.
    // If the product is renamed later, the customer still sees what they ordered.
    @Column(nullable = false)
    private String productNameSnapshot;

    // Convenience: total for this line item (price × quantity)
    public BigDecimal getSubtotal() {
        return priceAtPurchase.multiply(BigDecimal.valueOf(quantity));
    }
}
