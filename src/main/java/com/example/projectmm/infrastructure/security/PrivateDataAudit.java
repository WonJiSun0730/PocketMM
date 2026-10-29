package com.example.projectmm.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Independent durable transaction. An unavailable audit store blocks disclosure. No content or keys. */
@Service
public class PrivateDataAudit {
    private final JdbcTemplate jdbc;
    public PrivateDataAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String requestId, String actor, String action, String target, long ownerId,
                       long scopeId, String ids, String reason, String outcome, int count) {
        jdbc.update("insert into private_data_audit (request_id, actor, action, target, owner_id, scope_id, target_ids, reason, outcome, result_count, occurred_at) values (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                requestId, actor, action, target, ownerId, scopeId, ids, reason, outcome, count);
    }
}
