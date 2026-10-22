-- MariaDB: existing worlds keep NULL until a cover is uploaded.
-- Additive and safe to re-run; does not store image bytes in the database.
ALTER TABLE worlds ADD COLUMN IF NOT EXISTS cover_image_url VARCHAR(2048) NULL;
