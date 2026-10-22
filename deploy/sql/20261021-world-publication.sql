-- MariaDB additive migration. Existing worlds remain unpublished.
ALTER TABLE worlds ADD COLUMN IF NOT EXISTS hashtags TEXT NULL;
ALTER TABLE worlds ADD COLUMN IF NOT EXISTS published_at DATETIME(6) NULL;
