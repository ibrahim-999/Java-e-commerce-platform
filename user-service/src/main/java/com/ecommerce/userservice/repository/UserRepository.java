package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// @Repository — tells Spring "this is a data access component".
// Spring will create a bean (managed instance) of this interface automatically.
@Repository

// JpaRepository<User, Long> — two type parameters:
//   User = the entity this repository manages
//   Long = the type of the entity's primary key (@Id field)
//
// By extending JpaRepository, you get ALL of these methods for FREE:
//   save(user)          — INSERT or UPDATE a user
//   findById(1L)        — SELECT * FROM users WHERE id = 1
//   findAll()           — SELECT * FROM users
//   deleteById(1L)      — DELETE FROM users WHERE id = 1
//   count()             — SELECT COUNT(*) FROM users
//   existsById(1L)      — SELECT EXISTS(... WHERE id = 1)
//   findAll(pageable)   — SELECT * FROM users LIMIT ? OFFSET ? (pagination!)
//   ... and many more
//
// You NEVER write the implementation. Spring generates it at startup.

public interface UserRepository extends JpaRepository<User, Long> {

    // ==================== QUERY METHODS ====================
    // Spring reads the method name and generates the SQL automatically.
    // This is called "query derivation" — the method name IS the query.

    // findByEmail → SELECT * FROM users WHERE email = ?
    // Returns Optional because the user might not exist.
    // Optional forces you to handle the "not found" case — no NullPointerException.
    Optional<User> findByEmail(String email);

    // existsByEmail → SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
    // Returns true/false — useful for checking duplicates before inserting.
    boolean existsByEmail(String email);

    // findByStatus → SELECT * FROM users WHERE status = ?
    // Returns a List because multiple users can have the same status.
    List<User> findByStatus(UserStatus status);

    // findByFirstNameContainingIgnoreCase → SELECT * FROM users
    //   WHERE LOWER(first_name) LIKE LOWER('%keyword%')
    // "Containing" = LIKE with wildcards on both sides
    // "IgnoreCase" = case-insensitive search
    List<User> findByFirstNameContainingIgnoreCase(String firstName);
}
