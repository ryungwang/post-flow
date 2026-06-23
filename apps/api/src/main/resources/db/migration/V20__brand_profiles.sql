CREATE TABLE brand_profiles (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id),
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(1000),
    audience    VARCHAR(500),
    key_points  VARCHAR(1000),
    cta_text    VARCHAR(300),
    url         VARCHAR(2048),
    is_default  BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_brand_profiles_user ON brand_profiles (user_id);
