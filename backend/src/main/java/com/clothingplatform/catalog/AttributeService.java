package com.clothingplatform.catalog;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.Urls;
import com.clothingplatform.security.CurrentUser;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fabrics, colours and sizes: the reference data products draw from. */
@Service
public class AttributeService {
    public record FabricView(long id, String name, String material, String description, BigDecimal priceDelta, String imageUrl, boolean active, List<Long> colorIds) {}
    public record FabricRequest(@NotBlank @Size(max = 120) String name, @Size(max = 120) String material, @Size(max = 1000) String description,
                                @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal priceDelta, String imageUrl, Boolean active, List<Long> colorIds) {}
    public record ColorView(long id, String name, String hexCode, boolean active) {}
    public record ColorRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Use a hex colour like #E8505B.") String hexCode, Boolean active) {}
    public record SizeView(long id, String code, String name, int sortOrder, boolean active) {}
    public record SizeRequest(@NotBlank @Pattern(regexp = "^[A-Z0-9]{1,10}$", message = "Use up to 10 capital letters or digits.") String code,
                              @NotBlank @Size(max = 60) String name, Integer sortOrder, Boolean active) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final CatalogSupport support;
    public AttributeService(JdbcTemplate jdbc, AuditService audit, CatalogSupport support) { this.jdbc = jdbc; this.audit = audit; this.support = support; }

    private long actor() { return CurrentUser.require().getId(); }

    // ---- fabrics ----
    public List<FabricView> fabrics() {
        Map<Long, List<Long>> colors = new HashMap<>();
        jdbc.query("SELECT fabric_id, color_id FROM fabric_colors ORDER BY color_id", rs -> { colors.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2)); });
        return jdbc.query("SELECT id, name, material, description, price_delta, image_url, is_active FROM fabrics WHERE deleted_at IS NULL ORDER BY name",
                (rs, i) -> new FabricView(rs.getLong("id"), rs.getString("name"), rs.getString("material"), rs.getString("description"),
                        rs.getBigDecimal("price_delta"), rs.getString("image_url"), rs.getBoolean("is_active"), colors.getOrDefault(rs.getLong("id"), List.of())));
    }

    @Transactional
    public long createFabric(FabricRequest r) {
        List<Long> colorIds = support.requireIds("colors", r.colorIds(), "colours");
        long id = jdbc.queryForObject("INSERT INTO fabrics (name, material, description, price_delta, image_url, is_active) VALUES (?,?,?,?,?,?) RETURNING id", Long.class,
                r.name().trim(), r.material(), r.description(), r.priceDelta(), Urls.clean(r.imageUrl()), r.active() == null || r.active());
        setFabricColors(id, colorIds);
        audit.audit(actor(), "FABRIC_CREATED", "FABRIC", id, null, r, null);
        return id;
    }

    @Transactional
    public void updateFabric(long id, FabricRequest r) {
        requireLive("fabrics", id, "Fabric");
        List<Long> colorIds = support.requireIds("colors", r.colorIds(), "colours");
        jdbc.update("UPDATE fabrics SET name=?, material=?, description=?, price_delta=?, image_url=?, is_active=?, updated_at=now() WHERE id=?",
                r.name().trim(), r.material(), r.description(), r.priceDelta(), Urls.clean(r.imageUrl()), r.active() == null || r.active(), id);
        setFabricColors(id, colorIds);
        audit.audit(actor(), "FABRIC_UPDATED", "FABRIC", id, null, r, null);
    }

    @Transactional
    public void deleteFabric(long id) {
        requireLive("fabrics", id, "Fabric");
        jdbc.update("DELETE FROM product_fabrics WHERE fabric_id = ?", id);
        jdbc.update("UPDATE fabrics SET deleted_at = now(), updated_at = now() WHERE id = ?", id);
        audit.audit(actor(), "FABRIC_DELETED", "FABRIC", id, null, null, null);
    }

    private void setFabricColors(long fabricId, List<Long> colorIds) {
        jdbc.update("DELETE FROM fabric_colors WHERE fabric_id = ?", fabricId);
        for (long c : colorIds) jdbc.update("INSERT INTO fabric_colors (fabric_id, color_id) VALUES (?,?)", fabricId, c);
    }

    // ---- colours ----
    public List<ColorView> colors() {
        return jdbc.query("SELECT id, name, hex_code, is_active FROM colors ORDER BY name",
                (rs, i) -> new ColorView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
    }

    @Transactional
    public long createColor(ColorRequest r) {
        long id = jdbc.queryForObject("INSERT INTO colors (name, hex_code, is_active) VALUES (?,?,?) RETURNING id", Long.class, r.name().trim(), r.hexCode().toUpperCase(), r.active() == null || r.active());
        audit.audit(actor(), "COLOR_CREATED", "COLOR", id, null, r, null);
        return id;
    }

    @Transactional
    public void updateColor(long id, ColorRequest r) {
        if (jdbc.update("UPDATE colors SET name=?, hex_code=?, is_active=? WHERE id=?", r.name().trim(), r.hexCode().toUpperCase(), r.active() == null || r.active(), id) == 0) throw notFound("Colour");
        audit.audit(actor(), "COLOR_UPDATED", "COLOR", id, null, r, null);
    }

    @Transactional
    public void deleteColor(long id) {
        if (jdbc.update("DELETE FROM colors WHERE id = ?", id) == 0) throw notFound("Colour");   // FK blocks deletion while a fabric uses it
        audit.audit(actor(), "COLOR_DELETED", "COLOR", id, null, null, null);
    }

    // ---- sizes ----
    public List<SizeView> sizes() {
        return jdbc.query("SELECT id, code, name, sort_order, is_active FROM sizes ORDER BY sort_order, code",
                (rs, i) -> new SizeView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBoolean(5)));
    }

    @Transactional
    public long createSize(SizeRequest r) {
        long id = jdbc.queryForObject("INSERT INTO sizes (code, name, sort_order, is_active) VALUES (?,?,?,?) RETURNING id", Long.class, r.code(), r.name().trim(), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active());
        audit.audit(actor(), "SIZE_CREATED", "SIZE", id, null, r, null);
        return id;
    }

    @Transactional
    public void updateSize(long id, SizeRequest r) {
        if (jdbc.update("UPDATE sizes SET code=?, name=?, sort_order=?, is_active=? WHERE id=?", r.code(), r.name().trim(), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active(), id) == 0) throw notFound("Size");
        audit.audit(actor(), "SIZE_UPDATED", "SIZE", id, null, r, null);
    }

    @Transactional
    public void deleteSize(long id) {
        if (jdbc.update("DELETE FROM sizes WHERE id = ?", id) == 0) throw notFound("Size");
        audit.audit(actor(), "SIZE_DELETED", "SIZE", id, null, null, null);
    }

    private void requireLive(String table, long id, String label) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id = ? AND deleted_at IS NULL", Integer.class, id);
        if (n == null || n == 0) throw notFound(label);
    }
}
