package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.PriceStatsResponse;
import com.ecommerce.productservice.exception.DuplicateResourceException;
import com.ecommerce.productservice.exception.InsufficientStockException;
import com.ecommerce.productservice.exception.ResourceNotFoundException;
import com.ecommerce.productservice.factory.ProductFactory;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.model.ProductStatus;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    @Nested
    @DisplayName("createProduct")
    class CreateProduct {

        @Test
        @DisplayName("should create product successfully")
        void shouldCreateProductSuccessfully() {
            Product product = ProductFactory.createProduct();
            Category category = ProductFactory.createCategoryWithId(1L);

            when(productRepository.existsBySku(anyString())).thenReturn(false);
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
                Product saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            Product result = productService.createProduct(product, 1L);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getCategory()).isEqualTo(category);
            assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
            verify(productRepository).save(product);
        }

        @Test
        @DisplayName("should throw when SKU already exists")
        void shouldThrowWhenDuplicateSku() {
            Product product = ProductFactory.createProduct();

            when(productRepository.existsBySku(product.getSku())).thenReturn(true);

            assertThatThrownBy(() -> productService.createProduct(product, 1L))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already exists with sku");

            verify(productRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when category not found")
        void shouldThrowWhenCategoryNotFound() {
            Product product = ProductFactory.createProduct();

            when(productRepository.existsBySku(anyString())).thenReturn(false);
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.createProduct(product, 99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category not found with id: 99");
        }
    }

    @Nested
    @DisplayName("getProductById")
    class GetProductById {

        @Test
        @DisplayName("should return product when found")
        void shouldReturnProductWhenFound() {
            Product product = ProductFactory.createProductWithId(1L);

            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            Product result = productService.getProductById(1L);

            assertThat(result).isEqualTo(product);
        }

        @Test
        @DisplayName("should throw when product not found")
        void shouldThrowWhenNotFound() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found with id: 99");
        }
    }

    @Nested
    @DisplayName("reduceStock (Atomic SQL)")
    class ReduceStock {

        @Test
        @DisplayName("should reduce stock atomically — returns 1 row affected")
        void shouldReduceStockAtomically() {
            // Atomic SQL returns 1 = success
            when(productRepository.reduceStock(1L, 3)).thenReturn(1);

            // After the atomic update, the service reloads to check if status needs updating
            Product product = ProductFactory.createProductWithId(1L);
            product.setStockQuantity(7);  // 10 - 3 = 7 (still has stock)
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            productService.reduceStock(1L, 3);

            verify(productRepository).reduceStock(1L, 3);
            // Status stays ACTIVE because stock > 0
            assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("should set OUT_OF_STOCK when stock reaches zero")
        void shouldSetOutOfStockWhenZero() {
            when(productRepository.reduceStock(1L, 5)).thenReturn(1);

            Product product = ProductFactory.createProductWithId(1L);
            product.setStockQuantity(0);  // after atomic reduction: 5 - 5 = 0
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            productService.reduceStock(1L, 5);

            assertThat(product.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("should throw InsufficientStockException when atomic update returns 0")
        void shouldThrowWhenInsufficientStock() {
            // Atomic SQL returns 0 = insufficient stock (WHERE stock >= quantity failed)
            when(productRepository.reduceStock(1L, 50)).thenReturn(0);

            Product product = ProductFactory.createProductWithId(1L);
            product.setStockQuantity(2);
            product.setSku("SKU-123");
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productService.reduceStock(1L, 50))
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock");
        }
    }

    @Nested
    @DisplayName("restoreStock (Atomic SQL)")
    class RestoreStock {

        @Test
        @DisplayName("should restore stock and reactivate product")
        void shouldRestoreStockAndReactivate() {
            Product product = ProductFactory.createProductWithId(1L);
            product.setStockQuantity(0);
            product.setStatus(ProductStatus.OUT_OF_STOCK);

            // findById is called multiple times: once to verify existence, once after restore
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.restoreStock(1L, 10)).thenAnswer(invocation -> {
                // Simulate the atomic SQL restoring stock
                product.setStockQuantity(10);
                return 1;
            });

            productService.restoreStock(1L, 10);

            verify(productRepository).restoreStock(1L, 10);
            assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("getPriceStats (Database Aggregation)")
    class GetPriceStats {

        @Test
        @DisplayName("should return price statistics computed by the database")
        void shouldReturnPriceStats() {
            when(productRepository.getAveragePrice()).thenReturn(BigDecimal.valueOf(49.99));
            when(productRepository.getMinPrice()).thenReturn(BigDecimal.valueOf(9.99));
            when(productRepository.getMaxPrice()).thenReturn(BigDecimal.valueOf(999.99));
            when(productRepository.count()).thenReturn(1000L);

            PriceStatsResponse stats = productService.getPriceStats();

            assertThat(stats.averagePrice()).isEqualByComparingTo(BigDecimal.valueOf(49.99));
            assertThat(stats.minPrice()).isEqualByComparingTo(BigDecimal.valueOf(9.99));
            assertThat(stats.maxPrice()).isEqualByComparingTo(BigDecimal.valueOf(999.99));
            assertThat(stats.productCount()).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("getPriceStatsByCategory")
    class GetPriceStatsByCategory {

        @Test
        @DisplayName("should return category-specific price stats")
        void shouldReturnCategoryPriceStats() {
            Category category = ProductFactory.createCategoryWithId(1L);
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(productRepository.getAveragePriceByCategory(1L)).thenReturn(BigDecimal.valueOf(29.99));
            when(productRepository.getMinPriceByCategory(1L)).thenReturn(BigDecimal.valueOf(5.00));
            when(productRepository.getMaxPriceByCategory(1L)).thenReturn(BigDecimal.valueOf(99.99));
            when(productRepository.countByCategoryId(1L)).thenReturn(50L);

            PriceStatsResponse stats = productService.getPriceStatsByCategory(1L);

            assertThat(stats.averagePrice()).isEqualByComparingTo(BigDecimal.valueOf(29.99));
            assertThat(stats.productCount()).isEqualTo(50L);
        }

        @Test
        @DisplayName("should throw when category not found")
        void shouldThrowWhenCategoryNotFound() {
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getPriceStatsByCategory(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category not found with id: 99");
        }
    }

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProduct {

        @Test
        @DisplayName("should delete product when found")
        void shouldDeleteProductWhenFound() {
            Product product = ProductFactory.createProductWithId(1L);

            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            productService.deleteProduct(1L);

            verify(productRepository).delete(product);
        }
    }
}
