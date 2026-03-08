package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.PriceStatsResponse;
import com.ecommerce.productservice.exception.DuplicateResourceException;
import com.ecommerce.productservice.exception.InsufficientStockException;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.model.ProductStatus;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    // ==================== CREATE ====================

    @Transactional
    public Product createProduct(Product product, Long categoryId) {
        // Business rule: no duplicate SKUs
        if (productRepository.existsBySku(product.getSku())) {
            throw new DuplicateResourceException("Product", "sku", product.getSku());
        }

        // Look up the category
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));

        product.setCategory(category);
        product.setStatus(ProductStatus.ACTIVE);

        return productRepository.save(product);
    }

    // ==================== READ ====================

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    @Transactional(readOnly = true)
    public Product getProductBySku(String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
    }

    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> getProductsByCategory(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> searchProducts(String name, Pageable pageable) {
        return productRepository.findByNameContainingIgnoreCase(name, pageable);
    }

    // ==================== UPDATE ====================

    @Transactional
    public Product updateProduct(Long id, Product updatedData, Long categoryId) {
        Product existing = getProductById(id);

        if (updatedData.getName() != null) {
            existing.setName(updatedData.getName());
        }
        if (updatedData.getDescription() != null) {
            existing.setDescription(updatedData.getDescription());
        }
        if (updatedData.getPrice() != null) {
            existing.setPrice(updatedData.getPrice());
        }
        if (updatedData.getStockQuantity() != null) {
            existing.setStockQuantity(updatedData.getStockQuantity());
            // Auto-update status based on stock
            if (updatedData.getStockQuantity() == 0) {
                existing.setStatus(ProductStatus.OUT_OF_STOCK);
            } else if (existing.getStatus() == ProductStatus.OUT_OF_STOCK) {
                existing.setStatus(ProductStatus.ACTIVE);
            }
        }
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
            existing.setCategory(category);
        }

        // Dirty checking — Hibernate auto-saves changes at transaction commit
        return existing;
    }

    // ==================== DELETE ====================

    @Transactional
    public void deleteProduct(Long id) {
        Product product = getProductById(id);
        productRepository.delete(product);
    }

    // ==================== STOCK MANAGEMENT (Atomic — Race-Condition Safe) ====================
    //
    // OLD approach (race condition!):
    //   Product p = findById(id);         // READ: stock = 1
    //   p.setStock(stock - quantity);     // WRITE: stock = 0
    //   // Another thread can read stock = 1 between these two lines!
    //
    // NEW approach (atomic):
    //   UPDATE products SET stock = stock - 5 WHERE id = 1 AND stock >= 5
    //   // The check and update happen in ONE indivisible database operation.
    //   // No other transaction can sneak in between.

    @Transactional
    public void reduceStock(Long productId, int quantity) {
        // Atomic SQL: returns 1 if stock was reduced, 0 if insufficient stock
        int rowsAffected = productRepository.reduceStock(productId, quantity);

        if (rowsAffected == 0) {
            // Either product doesn't exist or insufficient stock — check which
            Product product = getProductById(productId);  // throws if not found
            throw new InsufficientStockException(
                    product.getSku(), quantity, product.getStockQuantity());
        }

        // Update status if stock hit zero (separate from the atomic check)
        Product product = getProductById(productId);
        if (product.getStockQuantity() == 0) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        }
    }

    @Transactional
    public void restoreStock(Long productId, int quantity) {
        // Verify product exists
        getProductById(productId);

        // Atomic stock addition
        productRepository.restoreStock(productId, quantity);

        // Reactivate if was out of stock
        Product product = getProductById(productId);
        if (product.getStatus() == ProductStatus.OUT_OF_STOCK) {
            product.setStatus(ProductStatus.ACTIVE);
        }
    }

    // ==================== PRICE AGGREGATIONS ====================
    // All computation happens in PostgreSQL — Java receives only the final numbers.
    // Even with millions of products, the DB does one efficient scan.

    @Transactional(readOnly = true)
    public PriceStatsResponse getPriceStats() {
        BigDecimal avg = productRepository.getAveragePrice();
        BigDecimal min = productRepository.getMinPrice();
        BigDecimal max = productRepository.getMaxPrice();
        long count = productRepository.count();

        return new PriceStatsResponse(avg, min, max, count);
    }

    @Transactional(readOnly = true)
    public PriceStatsResponse getPriceStatsByCategory(Long categoryId) {
        // Verify category exists
        categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));

        BigDecimal avg = productRepository.getAveragePriceByCategory(categoryId);
        BigDecimal min = productRepository.getMinPriceByCategory(categoryId);
        BigDecimal max = productRepository.getMaxPriceByCategory(categoryId);
        long count = productRepository.countByCategoryId(categoryId);

        return new PriceStatsResponse(avg, min, max, count);
    }
}
