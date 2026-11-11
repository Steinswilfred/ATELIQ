package com.clothingplatform.tailor;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.PageResult;
import com.clothingplatform.common.Sql;
import com.clothingplatform.common.Urls;
import com.clothingplatform.security.CurrentUser;
import com.clothingplatform.user.AccountStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tailor categories (admin-priced skill levels), tailor profiles and availability.
 * Order counts are zero until the order modules exist (Phases 8-11); the dashboard says so instead of inventing numbers.
 */
@Service
public class TailorService {
    private static final Set<String> AVAILABILITY = Set.of("AVAILABLE", "BUSY", "ON_LEAVE");

    public record CategoryView(long id, String name, String description, int level, BigDecimal stitchingPrice, boolean active, int tailorCount) {}
    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 500) String description, @NotNull @Min(1) @Max(100) Integer level,
                                  @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal stitchingPrice, Boolean active) {}

    public record TailorRow(long userId, long profileId, String fullName, String email, String phone, String accountStatus, long categoryId, String categoryName,
                            BigDecimal stitchingPrice, int experienceYears, BigDecimal ratingAvg, int ratingCount, String availability, int maxActiveOrders) {}
    public record TailorDetail(TailorRow tailor, String bio, String skills, String address, String photoUrl, String availabilityNote, LocalDate availableAgainOn, Long applicationId) {}
    public record AvailabilityRequest(@NotBlank String status, @Min(1) @Max(50) Integer maxActiveOrders, @Size(max = 300) String note, LocalDate availableAgainOn) {}
    public record ProfileUpdate(@Size(max = 2000) String bio, @Size(max = 2000) String skills, @Size(max = 500) String address, String photoUrl) {}
    public record Dashboard(TailorDetail profile, int activeOrders, int completedOrders, int awaitingFabricApproval, boolean ordersModuleReady) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public TailorService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }
    private long actor() { return CurrentUser.require().getId(); }

    // ---------- categories ----------
    public List<CategoryView> categories() {
        return jdbc.query("""
            SELECT c.id, c.name, c.description, c.level, c.stitching_price, c.is_active, (SELECT count(*) FROM tailor_profiles p WHERE p.category_id = c.id) AS n
            FROM tailor_categories c WHERE c.deleted_at IS NULL ORDER BY c.level, c.name""",
            (rs, i) -> new CategoryView(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getInt("level"), rs.getBigDecimal("stitching_price"), rs.getBoolean("is_active"), rs.getInt("n")));
    }

    @Transactional
    public long createCategory(CategoryRequest r) {
        long id = jdbc.queryForObject("INSERT INTO tailor_categories (name, description, level, stitching_price, is_active) VALUES (?,?,?,?,?) RETURNING id", Long.class,
                r.name().trim(), r.description(), r.level(), r.stitchingPrice(), r.active() == null || r.active());
        audit.audit(actor(), "TAILOR_CATEGORY_CREATED", "TAILOR_CATEGORY", id, null, r, null);
        return id;
    }

    /** Price changes affect new quotes only; existing orders keep the price they were placed with (Phase 8 snapshots it). */
    @Transactional
    public void updateCategory(long id, CategoryRequest r) {
        BigDecimal before = jdbc.query("SELECT stitching_price FROM tailor_categories WHERE id = ? AND deleted_at IS NULL", rs -> rs.next() ? rs.getBigDecimal(1) : null, id);
        if (before == null) throw notFound("Tailor category");
        jdbc.update("UPDATE tailor_categories SET name=?, description=?, level=?, stitching_price=?, is_active=?, updated_at=now() WHERE id=?",
                r.name().trim(), r.description(), r.level(), r.stitchingPrice(), r.active() == null || r.active(), id);
        audit.audit(actor(), "TAILOR_CATEGORY_UPDATED", "TAILOR_CATEGORY", id, Map.of("stitchingPrice", before), r, null);
    }

    @Transactional
    public void deleteCategory(long id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM tailor_profiles WHERE category_id = ?", Integer.class, id);
        if (n != null && n > 0) throw conflict("Tailors are assigned to this category. Move them first, or hide the category instead.");
        if (jdbc.update("UPDATE tailor_categories SET deleted_at = now(), updated_at = now() WHERE id = ? AND deleted_at IS NULL", id) == 0) throw notFound("Tailor category");
        audit.audit(actor(), "TAILOR_CATEGORY_DELETED", "TAILOR_CATEGORY", id, null, null, null);
    }

    // ---------- admin: tailors ----------
    private static final String ROW_SELECT = """
        SELECT u.id AS uid, p.id AS pid, u.full_name, u.email, u.phone, u.account_status, p.category_id, c.name AS cat, c.stitching_price,
               p.experience_years, p.rating_avg, p.rating_count, a.status AS avail, a.max_active_orders
        FROM tailor_profiles p JOIN users u ON u.id = p.user_id JOIN tailor_categories c ON c.id = p.category_id JOIN tailor_availability a ON a.tailor_id = p.id
        WHERE u.deleted_at IS NULL""";

    private static TailorRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TailorRow(rs.getLong("uid"), rs.getLong("pid"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"), rs.getString("account_status"),
                rs.getLong("category_id"), rs.getString("cat"), rs.getBigDecimal("stitching_price"), rs.getInt("experience_years"), rs.getBigDecimal("rating_avg"),
                rs.getInt("rating_count"), rs.getString("avail"), rs.getInt("max_active_orders"));
    }

    public PageResult<TailorRow> list(Long categoryId, String availability, String status, String q, int page, int size) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        if (categoryId != null) { where.append(" AND p.category_id = ?"); args.add(categoryId); }
        if (availability != null && !availability.isBlank()) { if (!AVAILABILITY.contains(availability)) throw bad("Unknown availability."); where.append(" AND a.status = ?"); args.add(availability); }
        if (status != null && !status.isBlank()) { try { AccountStatus.valueOf(status); } catch (IllegalArgumentException e) { throw bad("Unknown status."); } where.append(" AND u.account_status = ?"); args.add(status); }
        if (q != null && !q.isBlank()) { where.append(" AND (u.full_name ILIKE ? OR u.email ILIKE ? OR u.phone ILIKE ?)"); String like = "%" + q.trim() + "%"; args.add(like); args.add(like); args.add(like); }
        int pg = Sql.clampPage(page), sz = Sql.clampSize(size);
        Long total = jdbc.queryForObject("SELECT count(*) FROM tailor_profiles p JOIN users u ON u.id = p.user_id JOIN tailor_availability a ON a.tailor_id = p.id WHERE u.deleted_at IS NULL" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args); pageArgs.add(sz); pageArgs.add(pg * sz);
        List<TailorRow> items = jdbc.query(ROW_SELECT + where + " ORDER BY u.full_name LIMIT ? OFFSET ?", (rs, i) -> row(rs), pageArgs.toArray());
        return new PageResult<>(items, total == null ? 0 : total, pg, sz);
    }

    public TailorDetail detail(long userId) {
        List<TailorRow> rows = jdbc.query(ROW_SELECT + " AND u.id = ?", (rs, i) -> row(rs), userId);
        if (rows.isEmpty()) throw notFound("Tailor");
        Map<String, Object> x = jdbc.queryForMap("""
            SELECT p.bio, p.skills, p.address, p.photo_url, a.note, a.available_again_on FROM tailor_profiles p JOIN tailor_availability a ON a.tailor_id = p.id WHERE p.user_id = ?""", userId);
        List<Long> app = jdbc.queryForList("SELECT id FROM tailor_applications WHERE user_id = ? ORDER BY id DESC LIMIT 1", Long.class, userId);
        java.sql.Date d = (java.sql.Date) x.get("available_again_on");
        return new TailorDetail(rows.get(0), (String) x.get("bio"), (String) x.get("skills"), (String) x.get("address"), (String) x.get("photo_url"), (String) x.get("note"),
                d == null ? null : d.toLocalDate(), app.isEmpty() ? null : app.get(0));
    }

    @Transactional
    public void setCategory(long userId, long categoryId) {
        Integer ok = jdbc.queryForObject("SELECT count(*) FROM tailor_categories WHERE id = ? AND deleted_at IS NULL AND is_active", Integer.class, categoryId);
        if (ok == null || ok == 0) throw bad("That tailor category does not exist or is not active.");
        Long before = jdbc.query("SELECT category_id FROM tailor_profiles WHERE user_id = ?", rs -> rs.next() ? rs.getLong(1) : null, userId);
        if (before == null) throw notFound("Tailor");
        jdbc.update("UPDATE tailor_profiles SET category_id = ?, updated_at = now() WHERE user_id = ?", categoryId, userId);
        audit.audit(actor(), "TAILOR_CATEGORY_ASSIGNED", "USER", userId, before, categoryId, null);
    }

    /** Admin sets status and the active-order cap. */
    @Transactional
    public void setAvailabilityAsAdmin(long userId, AvailabilityRequest r) {
        if (r.maxActiveOrders() == null) throw bad("Set the maximum number of active orders.");
        writeAvailability(userId, r, r.maxActiveOrders());
        audit.audit(actor(), "TAILOR_AVAILABILITY_SET", "USER", userId, null, r, null);
    }

    /** Tailors change their own status and note, never the cap the admin set. */
    @Transactional
    public void setAvailabilityAsTailor(long userId, AvailabilityRequest r) { writeAvailability(userId, r, null); }

    private void writeAvailability(long userId, AvailabilityRequest r, Integer cap) {
        if (!AVAILABILITY.contains(r.status())) throw bad("Status must be AVAILABLE, BUSY or ON_LEAVE.");
        if (r.availableAgainOn() != null && r.availableAgainOn().isBefore(LocalDate.now())) throw bad("The return date cannot be in the past.");
        int rows = jdbc.update("""
            UPDATE tailor_availability SET status = ?, note = ?, available_again_on = ?, max_active_orders = COALESCE(?, max_active_orders), updated_at = now()
            WHERE tailor_id = (SELECT id FROM tailor_profiles WHERE user_id = ?)""", r.status(), r.note(), r.availableAgainOn(), cap, userId);
        if (rows == 0) throw notFound("Tailor");
    }

    // ---------- tailor: own profile ----------
    public TailorDetail myProfile(long userId) { return detail(userId); }

    @Transactional
    public void updateMyProfile(long userId, ProfileUpdate r) {
        String photo = r.photoUrl() == null ? null : Urls.clean(r.photoUrl());
        if (jdbc.update("UPDATE tailor_profiles SET bio = ?, skills = ?, address = ?, photo_url = ?, updated_at = now() WHERE user_id = ?", r.bio(), r.skills(), r.address(), photo, userId) == 0) throw notFound("Tailor profile");
    }

    public Dashboard dashboard(long userId) {
        String inactive = com.clothingplatform.order.OrderStatusMachine.INACTIVE_SQL;
        int active = count("SELECT count(*) FROM orders WHERE tailor_id = ? AND status NOT IN " + inactive, userId);
        int awaiting = count("SELECT count(*) FROM orders WHERE tailor_id = ? AND status IN ('FABRIC_SAMPLE_SUBMITTED','CUSTOMER_APPROVAL_PENDING')", userId);
        int completed = count("SELECT count(*) FROM orders WHERE tailor_id = ? AND status = 'DELIVERED'", userId);
        return new Dashboard(detail(userId), active, completed, awaiting, true);
    }

    private int count(String sql, long userId) { Integer n = jdbc.queryForObject(sql, Integer.class, userId); return n == null ? 0 : n; }
}
