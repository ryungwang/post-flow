-- Comment automation: auto-reply to keyword comments with a tracking link
CREATE TABLE comment_rules (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    post_id        BIGINT       REFERENCES posts (id),      -- null = all published posts
    keyword        VARCHAR(100) NOT NULL,
    reply_template VARCHAR(500) NOT NULL,
    cta_link_id    BIGINT       REFERENCES cta_links (id),  -- {link} placeholder source
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_comment_rules_user ON comment_rules (user_id);
CREATE INDEX idx_comment_rules_active ON comment_rules (active);

CREATE TABLE comment_replies (
    id               BIGSERIAL PRIMARY KEY,
    rule_id          BIGINT       NOT NULL REFERENCES comment_rules (id),
    post_id          BIGINT,
    threads_reply_id VARCHAR(64)  NOT NULL,
    replied_at       TIMESTAMPTZ  NOT NULL,
    UNIQUE (rule_id, threads_reply_id)
);
CREATE INDEX idx_comment_replies_rule ON comment_replies (rule_id);
