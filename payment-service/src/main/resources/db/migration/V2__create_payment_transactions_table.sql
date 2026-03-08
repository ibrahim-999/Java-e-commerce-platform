-- Payment transactions — audit log of every gateway attempt.
-- Each payment can have multiple transactions (retries, refunds).
CREATE TABLE payment_transactions (
    id                      BIGSERIAL PRIMARY KEY,
    payment_id              BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    type                    VARCHAR(20) NOT NULL,         -- CHARGE, REFUND
    status                  VARCHAR(20) NOT NULL,         -- SUCCESS, FAILED
    gateway_name            VARCHAR(50) NOT NULL,         -- STRIPE, PAYPAL, BANK_TRANSFER
    gateway_transaction_id  VARCHAR(100),                 -- null if failed
    amount                  DECIMAL(12,2) NOT NULL,
    message                 VARCHAR(500),
    attempt_number          INTEGER NOT NULL DEFAULT 1,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_txn_payment_id ON payment_transactions(payment_id);
CREATE INDEX idx_payment_txn_type ON payment_transactions(type);
CREATE INDEX idx_payment_txn_status ON payment_transactions(status);
