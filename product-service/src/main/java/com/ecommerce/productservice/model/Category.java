package com.ecommerce.productservice.model;

import jakarta.persistence.*;
import lombok.*;

// Category — a grouping for products (e.g., "Electronics", "Clothing", "Books").
// A category has many products (one-to-many relationship).

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;
}
