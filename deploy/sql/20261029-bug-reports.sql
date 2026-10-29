-- MANUAL ONLY. Verify backup/restore first. Apply before the updated encryption migration CLI.
-- Requires private_data_audit from 20261029-encryption-prepare.sql for operator access.
CREATE TABLE bug_reports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    owner_member_id BIGINT NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    session_id BIGINT NOT NULL,
    message_id BIGINT NULL,
    variant_no INT NULL,
    category VARCHAR(30) NOT NULL,
    status VARCHAR(16) NOT NULL,
    problem LONGTEXT NOT NULL,
    expected LONGTEXT NOT NULL,
    evidence_json LONGTEXT NULL,
    shared_context BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    updated_by_member_id BIGINT NULL,
    CONSTRAINT uk_bug_report_request UNIQUE(owner_member_id,request_id),
    INDEX ix_bug_report_status(status,id),
    INDEX ix_bug_report_owner_created(owner_member_id,created_at)
) ENGINE=InnoDB;
