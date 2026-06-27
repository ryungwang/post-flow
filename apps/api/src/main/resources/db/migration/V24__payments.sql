CREATE TABLE payments (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    amount     BIGINT       NOT NULL,
    currency   VARCHAR(10)  NOT NULL DEFAULT 'KRW',
    plan       VARCHAR(20)  NOT NULL,
    kind       VARCHAR(20)  NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    provider   VARCHAR(20)  NOT NULL,
    order_id   VARCHAR(120),
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_payments_user ON payments (user_id, created_at DESC);
