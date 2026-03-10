package com.ecommerce.productservice.service;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Additional Unit Tests")
class ProductServiceAdditionalTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    @Nested
    @DisplayName("updateProduct")
    class UpdateProduct {

        @Test
        @DisplayName("should update product with partial fields (only name and description)")
        void shouldUpdateProductWithPartialFields() {
            Product existing = ProductFactory.createProductWithId(1L);
            existing.setName("Old Name");
            existing.setDescription("Old Description");
            existing.setPrice(BigDecimal.valueOf(50.00));
            existing.setStockQuantity(10);
            existing.setStatus(ProductStatus.ACTIVE);

            Product updatedData = Product.builder()
                    .name("New Name")
                    .description("New Description")
                    .build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

            Product result = productService.updateProduct(1L, updatedData, null);

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getDescription()).isEqualTo("New Description");
            // Price and stock remain unchanged
            assertThat(result.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(50.00));
            assertThat(result.getStockQuantity()).isEqualTo(10);
        }

        @Test
        @DisplayName("should update product with category change")
        void shouldUpdateProductWithCategoryChange() {
            Product existing = ProductFactory.createProductWithId(1L);
            Category oldCategory = ProductFactory.createCategoryWithId(1L);
            existing.setCategory(oldCategory);

            Category newCategory = ProductFactory.createCategoryWithId(2L);

            Product updatedData = Product.builder().build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(categoryRepository.findById(2L)).thenReturn(Optional.of(newCategory));

            Product result = productService.updateProduct(1L, updatedData, 2L);

            assertThat(result.getCategory()).isEqualTo(newCategory);
            assertThat(result.getCategory().getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("should set status to OUT_OF_STOCK when stock goes to 0")
        void shouldSetOutOfStockWhenStockGoesToZero() {
            Product existing = ProductFactory.createProductWithId(1L);
            existing.setStockQuantity(10);
            existing.setStatus(ProductStatus.ACTIVE);

            Product updatedData = Product.builder()
                    .stockQuantity(0)
                    .build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

            Product result = productService.updateProduct(1L, updatedData, null);

            assertThat(result.getStockQuantity()).isEqualTo(0);
            assertThat(result.getStatus()).isEqualTo(ProductStatus.OUT_OF_STOCK);
        }

        @Test
        @DisplayName("should set status to ACTIVE when stock restored from 0")
        void shouldSetActiveWhenStockRestoredFromZero() {
            Product existing = ProductFactory.createProductWithId(1L);
            existing.setStockQuantity(0);
            existing.setStatus(ProductStatus.OUT_OF_STOCK);

            Product updatedData = Product.builder()
                    .stockQuantity(25)
                    .build();

            when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

            Product result = productService.updateProduct(1L, updatedData, null);

            assertThat(result.getStockQuantity()).isEqualTo(25);
            assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when product not found")
        void shouldThrowWhenProductNotFound() {
            Product updatedData = Product.builder()
                    .name("Updated")
                    .build();

            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(99L, updatedData, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found with id: 99");
        }
    }

    @Nested
    @DisplayName("getProductEntityById")
    class GetProductEntityById {

        @Test
        @DisplayName("should return product entity when found")
        void shouldReturnProductEntityWhenFound() {
            Product product = ProductFactory.createProductWithId(1L);

            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            Product result = productService.getProductEntityById(1L);

            assertThat(result).isEqualTo(product);
            assertThat(result.getId()).isEqualTo(1L);
            verify(productRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductEntityById(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found with id: 99");
        }
    }

    @Nested
    @DisplayName("getProductBySku")
    class GetProductBySku {

        @Test
        @DisplayName("should return product when SKU found")
        void shouldReturnProductWhenSkuFound() {
            Product product = ProductFactory.createProductWithId(1L);
            product.setSku("SKU-12345678");

            when(productRepository.findBySku("SKU-12345678")).thenReturn(Optional.of(product));

            Product result = productService.getProductBySku("SKU-12345678");

            assertThat(result).isEqualTo(product);
            assertThat(result.getSku()).isEqualTo("SKU-12345678");
            verify(productRepository).findBySku("SKU-12345678");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when SKU not found")
        void shouldThrowWhenSkuNotFound() {
            when(productRepository.findBySku("NONEXISTENT")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductBySku("NONEXISTENT"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found with sku: NONEXISTENT");
        }
    }

    @Nested
    @DisplayName("getAllProducts")
    class GetAllProducts {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Product product1 = ProductFactory.createProductWithId(1L);
            Product product2 = ProductFactory.createProductWithId(2L);
            Pageable pageable = PageRequest.of(0, 5);
            Page<Product> page = new PageImpl<>(List.of(product1, product2), pageable, 2);

            when(productRepository.findAll(pageable)).thenReturn(page);

            Page<Product> result = productService.getAllProducts(pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getNumber()).isEqualTo(0);
            assertThat(result.getSize()).isEqualTo(5);
            verify(productRepository).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("getProductsByCategory")
    class GetProductsByCategory {

        @Test
        @DisplayName("should return products by category")
        void shouldReturnProductsByCategory() {
            Product product1 = ProductFactory.createProductWithId(1L);
            Product product2 = ProductFactory.createProductWithId(2L);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> page = new PageImpl<>(List.of(product1, product2), pageable, 2);

            when(productRepository.findByCategoryId(1L, pageable)).thenReturn(page);

            Page<Product> result = productService.getProductsByCategory(1L, pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            verify(productRepository).findByCategoryId(1L, pageable);
        }
    }

    @Nested
    @DisplayName("searchProducts")
    class SearchProducts {

        @Test
        @DisplayName("should return matching products")
        void shouldReturnMatchingProducts() {
            Product product = ProductFactory.createProductWithId(1L);
            product.setName("Wireless Mouse");
            Pageable pageable = PageRequest.of(0, 10);
            Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);

            when(productRepository.findByNameContainingIgnoreCase("wireless", pageable)).thenReturn(page);

            Page<Product> result = productService.searchProducts("wireless", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Wireless Mouse");
            verify(productRepository).findByNameContainingIgnoreCase("wireless", pageable);
        }
    }

    @Nested
    @DisplayName("deleteProduct")
    class DeleteProduct {

        @Test
        @DisplayName("should throw ResourceNotFoundException when product not found")
        void shouldThrowWhenProductNotFound() {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Product not found with id: 99");

            verify(productRepository, never()).delete(any());
        }
    }
}
