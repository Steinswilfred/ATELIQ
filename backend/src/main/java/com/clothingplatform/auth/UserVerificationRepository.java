package com.clothingplatform.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserVerificationRepository extends JpaRepository<UserVerification, Long> {
    Optional<UserVerification> findByTokenHashAndChannelAndConsumedAtIsNull(String tokenHash, VerificationChannel channel);
    Optional<UserVerification> findFirstByUserIdAndChannelAndConsumedAtIsNullOrderByIdDesc(Long userId, VerificationChannel channel);
    Optional<UserVerification> findFirstByUserIdAndChannelOrderByIdDesc(Long userId, VerificationChannel channel);

    @Modifying
    @Query("update UserVerification v set v.consumedAt = :now where v.userId = :userId and v.channel = :channel and v.consumedAt is null")
    void invalidateOutstanding(@Param("userId") Long userId, @Param("channel") VerificationChannel channel, @Param("now") Instant now);
}
