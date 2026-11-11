package com.clothingplatform.catalog;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.common.Urls;
import com.clothingplatform.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customization groups and options are data, not code. An option's renderBinding tells the 3D/2D renderer
 * (Phase 6) what to change: {"type": "mesh_toggle|material_color|texture|morph_target|layer_2d", "target": "..."}.
 */
@Service
public class CustomizationService {
    private static final Set<String> BINDING_TYPES = Set.of("mesh_toggle", "material_color", "texture", "morph_target", "layer_2d");

    public record OptionView(long id, long groupId, String code, String name, String description, String imageUrl, BigDecimal priceDelta,
                             JsonNode renderBinding, int sortOrder, boolean active) {}
    public record GroupView(long id, String code, String name, String description, int sortOrder, boolean active, List<OptionView> options) {}
    public record GroupRequest(@NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,59}$", message = "Use lowercase letters, digits and underscores, e.g. collar_type.") String code,
                               @NotBlank @Size(max = 120) String name, @Size(max = 500) String description, Integer sortOrder, Boolean active) {}
    public record OptionRequest(@NotNull Long groupId, @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,59}$", message = "Use lowercase letters, digits and underscores.") String code,
                                @NotBlank @Size(max = 120) String name, @Size(max = 500) String description, String imageUrl,
                                @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal priceDelta, JsonNode renderBinding, Integer sortOrder, Boolean active) {}

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper mapper;
    public CustomizationService(JdbcTemplate jdbc, AuditService audit, ObjectMapper mapper) { this.jdbc = jdbc; this.audit = audit; this.mapper = mapper; }

    private long actor() { return CurrentUser.require().getId(); }

    public List<GroupView> groups() {
        Map<Long, List<OptionView>> byGroup = new HashMap<>();
        jdbc.query("""
            SELECT id, group_id, code, name, description, image_url, price_delta, render_binding::text AS rb, sort_order, is_active
            FROM customization_options WHERE deleted_at IS NULL ORDER BY sort_order, name""", rs -> {
            OptionView o = new OptionView(rs.getLong("id"), rs.getLong("group_id"), rs.getString("code"), rs.getString("name"), rs.getString("description"),
                    rs.getString("image_url"), rs.getBigDecimal("price_delta"), parse(rs.getString("rb")), rs.getInt("sort_order"), rs.getBoolean("is_active"));
            byGroup.computeIfAbsent(o.groupId(), k -> new ArrayList<>()).add(o);
        });
        return jdbc.query("SELECT id, code, name, description, sort_order, is_active FROM customization_groups WHERE deleted_at IS NULL ORDER BY sort_order, name",
                (rs, i) -> new GroupView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getBoolean(6), byGroup.getOrDefault(rs.getLong(1), List.of())));
    }

    private JsonNode parse(String s) {
        if (s == null) return null;
        try { return mapper.readTree(s); } catch (JsonProcessingException e) { return null; }
    }

    @Transactional
    public long createGroup(GroupRequest r) {
        long id = jdbc.queryForObject("INSERT INTO customization_groups (code, name, description, sort_order, is_active) VALUES (?,?,?,?,?) RETURNING id", Long.class,
                r.code(), r.name().trim(), r.description(), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active());
        audit.audit(actor(), "CUSTOMIZATION_GROUP_CREATED", "CUSTOMIZATION_GROUP", id, null, r, null);
        return id;
    }

    @Transactional
    public void updateGroup(long id, GroupRequest r) {
        if (jdbc.update("UPDATE customization_groups SET code=?, name=?, description=?, sort_order=?, is_active=?, updated_at=now() WHERE id=? AND deleted_at IS NULL",
                r.code(), r.name().trim(), r.description(), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active(), id) == 0) throw notFound("Group");
        audit.audit(actor(), "CUSTOMIZATION_GROUP_UPDATED", "CUSTOMIZATION_GROUP", id, null, r, null);
    }

    @Transactional
    public void deleteGroup(long id) {
        Integer opts = jdbc.queryForObject("SELECT count(*) FROM customization_options WHERE group_id = ? AND deleted_at IS NULL", Integer.class, id);
        Integer used = jdbc.queryForObject("SELECT count(*) FROM product_customization_groups WHERE group_id = ?", Integer.class, id);
        if (opts != null && opts > 0) throw conflict("Delete this group's options first.");
        if (used != null && used > 0) throw conflict("This group is used by products. Remove it from them or deactivate it.");
        if (jdbc.update("UPDATE customization_groups SET deleted_at = now(), updated_at = now() WHERE id = ? AND deleted_at IS NULL", id) == 0) throw notFound("Group");
        audit.audit(actor(), "CUSTOMIZATION_GROUP_DELETED", "CUSTOMIZATION_GROUP", id, null, null, null);
    }

    @Transactional
    public long createOption(OptionRequest r) {
        requireGroup(r.groupId());
        long id = jdbc.queryForObject("""
            INSERT INTO customization_options (group_id, code, name, description, image_url, price_delta, render_binding, sort_order, is_active)
            VALUES (?,?,?,?,?,?,?::jsonb,?,?) RETURNING id""", Long.class,
            r.groupId(), r.code(), r.name().trim(), r.description(), Urls.clean(r.imageUrl()), r.priceDelta(), binding(r.renderBinding()),
            r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active());
        audit.audit(actor(), "CUSTOMIZATION_OPTION_CREATED", "CUSTOMIZATION_OPTION", id, null, r, null);
        return id;
    }

    @Transactional
    public void updateOption(long id, OptionRequest r) {
        requireGroup(r.groupId());
        Long oldGroup = jdbc.query("SELECT group_id FROM customization_options WHERE id = ? AND deleted_at IS NULL", rs -> rs.next() ? rs.getLong(1) : null, id);
        if (oldGroup == null) throw notFound("Option");
        if (oldGroup != r.groupId().longValue()) throw bad("An option cannot be moved to another group. Create it there instead.");
        jdbc.update("""
            UPDATE customization_options SET code=?, name=?, description=?, image_url=?, price_delta=?, render_binding=?::jsonb, sort_order=?, is_active=?, updated_at=now() WHERE id=?""",
            r.code(), r.name().trim(), r.description(), Urls.clean(r.imageUrl()), r.priceDelta(), binding(r.renderBinding()), r.sortOrder() == null ? 0 : r.sortOrder(), r.active() == null || r.active(), id);
        audit.audit(actor(), "CUSTOMIZATION_OPTION_UPDATED", "CUSTOMIZATION_OPTION", id, null, r, null);
    }

    /** Soft delete; products stop offering it immediately. Existing orders keep their own snapshot (Phase 8). */
    @Transactional
    public void deleteOption(long id) {
        jdbc.update("DELETE FROM product_customization_options WHERE option_id = ?", id);
        if (jdbc.update("UPDATE customization_options SET deleted_at = now(), updated_at = now() WHERE id = ? AND deleted_at IS NULL", id) == 0) throw notFound("Option");
        audit.audit(actor(), "CUSTOMIZATION_OPTION_DELETED", "CUSTOMIZATION_OPTION", id, null, null, null);
    }

    private void requireGroup(Long id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM customization_groups WHERE id = ? AND deleted_at IS NULL", Integer.class, id);
        if (n == null || n == 0) throw bad("The chosen group does not exist.");
    }

    private String binding(JsonNode b) {
        if (b == null || b.isNull()) return null;
        if (!b.isObject() || !b.hasNonNull("type") || !BINDING_TYPES.contains(b.get("type").asText()))
            throw bad("Render binding type must be one of " + String.join(", ", new TreeSet<>(BINDING_TYPES)) + ".");
        if (!b.hasNonNull("target") || !b.get("target").isTextual() || !b.get("target").asText().matches("^[A-Za-z0-9_.\\-]{1,80}$"))
            throw bad("Render binding needs a target name (letters, digits, _ . -).");
        String type = b.get("type").asText();
        JsonNode value = b.get("value");
        boolean hasValue = value != null && !value.isNull();
        if (hasValue && (!value.isValueNode() || value.asText().length() > 120)) throw bad("Render binding value must be short text or a number.");
        switch (type) {
            case "material_color" -> { if (hasValue && !value.asText().matches("^#[0-9A-Fa-f]{6}$")) throw bad("A colour value must be a hex code like #1F3A5F, or left empty to use the chosen fabric colour."); }
            case "texture", "layer_2d" -> {
                if (!hasValue || !value.asText().matches("^(/media/|https://)\\S{1,110}$")) throw bad("This binding needs an uploaded image address (starting /media/ or https://) as its value.");
            }
            case "morph_target" -> {
                if (hasValue) { try { double d = Double.parseDouble(value.asText()); if (d < 0 || d > 1) throw bad("A shape value must be between 0 and 1."); } catch (NumberFormatException e) { throw bad("A shape value must be a number between 0 and 1."); } }
            }
            default -> { }
        }
        b.fieldNames().forEachRemaining(f -> { if (!Set.of("type", "target", "value").contains(f)) throw bad("Unknown render binding field '" + f + "'."); });
        return b.toString();
    }
}
