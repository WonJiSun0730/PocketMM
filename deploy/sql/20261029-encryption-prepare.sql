-- MANUAL ONLY. Stop writers and verify a restorable backup first. See docs/data-encryption.md.
-- MySQL/MariaDB DDL implicitly commits. Rehearse on a restored copy. Never auto-run on startup.
-- Retain a supporting memory_id index before dropping the old text uniqueness index.
CREATE INDEX ix_protected_detail_memory ON protected_memory_detail(memory_id);
ALTER TABLE protected_memory_detail DROP INDEX uk_protected_memory_detail;
ALTER TABLE protected_memory_detail ADD COLUMN detail_hash VARCHAR(64) NULL;
ALTER TABLE chat_messages MODIFY COLUMN content LONGTEXT NOT NULL;
ALTER TABLE world_room_messages MODIFY COLUMN content LONGTEXT NOT NULL;
ALTER TABLE world_room_message_variants MODIFY COLUMN content LONGTEXT NOT NULL;
ALTER TABLE world_room_message_variants MODIFY COLUMN before_player_state_json LONGTEXT NULL;
ALTER TABLE world_room_message_variants MODIFY COLUMN after_player_state_json LONGTEXT NULL;
ALTER TABLE world_room_message_variants MODIFY COLUMN user_scene_setup_json LONGTEXT NULL;
ALTER TABLE world_room_message_variants MODIFY COLUMN before_scene_facts_json LONGTEXT NULL;
ALTER TABLE world_room_message_variants MODIFY COLUMN after_scene_facts_json LONGTEXT NULL;
ALTER TABLE memories MODIFY COLUMN summary LONGTEXT NOT NULL;
ALTER TABLE memories MODIFY COLUMN evidence_quote LONGTEXT NULL;
ALTER TABLE memories MODIFY COLUMN fact_subject LONGTEXT NULL;
ALTER TABLE memories MODIFY COLUMN fact_relation LONGTEXT NULL;
ALTER TABLE memories MODIFY COLUMN fact_value LONGTEXT NULL;
ALTER TABLE memories MODIFY COLUMN semantic_subject LONGTEXT NULL;
ALTER TABLE memories MODIFY COLUMN semantic_relation LONGTEXT NULL;
ALTER TABLE memories MODIFY COLUMN semantic_object LONGTEXT NULL;
ALTER TABLE protected_memory_detail MODIFY COLUMN detail_text LONGTEXT NOT NULL;
ALTER TABLE memory_embeddings MODIFY COLUMN embedding LONGTEXT NOT NULL;
ALTER TABLE pocket_activity_reports MODIFY COLUMN activities_json LONGTEXT NOT NULL;
ALTER TABLE pocket_activity_reports MODIFY COLUMN narrative_context_json LONGTEXT NULL;
ALTER TABLE pocket_activity_reports MODIFY COLUMN narrative_text LONGTEXT NULL;
ALTER TABLE world_room_sessions MODIFY COLUMN intro_title_snapshot LONGTEXT NULL;
ALTER TABLE world_room_sessions MODIFY COLUMN intro_content_snapshot LONGTEXT NULL;
ALTER TABLE world_room_sessions MODIFY COLUMN initial_location LONGTEXT NULL;
ALTER TABLE world_room_sessions MODIFY COLUMN user_note LONGTEXT NULL;
ALTER TABLE world_room_sessions MODIFY COLUMN pocket_play_guide LONGTEXT NULL;
ALTER TABLE world_room_session_initial_conditions MODIFY COLUMN condition_text LONGTEXT NULL;
ALTER TABLE world_room_session_initial_inventory MODIFY COLUMN item_text LONGTEXT NULL;
ALTER TABLE world_room_player_states MODIFY COLUMN location LONGTEXT NULL;
ALTER TABLE world_room_player_conditions MODIFY COLUMN condition_text LONGTEXT NOT NULL;
ALTER TABLE world_room_player_inventory MODIFY COLUMN item_text LONGTEXT NOT NULL;

CREATE TABLE private_data_audit (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  request_id VARCHAR(36) NOT NULL, actor VARCHAR(100) NOT NULL,
  action VARCHAR(16) NOT NULL, target VARCHAR(32) NOT NULL,
  owner_id BIGINT NOT NULL, scope_id BIGINT NOT NULL, target_ids VARCHAR(500) NOT NULL,
  reason VARCHAR(500) NOT NULL, outcome VARCHAR(16) NOT NULL, result_count INT NOT NULL,
  occurred_at TIMESTAMP(6) NOT NULL,
  INDEX ix_private_audit_request (request_id), INDEX ix_private_audit_time (occurred_at)
);
