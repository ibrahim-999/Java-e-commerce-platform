package com.ecommerce.productservice.dto;

import com.ecommerce.productservice.model.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Record — immutable response DTO (same pattern as user-service)
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        String sku,
        String categoryName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getSku(),
                product.getCategory() != null ? product.getCategory().getName() : null,
                product.getStatus().name(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
