package com.ecommerce.userservice.strategy;

import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.model.UserStatus;
import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

// Searches users by status (exact match: ACTIVE, INACTIVE, or SUSPENDED).
// The query string is converted to the UserStatus enum.
//
// Example: query "ACTIVE" → UserStatus.ACTIVE → finds all active users.
// If the query isn't a valid status, valueOf() throws IllegalArgumentException,
// which our GlobalExceptionHandler catches and returns a 400 Bad Request.

@Component
@RequiredArgsConstructor
public class StatusSearchStrategy implements UserSearchStrategy {

    private final UserRepository userRepository;

    @Override
    public Page<User> search(String query, Pageable pageable) {
        UserStatus status = UserStatus.valueOf(query.toUpperCase());
        return userRepository.findByStatus(status, pageable);
    }
}
