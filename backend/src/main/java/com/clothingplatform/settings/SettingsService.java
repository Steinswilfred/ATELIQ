package com.clothingplatform.settings;

import com.clothingplatform.audit.AuditService;
import com.clothingplatform.exception.BusinessException;
import com.clothingplatform.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Website, theme and homepage settings. Only whitelisted keys with validated shapes are accepted. */
@Service
public class SettingsService {
    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern FONT = Pattern.compile("^[A-Za-z0-9 ]{2,40}$");
    private static final Pattern URL = Pattern.compile("^(https?://|/)\\S{1,500}$");
    private static final Set<String> COLOR_KEYS = Set.of("primary", "secondary", "background", "surface", "accent", "text");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public SettingsService(JdbcTemplate jdbc, ObjectMapper mapper, AuditService audit) { this.jdbc = jdbc; this.mapper = mapper; this.audit = audit; }

    public Map<String, JsonNode> publicSettings() { return read("WHERE is_public"); }
    public Map<String, JsonNode> allSettings() { return read(""); }

    private Map<String, JsonNode> read(String where) {
        Map<String, JsonNode> out = new TreeMap<>();
        jdbc.query("SELECT setting_key, setting_value::text FROM website_settings " + where, rs -> {
            try { out.put(rs.getString(1), mapper.readTree(rs.getString(2))); } catch (JsonProcessingException ignored) { }
        });
        return out;
    }

    @Transactional
    public void put(String key, JsonNode value) {
        validate(key, value);
        JsonNode before = read("WHERE setting_key = '" + key.replace("'", "") + "'").get(key);   // key already whitelisted by validate()
        jdbc.update("""
            INSERT INTO website_settings (setting_key, setting_value, updated_by) VALUES (?, ?::jsonb, ?)
            ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value, updated_by = EXCLUDED.updated_by, updated_at = now()""",
            key, value.toString(), CurrentUser.require().getId());
        audit.audit(CurrentUser.require().getId(), "SETTING_CHANGED", "SETTING", key, before, value, null);
    }

    private void validate(String key, JsonNode v) {
        switch (key) {
            case "site.name" -> text(v, 1, 60, "Site name");
            case "site.tagline" -> text(v, 0, 160, "Tagline");
            case "site.logoUrl", "site.faviconUrl" -> url(v, true);
            case "theme.colors" -> {
                object(v);
                for (String k : COLOR_KEYS) if (!v.hasNonNull(k) || !HEX.matcher(v.get(k).asText()).matches()) throw bad("Colour '" + k + "' must be a hex value like #E8505B.");
                v.fieldNames().forEachRemaining(f -> { if (!COLOR_KEYS.contains(f)) throw bad("Unknown colour '" + f + "'."); });
            }
            case "theme.fonts" -> {
                object(v);
                for (String k : List.of("heading", "body")) if (!v.hasNonNull(k) || !FONT.matcher(v.get(k).asText()).matches()) throw bad("Font '" + k + "' must be a font family name (letters, numbers, spaces).");
                if (!v.hasNonNull("baseSize") || !v.get("baseSize").isInt() || v.get("baseSize").asInt() < 12 || v.get("baseSize").asInt() > 22) throw bad("Base font size must be 12 to 22.");
            }
            case "finance.commissionPercent" -> number(v, 0, 90, "Commission");
            case "finance.settlementHoldDays" -> number(v, 0, 90, "Settlement hold");
            case "finance.lateCancellationFeePercent" -> number(v, 0, 100, "Cancellation fee");
            case "finance.autoApproveCustomerRefunds" -> { if (v == null || !v.isBoolean()) throw bad("This rule must be on or off."); }
            case "assignment.requireAvailable", "assignment.respectCapacity" -> { if (v == null || !v.isBoolean()) throw bad("This rule must be on or off."); }
            case "assignment.escalationHours" -> number(v, 0, 720, "Escalation time");
            case "checkout.taxRatePercent" -> number(v, 0, 100, "Tax rate");
            case "checkout.deliveryCharge" -> number(v, 0, 100000, "Delivery charge");
            case "checkout.freeDeliveryAbove" -> number(v, 0, 10000000, "Free-delivery amount");
            case "checkout.codEnabled" -> { if (v == null || !v.isBoolean()) throw bad("Cash on delivery must be on or off."); }
            case "homepage.banners" -> {
                if (!v.isArray() || v.size() > 10) throw bad("Banners must be a list of up to 10.");
                for (JsonNode b : v) {
                    object(b);
                    text(b.get("title"), 1, 100, "Banner title");
                    if (b.hasNonNull("subtitle")) text(b.get("subtitle"), 0, 200, "Banner subtitle");
                    if (b.hasNonNull("imageUrl")) url(b.get("imageUrl"), false);
                    if (b.hasNonNull("linkUrl")) url(b.get("linkUrl"), false);
                }
            }
            default -> throw bad("Unknown setting '" + key + "'.");
        }
    }

    private void text(JsonNode v, int min, int max, String label) {
        if (v == null || !v.isTextual() || v.asText().trim().length() < min || v.asText().length() > max) throw bad(label + " must be " + min + " to " + max + " characters.");
    }
    private void url(JsonNode v, boolean allowEmpty) {
        if (v == null || !v.isTextual()) throw bad("Enter a web address.");
        String s = v.asText();
        if (s.isEmpty() && allowEmpty) return;
        if (!URL.matcher(s).matches()) throw bad("Addresses must start with http://, https:// or /.");
    }
    private void number(JsonNode v, double min, double max, String label) {
        if (v == null || !v.isNumber() || v.asDouble() < min || v.asDouble() > max) throw bad(label + " must be a number from " + (long) min + " to " + (long) max + ".");
    }
    private void object(JsonNode v) { if (v == null || !v.isObject()) throw bad("Invalid value."); }
    private BusinessException bad(String m) { return new BusinessException("INVALID_SETTING", m, HttpStatus.BAD_REQUEST); }
}
