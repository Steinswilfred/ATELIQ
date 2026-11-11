package com.clothingplatform.address;

import static com.clothingplatform.catalog.CatalogSupport.*;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressService {
    public record AddressView(long id, String label, String fullName, String phone, String line1, String line2, String city, String state,
                              String postalCode, BigDecimal latitude, BigDecimal longitude, boolean isDefault) {}
    public record AddressRequest(@Size(max = 40) String label, @NotBlank @Size(max = 120) String fullName,
                                 @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Enter the phone number with country code, for example +919876543210.") String phone,
                                 @NotBlank @Size(max = 200) String line1, @Size(max = 200) String line2, @NotBlank @Size(max = 80) String city, @NotBlank @Size(max = 80) String state,
                                 @NotBlank @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Enter a 6-digit PIN code.") String postalCode,
                                 @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude, @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude, Boolean isDefault) {}

    private static final int MAX = 10;
    private final JdbcTemplate jdbc;
    public AddressService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String COLS = "id, label, full_name, phone, line1, line2, city, state, postal_code, latitude, longitude, is_default";
    private static AddressView row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AddressView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getBoolean(12));
    }

    public List<AddressView> list(long userId) {
        return jdbc.query("SELECT " + COLS + " FROM addresses WHERE user_id = ? AND deleted_at IS NULL ORDER BY is_default DESC, updated_at DESC", (rs, i) -> row(rs), userId);
    }

    /** Used by checkout: only the customer's own, non-deleted address. */
    public AddressView require(long userId, long id) {
        List<AddressView> l = jdbc.query("SELECT " + COLS + " FROM addresses WHERE id = ? AND user_id = ? AND deleted_at IS NULL", (rs, i) -> row(rs), id, userId);
        if (l.isEmpty()) throw bad("Choose one of your saved delivery addresses.");
        return l.get(0);
    }

    @Transactional
    public long create(long userId, AddressRequest r) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM addresses WHERE user_id = ? AND deleted_at IS NULL", Integer.class, userId);
        if (n != null && n >= MAX) throw bad("You can keep up to " + MAX + " addresses. Delete one first.");
        boolean def = Boolean.TRUE.equals(r.isDefault()) || n == null || n == 0;
        if (def) jdbc.update("UPDATE addresses SET is_default = FALSE WHERE user_id = ? AND is_default", userId);
        return jdbc.queryForObject("""
            INSERT INTO addresses (user_id, label, full_name, phone, line1, line2, city, state, postal_code, latitude, longitude, is_default)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?) RETURNING id""", Long.class, userId, r.label(), r.fullName().trim(), r.phone(), r.line1().trim(), r.line2(), r.city().trim(), r.state().trim(), r.postalCode(), r.latitude(), r.longitude(), def);
    }

    @Transactional
    public void update(long userId, long id, AddressRequest r) {
        Boolean wasDefault = jdbc.query("SELECT is_default FROM addresses WHERE id = ? AND user_id = ? AND deleted_at IS NULL", rs -> rs.next() ? rs.getBoolean(1) : null, id, userId);
        if (wasDefault == null) throw notFound("Address");
        boolean def = Boolean.TRUE.equals(r.isDefault()) || wasDefault;
        if (def) jdbc.update("UPDATE addresses SET is_default = FALSE WHERE user_id = ? AND is_default AND id <> ?", userId, id);
        jdbc.update("""
            UPDATE addresses SET label=?, full_name=?, phone=?, line1=?, line2=?, city=?, state=?, postal_code=?, latitude=?, longitude=?, is_default=?, updated_at=now() WHERE id=? AND user_id=?""",
            r.label(), r.fullName().trim(), r.phone(), r.line1().trim(), r.line2(), r.city().trim(), r.state().trim(), r.postalCode(), r.latitude(), r.longitude(), def, id, userId);
    }

    /** Orders keep their own address snapshot, so deleting an address never changes an order. */
    @Transactional
    public void delete(long userId, long id) {
        Boolean wasDefault = jdbc.query("SELECT is_default FROM addresses WHERE id = ? AND user_id = ? AND deleted_at IS NULL", rs -> rs.next() ? rs.getBoolean(1) : null, id, userId);
        if (wasDefault == null) throw notFound("Address");
        jdbc.update("UPDATE addresses SET deleted_at = now(), is_default = FALSE, updated_at = now() WHERE id = ?", id);
        if (wasDefault) jdbc.update("UPDATE addresses SET is_default = TRUE WHERE id = (SELECT id FROM addresses WHERE user_id = ? AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT 1)", userId);
    }
}
