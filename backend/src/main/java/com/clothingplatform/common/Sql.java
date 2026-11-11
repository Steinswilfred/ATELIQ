package com.clothingplatform.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

public final class Sql {
    private Sql() {}
    public static Instant ts(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }
    public static int clampSize(int size) { return Math.max(1, Math.min(100, size)); }
    public static int clampPage(int page) { return Math.max(0, page); }
}
