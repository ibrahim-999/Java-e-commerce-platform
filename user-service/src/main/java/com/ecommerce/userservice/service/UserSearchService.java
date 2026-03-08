package com.ecommerce.userservice.service;

import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.strategy.EmailSearchStrategy;
import com.ecommerce.userservice.strategy.NameSearchStrategy;
import com.ecommerce.userservice.strategy.StatusSearchStrategy;
import com.ecommerce.userservice.strategy.UserSearchStrategy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Map;

// UserSearchService — the "context" in the Strategy pattern.
//
// It holds a Map of all available strategies and picks the right one at runtime.
// The controller just says: searchService.search("email", "john", pageable)
// and this class handles which strategy to use.
//
// How Spring makes this elegant:
//   All three strategy classes are @Component beans.
//   Spring injects them into the constructor automatically.
//   We build the Map ourselves in the constructor — mapping type names to strategies.
//
// To add a new search type (e.g., "phone"):
//   1. Create PhoneSearchStrategy implements UserSearchStrategy
//   2. Add it to the map here: put("phone", phoneSearchStrategy)
//   That's it. No if/else chains. No changes to the controller.

@Service
public class UserSearchService {

    // Map<String, UserSearchStrategy> — maps a type name to its strategy.
    // "email" → EmailSearchStrategy
    // "name"  → NameSearchStrategy
    // "status" → StatusSearchStrategy
    private final Map<String, UserSearchStrategy> strategies;

    // Constructor injection — Spring passes in all three strategy beans.
    // We build the lookup map manually for clarity.
    public UserSearchService(
            EmailSearchStrategy emailStrategy,
            NameSearchStrategy nameStrategy,
            StatusSearchStrategy statusStrategy) {
        this.strategies = Map.of(
                "email", emailStrategy,
                "name", nameStrategy,
                "status", statusStrategy
        );
    }

    // Looks up the strategy by type and executes it.
    // If the type doesn't exist in the map, throws IllegalArgumentException
    // → GlobalExceptionHandler returns 400 Bad Request.
    public Page<User> search(String type, String query, Pageable pageable) {
        UserSearchStrategy strategy = strategies.get(type.toLowerCase());

        if (strategy == null) {
            throw new IllegalArgumentException(
                    "Unknown search type: " + type + ". Available types: " + strategies.keySet());
        }

        return strategy.search(query, pageable);
    }
}
