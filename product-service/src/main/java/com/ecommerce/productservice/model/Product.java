package com.ecommerce.productservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Product — the core entity of the product-service.
//
// Key design decisions:
//   - price uses BigDecimal, NOT double/float. Floating-point math causes rounding errors
//     with money (e.g., 0.1 + 0.2 = 0.30000000000000004). BigDecimal is exact.
//   - sku (Stock Keeping Unit) is a unique identifier used in warehouses/inventory.
//   - stockQuantity tracks available inventory.
//   - @ManyToOne: many products belong to one category.
//     FetchType.LAZY means the category is NOT loaded until you access it.
//     This prevents unnecessary queries — if you only need the product name,
//     why load the entire category object?

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    // BigDecimal for money — never use double/float for financial calculations.
    // precision = total digits, scale = digits after decimal point.
    // (10,2) means up to 99,999,999.99
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    // SKU — unique product identifier for inventory management.
    @Column(nullable = false, unique = true)
    private String sku;

    // @ManyToOne: many products → one category.
    // FetchType.LAZY: don't load the category unless we explicitly access it.
    // @JoinColumn: the foreign key column in the products table.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    // @Version — Optimistic Locking.
    // Hibernate auto-increments this on every UPDATE.
    // If two transactions read version=1 and both try to update:
    //   Transaction A: UPDATE ... SET version=2 WHERE id=1 AND version=1 → succeeds
    //   Transaction B: UPDATE ... SET version=2 WHERE id=1 AND version=1 → 0 rows → exception
    // The second transaction gets OptimisticLockException and must retry.
    @Version
    private Long version;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
