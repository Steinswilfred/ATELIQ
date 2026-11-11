package com.clothingplatform.admin;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.common.Sql;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.notification.EmailSender;
import com.clothingplatform.notification.NotificationService;
import com.clothingplatform.security.CurrentUser;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TailorApplicationService {
    public record ApplicationRow(long id, long userId, String fullName, String email, String phone, int experienceYears,
                                 String preferredCategory, String status, boolean emailVerified, boolean phoneVerified, Instant createdAt) {}
    public record ReviewEntry(String decision, String reviewer, String reason, String notes, Instant at) {}
    public record ApplicationDetail(ApplicationRow summary, String address, String previousExperience, String skills,
                                    String approvedCategory, String rejectionReason, List<ReviewEntry> history) {}

    private static final Set<String> STATUSES = Set.of("PENDING_EMAIL_VERIFICATION", "PENDING_PHONE_VERIFICATION", "PENDING_APPROVAL", "APPROVED", "REJECTED");

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final NotificationService notifications;
    private final EmailSender email;

    public TailorApplicationService(JdbcTemplate jdbc, AuditService audit, NotificationService notifications, EmailSender email) {
        this.jdbc = jdbc; this.audit = audit; this.notifications = notifications; this.email = email;
    }

    private static final String SELECT = """
        SELECT a.id, a.user_id, u.full_name, u.email, u.phone, a.experience_years, a.preferred_category, a.status,
               u.email_verified, u.phone_verified, a.created_at
        FROM tailor_applications a JOIN users u ON u.id = a.user_id""";

    private static ApplicationRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ApplicationRow(rs.getLong("id"), rs.getLong("user_id"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
                rs.getInt("experience_years"), rs.getString("preferred_category"), rs.getString("status"),
                rs.getBoolean("email_verified"), rs.getBoolean("phone_verified"), Sql.ts(rs, "created_at"));
    }

    public PageResult<ApplicationRow> list(String status, String q, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE u.deleted_at IS NULL");
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            if (!STATUSES.contains(status)) throw bad("Unknown status.");
            where.append(" AND a.status = ?"); args.add(status);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (u.full_name ILIKE ? OR u.email ILIKE ? OR u.phone ILIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        int p = Sql.clampPage(page), s = Sql.clampSize(size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM tailor_applications a JOIN users u ON u.id = a.user_id" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(s); pageArgs.add(p * s);
        List<ApplicationRow> items = jdbc.query(SELECT + where + " ORDER BY a.created_at DESC LIMIT ? OFFSET ?", (rs, i) -> row(rs), pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, p, s);
    }

    public ApplicationDetail detail(long id) {
        List<ApplicationRow> rows = jdbc.query(SELECT + " WHERE a.id = ?", (rs, i) -> row(rs), id);
        if (rows.isEmpty()) throw notFound();
        Map<String, Object> extra = jdbc.queryForMap("SELECT address, previous_experience, skills, approved_category, rejection_reason FROM tailor_applications WHERE id = ?", id);
        List<ReviewEntry> history = jdbc.query("""
            SELECT r.decision, u.full_name, r.reason, r.notes, r.created_at FROM tailor_application_reviews r
            JOIN users u ON u.id = r.reviewer_id WHERE r.application_id = ? ORDER BY r.created_at DESC""",
            (rs, i) -> new ReviewEntry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), Sql.ts(rs, "created_at")), id);
        return new ApplicationDetail(rows.get(0), (String) extra.get("address"), (String) extra.get("previous_experience"), (String) extra.get("skills"),
                (String) extra.get("approved_category"), (String) extra.get("rejection_reason"), history);
    }

    /**
     * Approval: application and account become APPROVED, the TAILOR role is granted, the tailor is told by email and in-app.
     * Initial availability is configured when tailor profiles arrive in Phase 7.
     */
    @Transactional
    public void approve(long id, Long categoryId, String notes) {
        if (categoryId == null) throw bad("Choose the tailor category to assign.");
        List<String> cat = jdbc.queryForList("SELECT name FROM tailor_categories WHERE id = ? AND deleted_at IS NULL AND is_active", String.class, categoryId);
        if (cat.isEmpty()) throw bad("That tailor category does not exist or is not active.");
        Applicant a = lockPending(id);
        long actor = CurrentUser.require().getId();
        jdbc.update("UPDATE tailor_applications SET status='APPROVED', approved_category=?, approved_category_id=?, reviewed_by=?, reviewed_at=now(), rejection_reason=NULL, updated_at=now() WHERE id=?", cat.get(0), categoryId, actor, id);
        jdbc.update("UPDATE users SET account_status='APPROVED', status_reason=NULL, updated_at=now() WHERE id=?", a.userId);
        jdbc.update("INSERT INTO user_roles (user_id, role_id, assigned_by) SELECT ?, id, ? FROM roles WHERE code='TAILOR' ON CONFLICT DO NOTHING", a.userId, actor);
        // profile + initial availability (defaults: AVAILABLE, 5 active orders; the admin can change both on the tailor page)
        jdbc.update("""
            INSERT INTO tailor_profiles (user_id, category_id, experience_years, skills, address)
            SELECT user_id, ?, experience_years, skills, address FROM tailor_applications WHERE id = ?
            ON CONFLICT (user_id) DO UPDATE SET category_id = EXCLUDED.category_id, updated_at = now()""", categoryId, id);
        jdbc.update("INSERT INTO tailor_availability (tailor_id) SELECT id FROM tailor_profiles WHERE user_id = ? ON CONFLICT DO NOTHING", a.userId);
        review(id, actor, "APPROVED", "Category: " + cat.get(0), notes);
        audit.audit(actor, "TAILOR_APPROVED", "TAILOR_APPLICATION", id, "PENDING_APPROVAL", "APPROVED", "category " + cat.get(0));
        notifications.notifyUser(a.userId, "TAILOR_APPROVED", "Your tailor application is approved", "You are in the " + cat.get(0) + " category. Sign in to open your tailor dashboard.", "/tailor");
        email.send(a.email, "Your tailor application is approved", "Hi " + a.name + ",\n\nYour application has been approved and you have been placed in the " + cat.get(0) + " category. You can now sign in and open your tailor dashboard.");
    }

    @Transactional
    public void reject(long id, String reason, String notes) {
        if (reason == null || reason.trim().length() < 5) throw bad("Give the applicant a reason (at least 5 characters).");
        if (reason.trim().length() > 500) throw bad("Keep the reason under 500 characters.");
        Applicant a = lockPending(id);
        long actor = CurrentUser.require().getId();
        jdbc.update("UPDATE tailor_applications SET status='REJECTED', reviewed_by=?, reviewed_at=now(), rejection_reason=?, updated_at=now() WHERE id=?", actor, reason.trim(), id);
        jdbc.update("UPDATE users SET account_status='REJECTED', status_reason=?, updated_at=now() WHERE id=?", reason.trim(), a.userId);
        review(id, actor, "REJECTED", reason.trim(), notes);
        audit.audit(actor, "TAILOR_REJECTED", "TAILOR_APPLICATION", id, "PENDING_APPROVAL", "REJECTED", reason);
        notifications.notifyUser(a.userId, "TAILOR_REJECTED", "Your tailor application was not approved", reason.trim(), null);
        email.send(a.email, "Your tailor application", "Hi " + a.name + ",\n\nYour application has not been approved.\nReason: " + reason.trim() + "\n\nContact support if you have questions.");
    }

    @Transactional
    public void requestInfo(long id, String message) {
        if (message == null || message.trim().length() < 5) throw bad("Write what information you need.");
        Applicant a = lockPending(id);
        long actor = CurrentUser.require().getId();
        review(id, actor, "INFO_REQUESTED", message.trim(), null);
        audit.audit(actor, "TAILOR_INFO_REQUESTED", "TAILOR_APPLICATION", id, null, null, message);
        notifications.notifyUser(a.userId, "TAILOR_INFO_REQUESTED", "More information needed for your application", message.trim(), null);
        email.send(a.email, "More information needed", "Hi " + a.name + ",\n\nWe need more information to review your application:\n" + message.trim() + "\n\nReply to this email or contact support.");
    }

    @Transactional
    public void addNote(long id, String notes) {
        if (notes == null || notes.isBlank()) throw bad("Write a note.");
        lockExisting(id);
        review(id, CurrentUser.require().getId(), "NOTE", null, notes.trim());
    }

    // ----- helpers -----

    private record Applicant(long userId, String name, String email, String status) {}

    private Applicant lockExisting(long id) {
        List<Applicant> l = jdbc.query("""
            SELECT a.user_id, u.full_name, u.email, a.status FROM tailor_applications a JOIN users u ON u.id = a.user_id
            WHERE a.id = ? FOR UPDATE OF a""", (rs, i) -> new Applicant(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)), id);
        if (l.isEmpty()) throw notFound();
        return l.get(0);
    }

    /** Row-locks the application so two admins cannot decide it at the same time. */
    private Applicant lockPending(long id) {
        Applicant a = lockExisting(id);
        if (!"PENDING_APPROVAL".equals(a.status))
            throw new BusinessException("INVALID_APPLICATION_STATE", "This application is not waiting for review (status: " + a.status + ").", HttpStatus.CONFLICT);
        return a;
    }

    private void review(long appId, long reviewer, String decision, String reason, String notes) {
        jdbc.update("INSERT INTO tailor_application_reviews (application_id, reviewer_id, decision, reason, notes) VALUES (?,?,?,?,?)", appId, reviewer, decision, reason, notes);
    }

    private BusinessException bad(String m) { return new BusinessException("BAD_REQUEST", m, HttpStatus.BAD_REQUEST); }
    private BusinessException notFound() { return new BusinessException("APPLICATION_NOT_FOUND", "Application not found.", HttpStatus.NOT_FOUND); }
}
