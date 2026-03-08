package com.ecommerce.userservice.strategy;

import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

// Searches users by first name OR last name (partial, case-insensitive).
// Example: query "john" matches users named "John Smith" or "Jane Johnson".
//
// The repository method name does the heavy lifting:
//   findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase
// translates to:
//   WHERE LOWER(first_name) LIKE '%john%' OR LOWER(last_name) LIKE '%john%'

@Component
@RequiredArgsConstructor
public class NameSearchStrategy implements UserSearchStrategy {

    private final UserRepository userRepository;

    @Override
    public Page<User> search(String query, Pageable pageable) {
        // Pass the same query for both first name and last name parameters
        return userRepository.findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCase(
                query, query, pageable);
    }
}
