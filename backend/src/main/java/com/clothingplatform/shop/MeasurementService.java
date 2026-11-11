package com.clothingplatform.shop;

import static com.clothingplatform.catalog.CatalogSupport.*;

import com.clothingplatform.common.Sql;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeasurementService {
    public record FieldView(String code, String label, String instruction, BigDecimal minCm, BigDecimal maxCm) {}
    public record ProfileView(long id, String name, String unit, Map<String, BigDecimal> measurements, boolean isDefault, Instant updatedAt) {}
    /** Measurements are sent in centimetres; the unit is only the customer's display preference. */
    public record ProfileRequest(@NotBlank @Size(max = 80) String name, @NotNull @Pattern(regexp = "^(CM|IN)$") String unit,
                                 @NotNull Map<String, BigDecimal> measurements, Boolean isDefault) {}

    private static final int MAX_PROFILES = 20;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public MeasurementService(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public List<FieldView> fields() {
        return jdbc.query("SELECT code, label, instruction, min_cm, max_cm FROM measurement_fields WHERE is_active ORDER BY sort_order",
                (rs, i) -> new FieldView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4), rs.getBigDecimal(5)));
    }

    public List<ProfileView> list(long userId) {
        return jdbc.query("SELECT id, name, unit, measurements::text AS m, is_default, updated_at FROM measurement_profiles WHERE user_id = ? AND deleted_at IS NULL ORDER BY is_default DESC, updated_at DESC",
                (rs, i) -> new ProfileView(rs.getLong("id"), rs.getString("name"), rs.getString("unit"), read(rs.getString("m")), rs.getBoolean("is_default"), Sql.ts(rs, "updated_at")), userId);
    }

    @Transactional
    public long create(long userId, ProfileRequest r) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM measurement_profiles WHERE user_id = ? AND deleted_at IS NULL", Integer.class, userId);
        if (n != null && n >= MAX_PROFILES) throw bad("You can keep up to " + MAX_PROFILES + " measurement profiles. Delete one first.");
        String json = validate(r);
        boolean makeDefault = Boolean.TRUE.equals(r.isDefault()) || n == null || n == 0;
        if (makeDefault) jdbc.update("UPDATE measurement_profiles SET is_default = FALSE WHERE user_id = ? AND is_default", userId);
        return jdbc.queryForObject("INSERT INTO measurement_profiles (user_id, name, unit, measurements, is_default) VALUES (?,?,?,?::jsonb,?) RETURNING id", Long.class,
                userId, r.name().trim(), r.unit(), json, makeDefault);
    }

    @Transactional
    public void update(long userId, long id, ProfileRequest r) {
        String json = validate(r);
        if (Boolean.TRUE.equals(r.isDefault())) jdbc.update("UPDATE measurement_profiles SET is_default = FALSE WHERE user_id = ? AND is_default AND id <> ?", userId, id);
        int rows = jdbc.update("UPDATE measurement_profiles SET name = ?, unit = ?, measurements = ?::jsonb, is_default = ?, updated_at = now() WHERE id = ? AND user_id = ? AND deleted_at IS NULL",
                r.name().trim(), r.unit(), json, Boolean.TRUE.equals(r.isDefault()) || isDefault(userId, id), id, userId);
        if (rows == 0) throw notFound("Measurement profile");
    }

    @Transactional
    public void delete(long userId, long id) {
        boolean wasDefault = isDefault(userId, id);
        if (jdbc.update("UPDATE measurement_profiles SET deleted_at = now(), is_default = FALSE, updated_at = now() WHERE id = ? AND user_id = ? AND deleted_at IS NULL", id, userId) == 0) throw notFound("Measurement profile");
        if (wasDefault) jdbc.update("UPDATE measurement_profiles SET is_default = TRUE WHERE id = (SELECT id FROM measurement_profiles WHERE user_id = ? AND deleted_at IS NULL ORDER BY updated_at DESC LIMIT 1)", userId);
    }

    private boolean isDefault(long userId, long id) {
        Boolean b = jdbc.query("SELECT is_default FROM measurement_profiles WHERE id = ? AND user_id = ? AND deleted_at IS NULL", rs -> rs.next() ? rs.getBoolean(1) : null, id, userId);
        return Boolean.TRUE.equals(b);
    }

    /** Every key must be a known field and every value inside that field's allowed range (cm). */
    private String validate(ProfileRequest r) {
        if (r.measurements().isEmpty()) throw bad("Enter at least one measurement.");
        Map<String, FieldView> known = new HashMap<>();
        fields().forEach(f -> known.put(f.code(), f));
        Map<String, BigDecimal> clean = new TreeMap<>();
        for (var e : r.measurements().entrySet()) {
            FieldView f = known.get(e.getKey());
            if (f == null) throw bad("Unknown measurement '" + e.getKey() + "'.");
            BigDecimal v = e.getValue();
            if (v == null || v.compareTo(f.minCm()) < 0 || v.compareTo(f.maxCm()) > 0)
                throw bad(f.label() + " must be between " + f.minCm().stripTrailingZeros().toPlainString() + " and " + f.maxCm().stripTrailingZeros().toPlainString() + " cm.");
            clean.put(e.getKey(), v.setScale(1, java.math.RoundingMode.HALF_UP));
        }
        try { return mapper.writeValueAsString(clean); } catch (JsonProcessingException ex) { throw bad("Invalid measurements."); }
    }

    private Map<String, BigDecimal> read(String json) {
        try { return mapper.readValue(json, new TypeReference<Map<String, BigDecimal>>() {}); } catch (JsonProcessingException e) { return Map.of(); }
    }
}
