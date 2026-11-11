package com.clothingplatform.coupon;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.order.PricingCalculator;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
    public record CouponView(long id, String code, String description, String discountType, BigDecimal value, BigDecimal minOrderAmount, BigDecimal maxDiscount,
                             Instant startsAt, Instant endsAt, Integer usageLimit, Integer perUserLimit, int usedCount, boolean active) {}
    public record CouponRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,40}$", message = "Use 3 to 40 letters, digits, - or _.") String code, @Size(max = 300) String description,
                                @NotBlank @Pattern(regexp = "^(PERCENT|FIXED)$") String discountType, @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal value,
                                @DecimalMin("0") BigDecimal minOrderAmount, @DecimalMin("0.01") BigDecimal maxDiscount, Instant startsAt, Instant endsAt,
                                @Min(1) Integer usageLimit, @Min(1) Integer perUserLimit, Boolean active) {}
    public record Applied(long id, String code, BigDecimal discount) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public CouponService(JdbcTemplate jdbc, AuditService audit) { this.jdbc = jdbc; this.audit = audit; }

    // ---- admin ----
    public List<CouponView> list() {
        return jdbc.query("""
            SELECT id, code, description, discount_type, value, min_order_amount, max_discount, starts_at, ends_at, usage_limit, per_user_limit, used_count, is_active
            FROM coupons WHERE deleted_at IS NULL ORDER BY created_at DESC""", (rs, i) -> new CouponView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5),
                rs.getBigDecimal(6), rs.getBigDecimal(7), ts(rs, 8), ts(rs, 9), (Integer) rs.getObject(10), (Integer) rs.getObject(11), rs.getInt(12), rs.getBoolean(13)));
    }
    private static Instant ts(java.sql.ResultSet rs, int c) throws java.sql.SQLException { var t = rs.getTimestamp(c); return t == null ? null : t.toInstant(); }

    @Transactional
    public long create(CouponRequest r) {
        check(r);
        long id = jdbc.queryForObject("""
            INSERT INTO coupons (code, description, discount_type, value, min_order_amount, max_discount, starts_at, ends_at, usage_limit, per_user_limit, is_active)
            VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING id""", Long.class, r.code().toUpperCase(), r.description(), r.discountType(), r.value(),
                r.minOrderAmount() == null ? BigDecimal.ZERO : r.minOrderAmount(), r.maxDiscount(), r.startsAt(), r.endsAt(), r.usageLimit(), r.perUserLimit(), r.active() == null || r.active());
        audit.audit(CurrentUser.require().getId(), "COUPON_CREATED", "COUPON", id, null, r, null);
        return id;
    }

    @Transactional
    public void update(long id, CouponRequest r) {
        check(r);
        if (jdbc.update("""
            UPDATE coupons SET code=?, description=?, discount_type=?, value=?, min_order_amount=?, max_discount=?, starts_at=?, ends_at=?, usage_limit=?, per_user_limit=?, is_active=?, updated_at=now()
            WHERE id=? AND deleted_at IS NULL""", r.code().toUpperCase(), r.description(), r.discountType(), r.value(), r.minOrderAmount() == null ? BigDecimal.ZERO : r.minOrderAmount(),
                r.maxDiscount(), r.startsAt(), r.endsAt(), r.usageLimit(), r.perUserLimit(), r.active() == null || r.active(), id) == 0) throw notFound("Coupon");
        audit.audit(CurrentUser.require().getId(), "COUPON_UPDATED", "COUPON", id, null, r, null);
    }

    @Transactional
    public void delete(long id) {
        if (jdbc.update("UPDATE coupons SET deleted_at = now(), is_active = FALSE, updated_at = now() WHERE id = ? AND deleted_at IS NULL", id) == 0) throw notFound("Coupon");
        audit.audit(CurrentUser.require().getId(), "COUPON_DELETED", "COUPON", id, null, null, null);
    }

    private void check(CouponRequest r) {
        if ("PERCENT".equals(r.discountType()) && r.value().compareTo(BigDecimal.valueOf(100)) > 0) throw bad("A percentage discount cannot exceed 100.");
        if (r.startsAt() != null && r.endsAt() != null && !r.endsAt().isAfter(r.startsAt())) throw bad("The end date must be after the start date.");
    }

    // ---- checkout ----
    private record Row(long id, String code, String type, BigDecimal value, BigDecimal minOrder, BigDecimal maxDiscount, Instant startsAt, Instant endsAt,
                       Integer usageLimit, Integer perUserLimit, int used, boolean active) {}

    private Row find(String code, boolean lock) {
        List<Row> l = jdbc.query("SELECT id, code, discount_type, value, min_order_amount, max_discount, starts_at, ends_at, usage_limit, per_user_limit, used_count, is_active FROM coupons WHERE lower(code) = lower(?) AND deleted_at IS NULL" + (lock ? " FOR UPDATE" : ""),
                (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), ts(rs, 7), ts(rs, 8), (Integer) rs.getObject(9), (Integer) rs.getObject(10), rs.getInt(11), rs.getBoolean(12)), code.trim());
        if (l.isEmpty() || !l.get(0).active()) throw bad("This coupon is not valid.");
        return l.get(0);
    }

    /** @param reserve true when placing an order: locks the coupon row so limits cannot be exceeded by concurrent orders */
    public Applied apply(String code, long userId, BigDecimal subtotal, boolean reserve) {
        Row c = find(code, reserve);
        Instant now = Instant.now();
        if (c.startsAt() != null && now.isBefore(c.startsAt())) throw bad("This coupon is not active yet.");
        if (c.endsAt() != null && now.isAfter(c.endsAt())) throw bad("This coupon has expired.");
        if (subtotal.compareTo(c.minOrder()) < 0) throw bad("This coupon needs an order of at least ₹" + c.minOrder().stripTrailingZeros().toPlainString() + ".");
        if (c.usageLimit() != null && c.used() >= c.usageLimit()) throw bad("This coupon has been fully used.");
        if (c.perUserLimit() != null) {
            Integer mine = jdbc.queryForObject("SELECT count(*) FROM coupon_redemptions WHERE coupon_id = ? AND user_id = ? AND released_at IS NULL", Integer.class, c.id(), userId);
            if (mine != null && mine >= c.perUserLimit()) throw bad("You have already used this coupon.");
        }
        return new Applied(c.id(), c.code(), PricingCalculator.couponDiscount(c.type(), c.value(), c.maxDiscount(), subtotal));
    }

    /** Called inside the order transaction, after apply(.., true). */
    public void redeem(Applied a, long userId, long orderId) {
        jdbc.update("INSERT INTO coupon_redemptions (coupon_id, user_id, order_id, discount_amount) VALUES (?,?,?,?)", a.id(), userId, orderId, a.discount());
        jdbc.update("UPDATE coupons SET used_count = used_count + 1 WHERE id = ?", a.id());
    }

    /** Gives the use back when an unpaid order is cancelled. */
    public void release(long orderId) {
        List<Long> ids = jdbc.queryForList("UPDATE coupon_redemptions SET released_at = now() WHERE order_id = ? AND released_at IS NULL RETURNING coupon_id", Long.class, orderId);
        for (Long id : ids) jdbc.update("UPDATE coupons SET used_count = GREATEST(used_count - 1, 0) WHERE id = ?", id);
    }
}
