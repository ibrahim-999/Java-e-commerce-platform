package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.CreateProductRequest;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DisplayName("ProductController Integration Tests")
class ProductControllerIntegrationTest {

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

    @BeforeEach
    void setUp() {
        // Clear Redis cache BEFORE clearing the DB — prevents stale cached data
        // from leaking between tests (DB rows are deleted but cache still holds old values)
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());

        productRepository.deleteAll();
        categoryRepository.deleteAll();
        testCategory = categoryRepository.save(
                Category.builder().name("Electronics").description("Electronic devices").build());
    }

    // Helper: create a product and return its ID
    private Long createProductAndGetId(CreateProductRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("data").get("id").asLong();
    }

    @Nested
    @DisplayName("POST /api/products")
    class CreateProduct {

        @Test
        @DisplayName("should create product successfully")
        void shouldCreateProduct() throws Exception {
            CreateProductRequest request = ProductFactory.createProductRequest(testCategory.getId());

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value(request.getName()))
                    .andExpect(jsonPath("$.data.sku").value(request.getSku()))
                    .andExpect(jsonPath("$.data.categoryName").value("Electronics"));
        }

        @Test
        @DisplayName("should return 409 when SKU already exists")
        void shouldReturn409WhenDuplicateSku() throws Exception {
            CreateProductRequest request = ProductFactory.createProductRequest(testCategory.getId());

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("should return 400 when validation fails")
        void shouldReturn400WhenValidationFails() throws Exception {
            CreateProductRequest request = CreateProductRequest.builder()
                    .name("")  // blank — fails @NotBlank
                    .build();

            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/products")
    class GetProducts {

        @Test
        @DisplayName("should return paginated products")
        void shouldReturnPaginatedProducts() throws Exception {
            CreateProductRequest request = ProductFactory.createProductRequest(testCategory.getId());
            createProductAndGetId(request);

            mockMvc.perform(get("/api/products")
                            .param("page", "0").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("should return product by ID")
        void shouldReturnProductById() throws Exception {
            CreateProductRequest request = ProductFactory.createProductRequest(testCategory.getId());
            Long productId = createProductAndGetId(request);

            mockMvc.perform(get("/api/products/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(productId))
                    .andExpect(jsonPath("$.data.name").value(request.getName()));
        }

        @Test
        @DisplayName("should return 404 when product not found")
        void shouldReturn404WhenNotFound() throws Exception {
            mockMvc.perform(get("/api/products/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/products/search")
    class SearchProducts {

        @Test
        @DisplayName("should search products by name")
        void shouldSearchByName() throws Exception {
            CreateProductRequest request = ProductFactory.createProductRequest(testCategory.getId());
            createProductAndGetId(request);

            mockMvc.perform(get("/api/products/search")
                            .param("name", request.getName().substring(0, 3)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/categories")
    class GetCategories {

        @Test
        @DisplayName("should return all categories")
        void shouldReturnAllCategories() throws Exception {
            mockMvc.perform(get("/api/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()", greaterThanOrEqualTo(1)));
        }
    }

    @Nested
    @DisplayName("GET /api/products/stats (Price Aggregation)")
    class PriceStats {

        @Test
        @DisplayName("should return overall price statistics")
        void shouldReturnOverallStats() throws Exception {
            // Create a couple of products so stats have data
            createProductAndGetId(ProductFactory.createProductRequest(testCategory.getId()));
            createProductAndGetId(ProductFactory.createProductRequest(testCategory.getId()));

            mockMvc.perform(get("/api/products/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.averagePrice").isNumber())
                    .andExpect(jsonPath("$.data.minPrice").isNumber())
                    .andExpect(jsonPath("$.data.maxPrice").isNumber())
                    .andExpect(jsonPath("$.data.productCount").value(2));
        }

        @Test
        @DisplayName("should return category-specific price stats")
        void shouldReturnCategoryStats() throws Exception {
            createProductAndGetId(ProductFactory.createProductRequest(testCategory.getId()));

            mockMvc.perform(get("/api/products/stats/category/{id}", testCategory.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.productCount").value(1));
        }

        @Test
        @DisplayName("should return 404 for non-existent category stats")
        void shouldReturn404ForNonExistentCategory() throws Exception {
            mockMvc.perform(get("/api/products/stats/category/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/products/{id}")
    class DeleteProduct {

        @Test
        @DisplayName("should delete product successfully")
        void shouldDeleteProduct() throws Exception {
            CreateProductRequest request = ProductFactory.createProductRequest(testCategory.getId());
            Long productId = createProductAndGetId(request);

            mockMvc.perform(delete("/api/products/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            // Verify it's actually deleted
            mockMvc.perform(get("/api/products/{id}", productId))
                    .andExpect(status().isNotFound());
        }
    }
}
