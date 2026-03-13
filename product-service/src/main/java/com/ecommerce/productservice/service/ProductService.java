package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.PriceStatsResponse;
import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.exception.DuplicateResourceException;
import com.ecommerce.productservice.exception.InsufficientStockException;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.model.ProductStatus;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
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

    // @Cacheable — "Before running this method, check if the result is already in Redis."
    //
    // How it works:
    //   1. Method called with id=5
    //   2. Spring builds cache key: "products::5"
    //   3. Redis: GET products::5
    //   4. If found → return cached ProductResponse, method body NEVER executes
    //   5. If not found → execute method, store result in Redis, then return
    //
    // value = "products" → the cache name (a logical group of cached entries)
    // key = "#id"        → use the method parameter "id" as the cache key
    //
    // We cache the ProductResponse (DTO), not the Product entity, because:
    //   - Entities have lazy-loaded relationships (Hibernate proxies) that can't be serialized
    //   - DTOs are simple, flat objects — perfect for JSON serialization into Redis
    @Cacheable(value = "products", key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        log.info("Cache MISS — fetching product {} from database", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
        return ProductResponse.fromEntity(product);
    }

    // Internal method for write operations that need the actual entity (not the DTO).
    // This is NOT cached — it always hits the database to get fresh data for modifications.
    @Transactional(readOnly = true)
    public Product getProductEntityById(Long id) {
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

    // @CacheEvict — "After this method runs, REMOVE the cached entry for this product."
    //
    // Why evict instead of update?
    //   - Simpler and safer — no risk of cache/DB mismatch
    //   - The next read will fetch fresh data from DB and re-cache it
    //   - "Cache invalidation" is one of the hardest problems in CS — keep it simple
    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public Product updateProduct(Long id, Product updatedData, Long categoryId) {
        log.info("Evicting product {} from cache (update)", id);
        Product existing = getProductEntityById(id);

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

    @CacheEvict(value = "products", key = "#id")
    @Transactional
    public void deleteProduct(Long id) {
        log.info("Evicting product {} from cache (delete)", id);
        Product product = getProductEntityById(id);
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

    @CacheEvict(value = "products", key = "#productId")
    @Transactional
    public void reduceStock(Long productId, int quantity) {
        log.info("Evicting product {} from cache (stock reduce)", productId);
        // Atomic SQL: returns 1 if stock was reduced, 0 if insufficient stock
        int rowsAffected = productRepository.reduceStock(productId, quantity);

        if (rowsAffected == 0) {
            // Either product doesn't exist or insufficient stock — check which
            Product product = getProductEntityById(productId);  // throws if not found
            throw new InsufficientStockException(
                    product.getSku(), quantity, product.getStockQuantity());
        }

        // Update status if stock hit zero (separate from the atomic check)
        Product product = getProductEntityById(productId);
        if (product.getStockQuantity() == 0) {
            product.setStatus(ProductStatus.OUT_OF_STOCK);
        }
    }

    @CacheEvict(value = "products", key = "#productId")
    @Transactional
    public void restoreStock(Long productId, int quantity) {
        log.info("Evicting product {} from cache (stock restore)", productId);
        // Verify product exists
        getProductEntityById(productId);

        // Atomic stock addition
        productRepository.restoreStock(productId, quantity);

        // Reactivate if was out of stock
        Product product = getProductEntityById(productId);
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
