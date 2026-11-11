package com.clothingplatform.common;

import java.text.Normalizer;
import org.springframework.jdbc.core.JdbcTemplate;

public final class Slugs {
    private Slugs() {}

    public static String of(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase()
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return s.isEmpty() ? "item" : s.substring(0, Math.min(s.length(), 100));
    }

    /** @param table an internal constant, never user input */
    public static String unique(JdbcTemplate jdbc, String table, String text, Long excludeId) {
        String base = of(text), slug = base;
        for (int n = 2; n < 1000; n++) {
            Integer c = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE slug = ? AND deleted_at IS NULL AND id <> ?",
                    Integer.class, slug, excludeId == null ? -1L : excludeId);
            if (c == null || c == 0) return slug;
            slug = base + "-" + n;
        }
        return base + "-" + System.nanoTime();
    }
}
