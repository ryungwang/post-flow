-- PostFlow initial schema (Flyway manages the `postflow` schema; tables created within it)

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    profile_image VARCHAR(512),
    plan          VARCHAR(20)  NOT NULL DEFAULT 'FREE',
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE social_accounts (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL REFERENCES users (id),
    provider          VARCHAR(20)  NOT NULL DEFAULT 'THREADS',
    access_token      VARCHAR(1024) NOT NULL,
    expires_at        TIMESTAMPTZ,
    last_refreshed_at TIMESTAMPTZ,
    status            VARCHAR(30)  NOT NULL DEFAULT 'CONNECTED',
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_social_accounts_user ON social_accounts (user_id);

CREATE TABLE posts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id),
    content          VARCHAR(1000) NOT NULL,
    status           VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    scheduled_at     TIMESTAMPTZ,
    published_at     TIMESTAMPTZ,
    threads_media_id VARCHAR(255),
    error_message    VARCHAR(1000),
    retry_count      INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_posts_user ON posts (user_id);
CREATE INDEX idx_posts_status_scheduled ON posts (status, scheduled_at);

CREATE TABLE analytics (
    id              BIGSERIAL PRIMARY KEY,
    post_id         BIGINT       NOT NULL REFERENCES posts (id),
    views           BIGINT       NOT NULL DEFAULT 0,
    likes           BIGINT       NOT NULL DEFAULT 0,
    replies         BIGINT       NOT NULL DEFAULT 0,
    reposts         BIGINT       NOT NULL DEFAULT 0,
    quotes          BIGINT       NOT NULL DEFAULT 0,
    shares          BIGINT       NOT NULL DEFAULT 0,
    engagement_rate DOUBLE PRECISION NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_analytics_post ON analytics (post_id);

CREATE TABLE ai_generations (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id),
    provider      VARCHAR(30) NOT NULL,
    model         VARCHAR(60) NOT NULL,
    prompt        TEXT        NOT NULL,
    result        TEXT,
    input_tokens  BIGINT      NOT NULL DEFAULT 0,
    output_tokens BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_ai_generations_user ON ai_generations (user_id);
