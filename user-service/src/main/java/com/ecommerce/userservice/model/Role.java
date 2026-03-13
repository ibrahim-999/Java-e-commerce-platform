package com.ecommerce.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A Role is simpler than User — just an ID and a name.
// We keep it as its own table (not an enum) because:
//   1. You can add new roles without changing code (just insert a row)
//   2. You can attach permissions to roles later
//   3. It's the standard approach for RBAC (Role-Based Access Control)

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Each role has a unique name: ROLE_ADMIN, ROLE_CUSTOMER, ROLE_SELLER
    // The "ROLE_" prefix is a Spring Security convention — it expects it
    // when checking roles with @PreAuthorize("hasRole('ADMIN')").
    // Spring automatically adds the prefix when checking, so you store
    // "ROLE_ADMIN" in the DB and check for "ADMIN" in your code.
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;
}
