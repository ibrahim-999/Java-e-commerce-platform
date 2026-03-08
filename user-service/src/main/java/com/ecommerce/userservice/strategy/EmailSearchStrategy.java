package com.ecommerce.userservice.strategy;

import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

// @Component — registers this class as a Spring bean.
// Spring will create one instance and make it available for injection.
//
// This strategy searches users by email (partial, case-insensitive).
// Example: query "john" matches "john@example.com", "JOHN.DOE@gmail.com", etc.

@Component
@RequiredArgsConstructor
public class EmailSearchStrategy implements UserSearchStrategy {

    private final UserRepository userRepository;

    @Override
    public Page<User> search(String query, Pageable pageable) {
        return userRepository.findByEmailContainingIgnoreCase(query, pageable);
    }
}
