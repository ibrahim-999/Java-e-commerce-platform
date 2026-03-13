package com.ecommerce.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

// @Entity — tells JPA "this class maps to a database table"
// Without this, Hibernate ignores this class completely.
@Entity

// @Table — specifies the table name in PostgreSQL.
// We use "users" (not "user") because "user" is a reserved keyword in PostgreSQL.
// If you name it "user", your queries will break.
@Table(name = "users")

// Lombok annotations — generate boilerplate code at compile time:
@Getter         // generates getters for all fields: getName(), getEmail(), etc.
@Setter         // generates setters for all fields: setName("John"), etc.
@NoArgsConstructor  // generates: public User() {} — required by JPA (it creates objects via reflection)
@AllArgsConstructor // generates: public User(Long id, String firstName, ...) — useful for testing
@Builder        // generates the Builder pattern: User.builder().firstName("John").build()
public class User {

    // @Id — marks this field as the primary key (unique identifier for each row)
    // @GeneratedValue(IDENTITY) — PostgreSQL auto-generates the ID (1, 2, 3, ...)
    //   using a SERIAL/BIGSERIAL column. You never set the ID manually.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @Column — maps this field to a specific column in the table
    //   nullable = false → this column cannot be NULL in the database (required field)
    //   length = 50      → VARCHAR(50) — limits the string length in the DB
    //
    // The column name defaults to the field name (firstName → first_name via naming strategy)
    // but we make it explicit with "name" for clarity.
    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    // unique = true → creates a UNIQUE constraint in the DB.
    // No two users can have the same email. The database enforces this,
    // not just your Java code — so even direct SQL inserts are protected.
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    // @Enumerated(STRING) — stores the enum value as a string in the DB ("ACTIVE", "INACTIVE")
    //   vs EnumType.ORDINAL which stores 0, 1, 2 — fragile because reordering the enum breaks data.
    //   ALWAYS use STRING for enums.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    // ==================== RELATIONSHIPS ====================
    //
    // @ManyToMany — a user can have MANY roles, a role can belong to MANY users.
    //
    // @JoinTable — defines the join table that links users and roles.
    //   In a many-to-many relationship, neither table stores the other's ID directly.
    //   Instead, a THIRD table (user_roles) holds the pairs:
    //
    //   user_roles table:
    //   | user_id | role_id |
    //   |---------|---------|
    //   |    1    |    1    |  ← user 1 has role 1 (ROLE_CUSTOMER)
    //   |    1    |    2    |  ← user 1 also has role 2 (ROLE_ADMIN)
    //   |    2    |    1    |  ← user 2 has role 1 (ROLE_CUSTOMER)
    //
    // fetch = EAGER — load roles immediately when loading a user.
    //   vs LAZY = only load roles when you access user.getRoles().
    //   We use EAGER here because we almost always need roles (for security checks).
    //   For large collections, LAZY is better to avoid loading unnecessary data.
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // @CreationTimestamp — Hibernate automatically sets this when the entity is first saved.
    //   You never set this manually — it's always the exact moment the row was created.
    // updatable = false — prevents this field from changing on updates.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // @UpdateTimestamp — Hibernate automatically updates this every time the entity is saved.
    //   Useful for tracking when a record was last modified.
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
