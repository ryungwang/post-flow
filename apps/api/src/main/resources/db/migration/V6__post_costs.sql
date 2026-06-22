-- Per-post cost (ad/promotion spend) — ROI% denominator
CREATE TABLE post_costs (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users (id),
    post_id    BIGINT        NOT NULL UNIQUE REFERENCES posts (id),
    amount     NUMERIC(14,2) NOT NULL,
    currency   VARCHAR(8)    NOT NULL DEFAULT 'KRW',
    note       VARCHAR(500),
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_post_costs_user ON post_costs (user_id);
