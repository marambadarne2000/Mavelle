-- Manufacturer identity is distinct from merchant membership and demo commerce data.
ALTER TABLE companies ADD COLUMN source_url TEXT;
ALTER TABLE products ADD COLUMN source_url TEXT;
ALTER TABLE products ADD COLUMN reference_image_url TEXT;
ALTER TABLE products ADD COLUMN specifications JSONB NOT NULL DEFAULT '{}';
ALTER TABLE products ADD COLUMN source_checked_on DATE;
