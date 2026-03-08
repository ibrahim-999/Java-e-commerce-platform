CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_method  VARCHAR(20) NOT NULL,
    transaction_id  VARCHAR(100) UNIQUE,
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_user_id ON payments(user_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_transaction_id ON payments(transaction_id);
