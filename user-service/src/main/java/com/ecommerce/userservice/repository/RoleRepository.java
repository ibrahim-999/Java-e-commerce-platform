package com.ecommerce.userservice.repository;

import com.ecommerce.userservice.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    // findByName → SELECT * FROM roles WHERE name = ?
    // Used when assigning roles: roleRepository.findByName("ROLE_CUSTOMER")
    Optional<Role> findByName(String name);
}
