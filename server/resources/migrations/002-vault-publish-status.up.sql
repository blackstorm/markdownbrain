-- Add publish status snapshot fields to vaults
-- Tracks the last publish attempt result for Console visibility (MVP)

ALTER TABLE vaults ADD COLUMN last_publish_at TIMESTAMP;
--;;

ALTER TABLE vaults ADD COLUMN last_publish_status TEXT NOT NULL DEFAULT 'never';
--;;

ALTER TABLE vaults ADD COLUMN last_publish_error_code TEXT;
--;;

ALTER TABLE vaults ADD COLUMN last_publish_error_message TEXT;
