package com.clothingplatform.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Single place that writes login, activity and audit records. */
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public void login(Long userId, String email, boolean success, String reason, String ip, String ua) {
        jdbc.update("INSERT INTO login_logs (user_id, email, success, failure_reason, ip_address, user_agent) VALUES (?,?,?,?,?,?)",
                userId, email, success, reason, ip, ua == null ? null : ua.substring(0, Math.min(300, ua.length())));
    }

    public void activity(Long userId, String action, String entityType, Object entityId, String ip) {
        jdbc.update("INSERT INTO activity_logs (user_id, action, entity_type, entity_id, ip_address) VALUES (?,?,?,?,?)",
                userId, action, entityType, entityId == null ? null : String.valueOf(entityId), ip);
    }

    public void audit(Long actorId, String action, String entityType, Object entityId, Object oldValue, Object newValue, String reason) {
        jdbc.update("INSERT INTO audit_logs (actor_id, action, entity_type, entity_id, old_value, new_value, reason) VALUES (?,?,?,?,?::jsonb,?::jsonb,?)",
                actorId, action, entityType, String.valueOf(entityId), json(oldValue), json(newValue), reason);
    }

    private String json(Object o) {
        if (o == null) return null;
        try { return mapper.writeValueAsString(o); } catch (JsonProcessingException e) { return null; }
    }
}
