package com.clothingplatform.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class JwtServiceTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void roundTripsUserId() {
        JwtService s = new JwtService(SECRET, 15);
        assertEquals(42L, s.parseUserId(s.createAccessToken(42)).orElseThrow());
    }

    @Test
    void rejectsTamperedAndForeignTokens() {
        JwtService s = new JwtService(SECRET, 15);
        String t = s.createAccessToken(1);
        assertTrue(s.parseUserId(t + "x").isEmpty());
        assertTrue(new JwtService("fedcba9876543210fedcba9876543210", 15).parseUserId(t).isEmpty());
    }

    @Test
    void rejectsShortSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short", 15));
    }

    @Test
    void hasherIsStableAndComparesSafely() {
        TokenHasher h = new TokenHasher(SECRET);
        assertTrue(h.matches("123456", h.hash("123456")));
        assertFalse(h.matches("123457", h.hash("123456")));
    }
}
