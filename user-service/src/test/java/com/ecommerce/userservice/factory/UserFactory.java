package com.ecommerce.userservice.factory;

import com.ecommerce.userservice.dto.CreateUserRequest;
import com.ecommerce.userservice.model.Role;
import com.ecommerce.userservice.model.User;
import com.ecommerce.userservice.model.UserStatus;
import net.datafaker.Faker;

import java.util.Set;

// Test factory — the Java equivalent of Laravel's Factory.
// Generates realistic fake data so tests don't use hardcoded "John Doe" everywhere.
//
// Usage in tests:
//   User user = UserFactory.createUser();                    // random user
//   User admin = UserFactory.createUser(adminRole);          // user with specific role
//   CreateUserRequest req = UserFactory.createUserRequest(); // DTO for controller tests

public class UserFactory {

    private static final Faker faker = new Faker();

    // Creates a User entity with random data and a given role
    public static User createUser(Role role) {
        return User.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .email(faker.internet().emailAddress())
                .password("password123")
                .phoneNumber(faker.phoneNumber().cellPhone())
                .status(UserStatus.ACTIVE)
                .roles(Set.of(role))
                .build();
    }

    // Creates a User entity with random data (no role — for unit tests where roles are mocked)
    public static User createUser() {
        return User.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .email(faker.internet().emailAddress())
                .password("password123")
                .phoneNumber(faker.phoneNumber().cellPhone())
                .status(UserStatus.ACTIVE)
                .build();
    }

    // Creates a User entity with a specific ID (for mocking repository responses)
    public static User createUserWithId(Long id) {
        User user = createUser();
        user.setId(id);
        return user;
    }

    // Creates a CreateUserRequest DTO (for controller/integration tests)
    public static CreateUserRequest createUserRequest() {
        return CreateUserRequest.builder()
                .firstName(faker.name().firstName())
                .lastName(faker.name().lastName())
                .email(faker.internet().emailAddress())
                .password("password123")
                .phoneNumber(faker.phoneNumber().cellPhone())
                .build();
    }

    // Creates the default ROLE_CUSTOMER
    public static Role customerRole() {
        Role role = new Role();
        role.setId(1L);
        role.setName("ROLE_CUSTOMER");
        return role;
    }

    // Creates ROLE_ADMIN
    public static Role adminRole() {
        Role role = new Role();
        role.setId(2L);
        role.setName("ROLE_ADMIN");
        return role;
    }
}
