package com.ecommerce.userservice.strategy;

import com.ecommerce.userservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

// Strategy Pattern — a behavioral design pattern.
//
// The problem:
//   Users want to search by email, name, or status. Without Strategy, you'd write:
//
//   if (type.equals("email")) { ... }
//   else if (type.equals("name")) { ... }
//   else if (type.equals("status")) { ... }
//
//   This violates the Open/Closed Principle — adding a new search type
//   means modifying existing code (adding another else-if).
//
// The solution:
//   1. Define an interface (this file) — the "strategy contract"
//   2. Each search type is its own class implementing this interface
//   3. A service picks the right strategy at runtime
//
//   Adding a new search type = creating a new class. No existing code changes.
//
// This is the same concept as Laravel's "strategy" or "policy" patterns,
// but formalized as a Java interface.

public interface UserSearchStrategy {

    // Every search strategy must implement this method.
    // Takes a search query and pagination info, returns a page of results.
    Page<User> search(String query, Pageable pageable);
}
