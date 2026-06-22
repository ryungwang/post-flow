-- Content ROI: trackable CTA links → clicks → leads → conversions(revenue)

CREATE TABLE cta_links (
    id              BIGSERIAL PRIMARY KEY,
    post_id         BIGINT       NOT NULL REFERENCES posts (id),
    user_id         BIGINT       NOT NULL REFERENCES users (id),
    slug            VARCHAR(16)  NOT NULL UNIQUE,
    destination_url VARCHAR(2048) NOT NULL,
    label           VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_cta_links_post ON cta_links (post_id);
CREATE INDEX idx_cta_links_user ON cta_links (user_id);

CREATE TABLE link_clicks (
    id          BIGSERIAL PRIMARY KEY,
    cta_link_id BIGINT       NOT NULL REFERENCES cta_links (id),
    post_id     BIGINT       NOT NULL,
    clicked_at  TIMESTAMPTZ  NOT NULL,
    referrer    VARCHAR(1024),
    ua          VARCHAR(512),
    ip_hash     VARCHAR(64)
);
CREATE INDEX idx_link_clicks_post ON link_clicks (post_id);
CREATE INDEX idx_link_clicks_cta ON link_clicks (cta_link_id);

CREATE TABLE leads (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    post_id     BIGINT,
    cta_link_id BIGINT       REFERENCES cta_links (id),
    email       VARCHAR(320) NOT NULL,
    name        VARCHAR(255),
    source      VARCHAR(60),
    status      VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_leads_post ON leads (post_id);
CREATE INDEX idx_leads_user ON leads (user_id);

CREATE TABLE conversions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id),
    post_id     BIGINT,
    lead_id     BIGINT        REFERENCES leads (id),
    cta_link_id BIGINT        REFERENCES cta_links (id),
    amount      NUMERIC(14,2) NOT NULL,
    currency    VARCHAR(8)    NOT NULL DEFAULT 'KRW',
    occurred_at TIMESTAMPTZ   NOT NULL,
    note        VARCHAR(1000),
    source      VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_conversions_post ON conversions (post_id);
CREATE INDEX idx_conversions_user ON conversions (user_id);
