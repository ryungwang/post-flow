ALTER TABLE posts ADD COLUMN social_account_id BIGINT REFERENCES social_accounts (id);
