package com.clothingplatform.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_verifications")
public class UserVerification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private VerificationChannel channel;
    @Column(name = "token_hash", nullable = false) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(nullable = false) private int attempts;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    protected UserVerification() {}
    public UserVerification(Long userId, VerificationChannel channel, String tokenHash, Instant expiresAt) {
        this.userId = userId; this.channel = channel; this.tokenHash = tokenHash; this.expiresAt = expiresAt;
    }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant v) { consumedAt = v; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int v) { attempts = v; }
    public Instant getCreatedAt() { return createdAt; }
}
