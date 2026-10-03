package com.harish.pickles.service;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.domain.PasswordResetToken;
import com.harish.pickles.domain.User;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.repository.PasswordResetTokenRepository;
import com.harish.pickles.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * "Forgot my password": email a one-time link, then let the person choose a new password.
 *
 * <ul>
 *   <li>The link carries a random 256-bit token. Only its SHA-256 is stored.</li>
 *   <li>{@link #requestReset} never reveals whether an address has an account - it behaves
 *       the same either way, and the email goes out on a background thread.</li>
 *   <li>A link works once, expires (default 60 minutes), and is dead the moment a newer one
 *       is issued or the password has been changed.</li>
 *   <li>The link is built from {@code app.base-url}, never from the request's Host header, so
 *       it cannot be poisoned to point at someone else's site.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 72;
    static final String INVALID_LINK = "This password reset link is invalid or has expired. Please request a new one.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AppProperties props;

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder, MailService mailService, AppProperties props) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.props = props;
    }

    /**
     * Emails a reset link if {@code email} belongs to an enabled account. Returns normally in every
     * case so the caller cannot tell an unknown address from a known one.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        Optional<User> found = userRepository.findByEmailIgnoreCase(email.trim());
        if (found.isEmpty() || !found.get().isEnabled()) {
            log.info("Password reset requested for an address with no active account");
            return;
        }
        User user = found.get();
        LocalDateTime now = LocalDateTime.now();

        tokenRepository.deleteExpiredBefore(now.minusDays(1));

        long cooldown = props.getPasswordReset().getResendCooldownSeconds();
        if (tokenRepository.existsByUserAndCreatedAtAfter(user, now.minusSeconds(cooldown))) {
            log.info("Password reset for {} skipped: a link was sent in the last {}s", user.getEmail(), cooldown);
            return;
        }

        tokenRepository.invalidateOpenTokens(user, now);
        String rawToken = newToken();
        long validFor = props.getPasswordReset().getExpiryMinutes();
        tokenRepository.save(new PasswordResetToken(user, sha256(rawToken), now.plusMinutes(validFor)));

        String link = trimTrailingSlash(props.getBaseUrl()) + "/reset-password?token=" + rawToken;
        String to = user.getEmail();
        String name = user.getFullName();
        // Only email once the token row is safely committed, so the link can never point at nothing.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                mailService.sendPasswordReset(to, name, link, validFor);
            }
        });
    }

    /** True when {@code token} is real, unused, unexpired and its account can still sign in. */
    @Transactional(readOnly = true)
    public boolean isValid(String token) {
        return findUsable(token).isPresent();
    }

    /** Spends the token and sets the new password. */
    @Transactional
    public void completeReset(String token, String newPassword) {
        PasswordResetToken found = findUsable(token).orElseThrow(() -> new BusinessException(INVALID_LINK));
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (newPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new BusinessException("Password must be " + MAX_PASSWORD_LENGTH + " characters or fewer");
        }

        LocalDateTime now = LocalDateTime.now();
        if (tokenRepository.markUsed(found.getId(), now) == 0) {
            throw new BusinessException(INVALID_LINK);
        }
        User user = found.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.invalidateOpenTokens(user, now);
        log.info("Password reset completed for {}", user.getEmail());
    }

    private Optional<PasswordResetToken> findUsable(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokenRepository.findByTokenHash(sha256(token.trim()))
                .filter(t -> t.isUsable(LocalDateTime.now()))
                .filter(t -> t.getUser().isEnabled());
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
