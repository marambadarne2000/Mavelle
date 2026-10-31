-- Existing announcements retain their purchased-customer audience.
ALTER TABLE store_offers ADD COLUMN public_advertisement BOOLEAN NOT NULL DEFAULT FALSE;
