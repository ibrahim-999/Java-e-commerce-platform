package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.BaseIntegrationTest;
import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.factory.ProductFactory;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("ProductController Additional Integration Tests")
class ProductControllerAdditionalTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CacheManager cacheManager;

    private Category testCategory;
    private Category secondCategory;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

        productRepository.deleteAll();
        categoryRepository.deleteAll();
        testCategory = categoryRepository.save(
                Category.builder().name("Electronics").description("Electronic devices").build());
        secondCategory = categoryRepository.save(
                Category.builder().name("Books").description("Books and literature").build());
    }

    private Long createProductAndGetId(CreateProductRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    private CreateProductRequest createRequestWithStock(Long categoryId, int stock, BigDecimal price) {
        return CreateProductRequest.builder()
                .name("Test Product " + System.nanoTime())
                .description("A test product")
                .price(price)
                .stockQuantity(stock)
                .sku("SKU-" + System.nanoTime())
                .categoryId(categoryId)
                .build();
    }

    @Nested
    @DisplayName("PUT /api/products/{id}")
    class UpdateProduct {

        @Test
        @DisplayName("should update product successfully")
        void shouldUpdateProduct() throws Exception {
            CreateProductRequest createRequest = ProductFactory.createProductRequest(testCategory.getId());
            Long productId = createProductAndGetId(createRequest);

            UpdateProductRequest updateRequest = UpdateProductRequest.builder()
                    .name("Updated Product Name")
                    .description("Updated description")
                    .price(BigDecimal.valueOf(99.99))
                    .build();

            mockMvc.perform(put("/api/products/{id}", productId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Product updated successfully"))
                    .andExpect(jsonPath("$.data.name").value("Updated Product Name"))
                    .andExpect(jsonPath("$.data.description").value("Updated description"))
                    .andExpect(jsonPath("$.data.price").value(99.99));
        }

        @Test
        @DisplayName("should return 404 when updating non-existent product")
        void shouldReturn404WhenProductNotFound() throws Exception {
            UpdateProductRequest updateRequest = UpdateProductRequest.builder()
                    .name("Updated Name")
                    .build();

            mockMvc.perform(put("/api/products/{id}", 999999L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("PUT /api/products/{id}/stock/reduce")
    class ReduceStock {

        @Test
        @DisplayName("should reduce stock successfully")
        void shouldReduceStockSuccessfully() throws Exception {
            CreateProductRequest createRequest = createRequestWithStock(
                    testCategory.getId(), 20, BigDecimal.valueOf(29.99));
            Long productId = createProductAndGetId(createRequest);

            mockMvc.perform(put("/api/products/{id}/stock/reduce", productId)
                            .param("quantity", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Stock reduced successfully"));

            // Verify the stock was reduced
            mockMvc.perform(get("/api/products/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stockQuantity").value(15));
        }

        @Test
        @DisplayName("should return 400 when insufficient stock")
        void shouldReturn400WhenInsufficientStock() throws Exception {
            CreateProductRequest createRequest = createRequestWithStock(
                    testCategory.getId(), 3, BigDecimal.valueOf(19.99));
            Long productId = createProductAndGetId(createRequest);

            mockMvc.perform(put("/api/products/{id}/stock/reduce", productId)
                            .param("quantity", "50"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Insufficient stock")));
        }
    }

    @Nested
    @DisplayName("PUT /api/products/{id}/stock/restore")
    class RestoreStock {

        @Test
        @DisplayName("should restore stock successfully")
        void shouldRestoreStockSuccessfully() throws Exception {
            CreateProductRequest createRequest = createRequestWithStock(
                    testCategory.getId(), 5, BigDecimal.valueOf(39.99));
            Long productId = createProductAndGetId(createRequest);

            mockMvc.perform(put("/api/products/{id}/stock/restore", productId)
                            .param("quantity", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Stock restored successfully"));

            // Verify the stock was restored
            mockMvc.perform(get("/api/products/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.stockQuantity").value(15));
        }
    }

    @Nested
    @DisplayName("GET /api/products/category/{categoryId}")
    class GetProductsByCategory {

        @Test
        @DisplayName("should return products filtered by category")
        void shouldReturnProductsByCategory() throws Exception {
            // Create products in different categories
            createProductAndGetId(ProductFactory.createProductRequest(testCategory.getId()));
            createProductAndGetId(ProductFactory.createProductRequest(testCategory.getId()));
            createProductAndGetId(ProductFactory.createProductRequest(secondCategory.getId()));

            mockMvc.perform(get("/api/products/category/{categoryId}", testCategory.getId())
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].categoryName").value("Electronics"));

            mockMvc.perform(get("/api/products/category/{categoryId}", secondCategory.getId())
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].categoryName").value("Books"));
        }
    }

    @Nested
    @DisplayName("GET /api/products with sorting")
    class GetProductsSorted {

        @Test
        @DisplayName("should return products sorted by price ascending")
        void shouldReturnProductsSortedByPriceAsc() throws Exception {
            createProductAndGetId(createRequestWithStock(
                    testCategory.getId(), 10, BigDecimal.valueOf(99.99)));
            createProductAndGetId(createRequestWithStock(
                    testCategory.getId(), 10, BigDecimal.valueOf(9.99)));
            createProductAndGetId(createRequestWithStock(
                    testCategory.getId(), 10, BigDecimal.valueOf(49.99)));

            mockMvc.perform(get("/api/products")
                            .param("page", "0")
                            .param("size", "5")
                            .param("sort", "price,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(3))
                    .andExpect(jsonPath("$.content[0].price").value(9.99))
                    .andExpect(jsonPath("$.content[1].price").value(49.99))
                    .andExpect(jsonPath("$.content[2].price").value(99.99));
        }
    }
}
