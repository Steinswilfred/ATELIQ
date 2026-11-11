package com.clothingplatform.auth;

import com.clothingplatform.exception.BusinessException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Rotated(Long userId, String newToken) {}

    private final RefreshTokenRepository repo;
    private final TokenHasher hasher;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repo, TokenHasher hasher, @Value("${app.jwt.refresh-ttl-days}") long days) {
        this.repo = repo; this.hasher = hasher; this.ttl = Duration.ofDays(days);
    }

    public Duration ttl() { return ttl; }

    @Transactional
    public String issueNewFamily(Long userId, String ip, String ua) { return issue(userId, UUID.randomUUID(), ip, ua); }

    private String issue(Long userId, UUID family, String ip, String ua) {
        byte[] b = new byte[48];
        RANDOM.nextBytes(b);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        repo.save(new RefreshToken(userId, family, hasher.hash(raw), Instant.now().plus(ttl), ip, ua));
        return raw;
    }

    /**
     * Single-use rotation. Presenting an already-used token means it was copied, so the whole family is revoked.
     * noRollbackFor keeps that revocation even though we throw.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Rotated rotate(String raw, String ip, String ua) {
        RefreshToken t = repo.findByTokenHash(hasher.hash(raw)).orElseThrow(this::invalid);
        if (t.getRevokedAt() != null || t.getExpiresAt().isBefore(Instant.now())) throw invalid();
        if (t.getUsedAt() != null) {
            repo.revokeFamily(t.getFamilyId(), Instant.now());
            throw invalid();
        }
        t.setUsedAt(Instant.now());
        return new Rotated(t.getUserId(), issue(t.getUserId(), t.getFamilyId(), ip, ua));
    }

    @Transactional
    public void revokeByRaw(String raw) {
        repo.findByTokenHash(hasher.hash(raw)).ifPresent(t -> repo.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAllForUser(Long userId) { repo.revokeAllForUser(userId, Instant.now()); }

    private BusinessException invalid() {
        return new BusinessException("INVALID_REFRESH_TOKEN", "Your session has expired. Please sign in again.", HttpStatus.UNAUTHORIZED);
    }
}
