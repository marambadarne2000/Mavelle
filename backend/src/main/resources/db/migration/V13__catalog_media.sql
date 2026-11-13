CREATE TABLE media_assets (
 id UUID PRIMARY KEY,
 owner_id BIGINT NOT NULL REFERENCES users(id),
 content BYTEA NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE products ADD COLUMN image_id UUID REFERENCES media_assets(id);
ALTER TABLE companies ADD COLUMN logo_id UUID REFERENCES media_assets(id);
