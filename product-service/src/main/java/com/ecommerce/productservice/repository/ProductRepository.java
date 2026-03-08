package com.ecommerce.productservice.repository;

import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.model.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // ==================== ATOMIC STOCK OPERATIONS ====================
    // These solve race conditions by doing the check and update in ONE atomic SQL statement.
    // No read-then-write = no race condition.

    // Atomically reduce stock.
    // The WHERE clause ensures stock >= quantity — if not, 0 rows are affected.
    // @Modifying tells Spring this is an UPDATE/DELETE, not a SELECT.
    // Returns the number of rows affected: 1 = success, 0 = insufficient stock.
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity " +
           "WHERE p.id = :id AND p.stockQuantity >= :quantity")
    int reduceStock(@Param("id") Long id, @Param("quantity") int quantity);

    // Atomically restore stock (e.g., when an order is cancelled).
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity WHERE p.id = :id")
    int restoreStock(@Param("id") Long id, @Param("quantity") int quantity);

    // ==================== AGGREGATION QUERIES ====================
    // Let the database compute aggregations — never load millions of rows into Java.
    // These queries return a single number, not a list of products.

    // Average price of all products
    @Query("SELECT AVG(p.price) FROM Product p")
    BigDecimal getAveragePrice();

    // Average price within a specific category
    @Query("SELECT AVG(p.price) FROM Product p WHERE p.category.id = :categoryId")
    BigDecimal getAveragePriceByCategory(@Param("categoryId") Long categoryId);

    // Minimum price of all products
    @Query("SELECT MIN(p.price) FROM Product p")
    BigDecimal getMinPrice();

    // Maximum price of all products
    @Query("SELECT MAX(p.price) FROM Product p")
    BigDecimal getMaxPrice();

    // Count products by status
    long countByStatus(ProductStatus status);

    // Minimum price within a category
    @Query("SELECT MIN(p.price) FROM Product p WHERE p.category.id = :categoryId")
    BigDecimal getMinPriceByCategory(@Param("categoryId") Long categoryId);

    // Maximum price within a category
    @Query("SELECT MAX(p.price) FROM Product p WHERE p.category.id = :categoryId")
    BigDecimal getMaxPriceByCategory(@Param("categoryId") Long categoryId);

    // Count products in a category
    long countByCategoryId(Long categoryId);
}
