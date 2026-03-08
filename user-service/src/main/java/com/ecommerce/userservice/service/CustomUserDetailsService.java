package com.ecommerce.userservice.service;

import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

// UserDetailsService is the interface Spring Security uses to load user data.
// When someone tries to log in, Spring calls loadUserByUsername() to find the user
// and check their password.
//
// We implement this interface to tell Spring Security:
// "Here's how you find users in OUR database."

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Find our User entity from the database
        com.ecommerce.userservice.model.User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        // Convert our User entity into Spring Security's UserDetails.
        // Spring Security's User class (different from our User entity) expects:
        //   - username (we use email)
        //   - password (the hashed password from DB)
        //   - authorities (roles converted to GrantedAuthority)
        return new User(
                user.getEmail(),
                user.getPassword(),
                user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority(role.getName()))
                        .toList()
        );
    }
}
