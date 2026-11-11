package com.clothingplatform.catalog;

import com.clothingplatform.exception.BusinessException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CatalogSupport {
    private final JdbcTemplate jdbc;
    public CatalogSupport(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static BusinessException bad(String m) { return new BusinessException("BAD_REQUEST", m, HttpStatus.BAD_REQUEST); }
    public static BusinessException notFound(String what) { return new BusinessException("NOT_FOUND", what + " not found.", HttpStatus.NOT_FOUND); }
    public static BusinessException conflict(String m) { return new BusinessException("CONFLICT", m, HttpStatus.CONFLICT); }

    /** Verifies every id exists in the (internal) table; returns the de-duplicated ids. */
    public List<Long> requireIds(String table, Collection<Long> ids, String label) {
        List<Long> distinct = List.copyOf(new HashSet<>(ids == null ? List.of() : ids));
        if (distinct.isEmpty()) return distinct;
        String in = String.join(",", java.util.Collections.nCopies(distinct.size(), "?"));
        boolean softDeletes = table.equals("fabrics") || table.equals("customization_options") || table.equals("customization_groups");
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id IN (" + in + ")" + (softDeletes ? " AND deleted_at IS NULL" : ""),
                Integer.class, distinct.toArray());
        if (n == null || n != distinct.size()) throw bad("One or more selected " + label + " no longer exist.");
        return distinct;
    }
}
