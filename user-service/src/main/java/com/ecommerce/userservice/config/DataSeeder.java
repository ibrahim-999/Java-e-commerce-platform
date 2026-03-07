package com.ecommerce.userservice.config;

import com.ecommerce.userservice.model.Role;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.model.UserStatus;
import com.ecommerce.userservice.repository.RoleRepository;
import com.ecommerce.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;

// CommandLineRunner — Spring runs this bean's run() method after the app starts.
// This is the Java/Spring equivalent of Laravel's DatabaseSeeder.
//
// @Profile("dev") — this seeder ONLY runs when the "dev" profile is active.
// In production, this class is completely ignored. You activate profiles via:
//   application.properties: spring.profiles.active=dev
//   or command line: java -jar app.jar --spring.profiles.active=dev
//
// @Slf4j — Lombok generates a logger: log.info(), log.error(), etc.

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        // Only seed if the database is empty — prevents duplicate data on restart
        if (userRepository.count() > 0) {
            log.info("Database already seeded, skipping...");
            return;
        }

        log.info("Seeding database with initial data...");

        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseThrow(() -> new RuntimeException("ROLE_CUSTOMER not found"));
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseThrow(() -> new RuntimeException("ROLE_ADMIN not found"));
        Role sellerRole = roleRepository.findByName("ROLE_SELLER")
                .orElseThrow(() -> new RuntimeException("ROLE_SELLER not found"));

        // Create an admin user
        User admin = User.builder()
                .firstName("Admin")
                .lastName("User")
                .email("admin@ecommerce.com")
                .password("admin123")  // will be hashed in Phase 3
                .phoneNumber("+1000000000")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(adminRole, customerRole))
                .build();
        userRepository.save(admin);

        // Create a seller user
        User seller = User.builder()
                .firstName("Seller")
                .lastName("User")
                .email("seller@ecommerce.com")
                .password("seller123")
                .phoneNumber("+1000000001")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(sellerRole, customerRole))
                .build();
        userRepository.save(seller);

        // Create a regular customer
        User customer = User.builder()
                .firstName("Customer")
                .lastName("User")
                .email("customer@ecommerce.com")
                .password("customer123")
                .phoneNumber("+1000000002")
                .status(UserStatus.ACTIVE)
                .roles(Set.of(customerRole))
                .build();
        userRepository.save(customer);

        log.info("Database seeded with 3 users (admin, seller, customer)");
    }
}
