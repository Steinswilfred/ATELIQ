package com.clothingplatform.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(name = "token_hash", nullable = false) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    @Column(name = "ip_address") private String ipAddress;
    @Column(name = "user_agent") private String userAgent;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected RefreshToken() {}
    public RefreshToken(Long userId, UUID familyId, String tokenHash, Instant expiresAt, String ip, String ua) {
        this.userId = userId; this.familyId = familyId; this.tokenHash = tokenHash; this.expiresAt = expiresAt;
        this.ipAddress = ip; this.userAgent = ua == null ? null : ua.substring(0, Math.min(ua.length(), 300));
    }
    public Long getUserId() { return userId; }
    public UUID getFamilyId() { return familyId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public void setUsedAt(Instant v) { usedAt = v; }
    public Instant getRevokedAt() { return revokedAt; }
}
