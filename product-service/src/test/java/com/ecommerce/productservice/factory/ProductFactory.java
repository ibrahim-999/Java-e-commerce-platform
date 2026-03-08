package com.ecommerce.productservice.factory;

import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.model.ProductStatus;
import net.datafaker.Faker;

import java.math.BigDecimal;

public class ProductFactory {

    private static final Faker faker = new Faker();

    public static Product createProduct() {
        return Product.builder()
                .name(faker.commerce().productName())
                .description(faker.lorem().sentence())
                .price(BigDecimal.valueOf(faker.number().randomDouble(2, 1, 999)))
                .stockQuantity(faker.number().numberBetween(1, 100))
                .sku("SKU-" + faker.number().digits(8))
                .status(ProductStatus.ACTIVE)
                .build();
    }

    public static Product createProductWithId(Long id) {
        Product product = createProduct();
        product.setId(id);
        return product;
    }

    public static Product createProductWithCategory(Category category) {
        Product product = createProduct();
        product.setCategory(category);
        return product;
    }

    public static CreateProductRequest createProductRequest(Long categoryId) {
        return CreateProductRequest.builder()
                .name(faker.commerce().productName())
                .description(faker.lorem().sentence())
                .price(BigDecimal.valueOf(faker.number().randomDouble(2, 1, 999)))
                .stockQuantity(faker.number().numberBetween(1, 100))
                .sku("SKU-" + faker.number().digits(8))
                .categoryId(categoryId)
                .build();
    }

    public static Category createCategory() {
        return Category.builder()
                .name(faker.commerce().department() + "-" + faker.number().digits(4))
                .description(faker.lorem().sentence())
                .build();
    }

    public static Category createCategoryWithId(Long id) {
        Category category = createCategory();
        category.setId(id);
        return category;
    }
}
