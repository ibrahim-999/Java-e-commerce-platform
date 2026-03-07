-- V1__create_users_table.sql
--
-- Flyway naming convention: V{version}__{description}.sql
--   V1  = version number (runs in order: V1, V2, V3, ...)
--   __  = double underscore separator (required)
--   create_users_table = human-readable description
--
-- This migration ONLY runs once. Flyway tracks it in a "flyway_schema_history"
-- table. If you need to change the schema, create V2, V3, etc. — never edit V1.

CREATE TABLE users (
    id              BIGSERIAL       PRIMARY KEY,        -- auto-incrementing ID
    first_name      VARCHAR(50)     NOT NULL,
    last_name       VARCHAR(50)     NOT NULL,
    email           VARCHAR(100)    NOT NULL UNIQUE,     -- no duplicate emails
    password        VARCHAR(255)    NOT NULL,             -- will store BCrypt hashes (60 chars, but 255 for safety)
    phone_number    VARCHAR(20),                          -- optional field
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP
);

-- Index on email for fast lookups during login.
-- Without this, PostgreSQL does a full table scan on every login query.
-- With this, it jumps directly to the matching row.
CREATE INDEX idx_users_email ON users(email);
