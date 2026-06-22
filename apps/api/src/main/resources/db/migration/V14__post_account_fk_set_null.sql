-- Allow disconnecting a Threads account even if posts referenced it (post falls back to default).
ALTER TABLE posts DROP CONSTRAINT IF EXISTS posts_social_account_id_fkey;
ALTER TABLE posts ADD CONSTRAINT posts_social_account_id_fkey
    FOREIGN KEY (social_account_id) REFERENCES social_accounts (id) ON DELETE SET NULL;
