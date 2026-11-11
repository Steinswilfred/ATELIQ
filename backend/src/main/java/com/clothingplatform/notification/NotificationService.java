package com.clothingplatform.notification;

import com.clothingplatform.common.Sql;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** In-app notifications. Real-time push over WebSocket is added in Phase 13 on top of this table. */
@Service
public class NotificationService {
    public record NotificationView(long id, String type, String title, String body, String link, boolean read, java.time.Instant createdAt) {}

    private final JdbcTemplate jdbc;
    public NotificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void notifyUser(long userId, String type, String title, String body, String link) {
        jdbc.update("INSERT INTO notifications (user_id, type, title, body, link) VALUES (?,?,?,?,?)", userId, type, title, body, link);
    }

    /** Notifies the Super Admin(s) and every active admin whose roles carry the permission. */
    public void notifyPermissionHolders(String permission, String type, String title, String body, String link) {
        List<Long> ids = jdbc.queryForList("""
            SELECT u.id FROM users u
            WHERE u.deleted_at IS NULL AND u.account_status = 'ACTIVE'
              AND (u.user_type = 'SUPER_ADMIN'
                   OR (u.user_type = 'ADMIN' AND EXISTS (
                        SELECT 1 FROM user_roles ur
                        JOIN role_permissions rp ON rp.role_id = ur.role_id AND rp.enabled
                        JOIN permissions p ON p.id = rp.permission_id
                        WHERE ur.user_id = u.id AND p.code = ?)))
            """, Long.class, permission);
        for (long id : ids) notifyUser(id, type, title, body, link);
    }

    public Map<String, Object> list(long userId) {
        List<NotificationView> items = jdbc.query(
                "SELECT id, type, title, body, link, read_at, created_at FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 50",
                (rs, i) -> new NotificationView(rs.getLong("id"), rs.getString("type"), rs.getString("title"), rs.getString("body"),
                        rs.getString("link"), rs.getTimestamp("read_at") != null, Sql.ts(rs, "created_at")), userId);
        Integer unread = jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND read_at IS NULL", Integer.class, userId);
        return Map.of("items", items, "unread", unread == null ? 0 : unread);
    }

    public void markRead(long userId, long id) {
        jdbc.update("UPDATE notifications SET read_at = now() WHERE id = ? AND user_id = ? AND read_at IS NULL", id, userId);
    }

    public void markAllRead(long userId) {
        jdbc.update("UPDATE notifications SET read_at = now() WHERE user_id = ? AND read_at IS NULL", userId);
    }
}
