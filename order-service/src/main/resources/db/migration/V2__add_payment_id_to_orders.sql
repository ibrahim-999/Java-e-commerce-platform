-- Add payment_id to orders table.
-- This is a cross-service reference to the payment in payment-service.
-- Like userId, it's a plain BIGINT — not a foreign key (different database).
ALTER TABLE orders ADD COLUMN payment_id BIGINT;
