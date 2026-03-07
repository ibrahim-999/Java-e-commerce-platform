-- V2__create_roles_table.sql
--
-- Roles table and the join table that links users to roles.

CREATE TABLE roles (
    id      BIGSERIAL       PRIMARY KEY,
    name    VARCHAR(50)     NOT NULL UNIQUE      -- ROLE_ADMIN, ROLE_CUSTOMER, ROLE_SELLER
);

-- The join table for the @ManyToMany relationship.
-- This table has NO entity in Java — JPA manages it via @JoinTable on User.
-- The composite primary key (user_id, role_id) prevents duplicate assignments.
CREATE TABLE user_roles (
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id     BIGINT      NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Seed the default roles so they exist before any user registers.
-- These match the Spring Security convention: ROLE_ prefix + role name.
INSERT INTO roles (name) VALUES ('ROLE_CUSTOMER');
INSERT INTO roles (name) VALUES ('ROLE_SELLER');
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');
