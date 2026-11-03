CREATE TABLE account_mfa (
 user_id BIGINT PRIMARY KEY REFERENCES users(id),
 secret_cipher TEXT,
 pending_cipher TEXT,
 pending_expires TIMESTAMPTZ,
 enabled BOOLEAN NOT NULL DEFAULT false,
 last_step BIGINT NOT NULL DEFAULT -1
);
CREATE TABLE mfa_recovery_codes (
 user_id BIGINT NOT NULL REFERENCES users(id),
 code_hash VARCHAR(64) NOT NULL,
 used_at TIMESTAMPTZ,
 PRIMARY KEY(user_id,code_hash)
);
