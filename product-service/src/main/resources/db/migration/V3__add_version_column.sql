-- Optimistic Locking: version column for concurrent update detection.
-- Hibernate increments this on every UPDATE.
-- If two transactions conflict, the second gets an OptimisticLockException.
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
