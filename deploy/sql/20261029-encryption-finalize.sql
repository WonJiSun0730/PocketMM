-- MANUAL ONLY: only after --verify returns zero plaintext and all rows authenticate.
-- Confirm this query returns 0 before applying the constraints:
SELECT COUNT(*) AS missing_hashes FROM protected_memory_detail WHERE detail_hash IS NULL;
ALTER TABLE protected_memory_detail MODIFY COLUMN detail_hash VARCHAR(64) NOT NULL;
ALTER TABLE protected_memory_detail ADD CONSTRAINT uk_protected_memory_detail UNIQUE(memory_id, detail_hash);
