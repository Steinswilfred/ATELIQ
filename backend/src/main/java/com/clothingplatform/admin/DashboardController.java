package com.clothingplatform.admin;

import com.clothingplatform.common.ApiResponse;
import com.clothingplatform.common.Sql;
import com.clothingplatform.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Only real figures: order, product and revenue metrics are added when those modules exist (Phases 4, 8, 12). */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class DashboardController {
    public record Recent(String action, String actor, Instant at) {}
    public record Stats(long customers, long tailors, long activeTailors, long pendingTailorApplications, long blockedAccounts, List<Recent> recent) {}

    private final JdbcTemplate jdbc;
    public DashboardController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public ApiResponse<Stats> stats() {
        long customers = count("SELECT count(*) FROM users WHERE user_type='CUSTOMER' AND deleted_at IS NULL");
        long tailors = count("SELECT count(*) FROM users WHERE user_type='TAILOR' AND deleted_at IS NULL");
        long active = count("SELECT count(*) FROM users WHERE user_type='TAILOR' AND account_status='APPROVED' AND deleted_at IS NULL");
        long pending = count("SELECT count(*) FROM tailor_applications WHERE status='PENDING_APPROVAL'");
        long blocked = count("SELECT count(*) FROM users WHERE account_status IN ('BLOCKED','SUSPENDED') AND deleted_at IS NULL");
        var me = CurrentUser.require();
        List<Recent> recent = (me.isSuperAdmin() || me.hasPermission("LOG_VIEW")) ? jdbc.query("""
            SELECT action, name, created_at FROM (
              SELECT l.action, u.full_name AS name, l.created_at FROM activity_logs l LEFT JOIN users u ON u.id = l.user_id
              UNION ALL
              SELECT l.action, u.full_name, l.created_at FROM audit_logs l LEFT JOIN users u ON u.id = l.actor_id) x
            ORDER BY created_at DESC LIMIT 10""", (rs, i) -> new Recent(rs.getString(1), rs.getString(2), Sql.ts(rs, "created_at"))) : List.of();
        return ApiResponse.ok(new Stats(customers, tailors, active, pending, blocked, recent));
    }

    private long count(String sql) { Long n = jdbc.queryForObject(sql, Long.class); return n == null ? 0 : n; }
}
