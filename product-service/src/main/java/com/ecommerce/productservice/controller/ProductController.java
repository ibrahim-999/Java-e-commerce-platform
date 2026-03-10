package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.*;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // POST /api/products — create a new product
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .sku(request.getSku())
                .build();

        Product saved = productService.createProduct(product, request.getCategoryId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", ProductResponse.fromEntity(saved)));
    }

    // GET /api/products/{id} — get product by ID (cached in Redis)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductById(@PathVariable Long id) {
        ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    // GET /api/products — list all products (paginated)
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParams = sort.split(",");
        Sort sortOrder = Sort.by(
                sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC,
                sortParams[0]
        );

        Pageable pageable = PageRequest.of(page, size, sortOrder);
        Page<ProductResponse> products = productService.getAllProducts(pageable)
                .map(ProductResponse::fromEntity);

        return ResponseEntity.ok(products);
    }

    // GET /api/products/search?name=phone — search products by name
    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductResponse> results = productService.searchProducts(name, pageable)
                .map(ProductResponse::fromEntity);

        return ResponseEntity.ok(results);
    }

    // GET /api/products/category/{categoryId} — products by category
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<ProductResponse> products = productService.getProductsByCategory(categoryId, pageable)
                .map(ProductResponse::fromEntity);

        return ResponseEntity.ok(products);
    }

    // GET /api/products/stats — overall price statistics
    // All computation happens in PostgreSQL. Java receives only the final numbers.
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PriceStatsResponse>> getPriceStats() {
        return ResponseEntity.ok(ApiResponse.success(productService.getPriceStats()));
    }

    // GET /api/products/stats/category/{categoryId} — price stats for a category
    @GetMapping("/stats/category/{categoryId}")
    public ResponseEntity<ApiResponse<PriceStatsResponse>> getPriceStatsByCategory(
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success(productService.getPriceStatsByCategory(categoryId)));
    }

    // PUT /api/products/{id} — update product
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        Product updatedData = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .build();

        Product updated = productService.updateProduct(id, updatedData, request.getCategoryId());
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", ProductResponse.fromEntity(updated)));
    }

    // DELETE /api/products/{id} — delete product
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }

    // ==================== STOCK API (called by order-service) ====================
    // These are "internal" endpoints — called by other microservices, not by end users.
    // In production, you'd protect these with service-to-service authentication.

    // PUT /api/products/{id}/stock/reduce?quantity=3
    @PutMapping("/{id}/stock/reduce")
    public ResponseEntity<ApiResponse<Void>> reduceStock(
            @PathVariable Long id,
            @RequestParam int quantity) {
        productService.reduceStock(id, quantity);
        return ResponseEntity.ok(ApiResponse.success("Stock reduced successfully", null));
    }

    // PUT /api/products/{id}/stock/restore?quantity=3
    @PutMapping("/{id}/stock/restore")
    public ResponseEntity<ApiResponse<Void>> restoreStock(
            @PathVariable Long id,
            @RequestParam int quantity) {
        productService.restoreStock(id, quantity);
        return ResponseEntity.ok(ApiResponse.success("Stock restored successfully", null));
    }
}
