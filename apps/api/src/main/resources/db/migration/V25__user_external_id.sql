-- SSO(통합계정) 신원 연결: external_id(토큰 sub). 크레덴셜은 SSO에만, 여긴 로컬 프로필만.
ALTER TABLE users ADD COLUMN external_id VARCHAR(64);
-- 기존 dev/데모 유저(id=1)를 SSO 데모 계정(external_id=demo-user)에 연결 → 데모 로그인 시 기존 시드가 보이게.
UPDATE users SET external_id = 'demo-user' WHERE id = 1;
CREATE UNIQUE INDEX ux_users_external_id ON users (external_id);
