package com.clothingplatform.auth;

import com.clothingplatform.user.User;
import com.clothingplatform.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private final UserVerificationRepository repo;
    private final UserRepository users;
    private final TokenHasher hasher;

    public VerificationService(UserVerificationRepository repo, UserRepository users, TokenHasher hasher) {
        this.repo = repo; this.users = users; this.hasher = hasher;
    }

    /** True if a code/link for this channel was issued very recently (basic anti-spam for email/SMS). */
    public boolean issuedRecently(Long userId, VerificationChannel ch) {
        return repo.findFirstByUserIdAndChannelOrderByIdDesc(userId, ch)
                .map(v -> v.getCreatedAt().isAfter(Instant.now().minus(RESEND_COOLDOWN))).orElse(false);
    }

    @Transactional
    public String issueLinkToken(Long userId, VerificationChannel ch, Duration ttl) {
        repo.invalidateOutstanding(userId, ch, Instant.now());
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        repo.save(new UserVerification(userId, ch, hasher.hash(token), Instant.now().plus(ttl)));
        return token;
    }

    @Transactional
    public String issueOtp(Long userId) {
        repo.invalidateOutstanding(userId, VerificationChannel.PHONE, Instant.now());
        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        repo.save(new UserVerification(userId, VerificationChannel.PHONE, hasher.hash(otp), Instant.now().plus(Duration.ofMinutes(10))));
        return otp;
    }

    /** Consumes a single-use link token and returns its owner. */
    @Transactional
    public Optional<User> consumeLinkToken(String token, VerificationChannel ch) {
        return repo.findByTokenHashAndChannelAndConsumedAtIsNull(hasher.hash(token), ch)
                .filter(v -> v.getExpiresAt().isAfter(Instant.now()))
                .flatMap(v -> { v.setConsumedAt(Instant.now()); return users.findById(v.getUserId()); });
    }

    /** Checks an OTP; wrong guesses are counted and the code dies after {@value #MAX_OTP_ATTEMPTS} attempts. */
    @Transactional
    public boolean consumeOtp(Long userId, String otp) {
        Optional<UserVerification> ov = repo.findFirstByUserIdAndChannelAndConsumedAtIsNullOrderByIdDesc(userId, VerificationChannel.PHONE);
        if (ov.isEmpty()) return false;
        UserVerification v = ov.get();
        if (v.getExpiresAt().isBefore(Instant.now()) || v.getAttempts() >= MAX_OTP_ATTEMPTS) return false;
        v.setAttempts(v.getAttempts() + 1);
        if (!hasher.matches(otp, v.getTokenHash())) return false;
        v.setConsumedAt(Instant.now());
        return true;
    }
}
