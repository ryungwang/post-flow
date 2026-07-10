-- Mastodon는 인스턴스별 API 호스트가 다르다(연합). 계정마다 인스턴스 base URL을 저장한다.
-- 다른 프로바이더는 NULL(고정 호스트).
ALTER TABLE social_accounts ADD COLUMN instance_url VARCHAR(255);
