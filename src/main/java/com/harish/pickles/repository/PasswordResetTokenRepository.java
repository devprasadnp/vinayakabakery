package com.harish.pickles.repository;

import com.harish.pickles.domain.PasswordResetToken;
import com.harish.pickles.domain.User;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** True when this user was already sent a link at or after {@code since} - used to throttle. */
    boolean existsByUserAndCreatedAtAfter(User user, LocalDateTime since);

    /**
     * Claims one token. Returns 1 for the single caller that gets it and 0 for anyone racing it, so
     * a link can never be spent twice even when two requests arrive at the same instant.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Kills every link still open for a user, e.g. once one of them has been used. */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.user = :user AND t.usedAt IS NULL")
    int invalidateOpenTokens(@Param("user") User user, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
