-- 멀티플랫폼 발행(Phase 0): social_accounts를 provider 무관 범용으로.
-- external_id = 범용 계정 id(Threads=threads_user_id, Bluesky=DID). handle = Bluesky 핸들.
-- refresh_token = Bluesky refreshJwt(회전형). Threads는 in-place 갱신이라 null.
ALTER TABLE social_accounts ADD COLUMN external_id VARCHAR(255);
ALTER TABLE social_accounts ADD COLUMN handle VARCHAR(255);
ALTER TABLE social_accounts ADD COLUMN refresh_token VARCHAR(1024);

-- 기존 Threads 계정 백필: external_id = threads_user_id.
UPDATE social_accounts SET external_id = threads_user_id WHERE external_id IS NULL;

-- 채널 유니크: (user_id, provider, external_id). external_id NULL은 Postgres에서 서로 distinct라 허용.
CREATE UNIQUE INDEX ux_social_accounts_user_provider_external
    ON social_accounts (user_id, provider, external_id);
