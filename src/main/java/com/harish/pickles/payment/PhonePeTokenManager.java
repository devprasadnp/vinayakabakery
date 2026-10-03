package com.harish.pickles.payment;

import com.harish.pickles.exception.PaymentException;
import com.harish.pickles.payment.dto.PhonePeTokenResponse;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Fetches and caches the PhonePe OAuth token.
 * PhonePe rate-limits token creation, so the token is reused until 60s before it expires.
 */
@Component
public class PhonePeTokenManager {

    private static final Logger log = LoggerFactory.getLogger(PhonePeTokenManager.class);
    private static final long REFRESH_SKEW_SECONDS = 60;

    private final PhonePeProperties props;
    private final RestClient restClient;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public PhonePeTokenManager(PhonePeProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder.build();
    }

    public String getAccessToken() {
        String token = cachedToken;
        if (token != null && Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_SKEW_SECONDS))) {
            return token;
        }
        lock.lock();
        try {
            // another thread may have refreshed while we waited
            if (cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(REFRESH_SKEW_SECONDS))) {
                return cachedToken;
            }
            return refresh();
        } finally {
            lock.unlock();
        }
    }

    /** Drops the cached token so the next call re-authenticates (used after a 401 from PhonePe). */
    public void invalidate() {
        cachedToken = null;
        expiresAt = Instant.EPOCH;
    }

    private String refresh() {
        if (!props.hasCredentials()) {
            throw new PaymentException("PhonePe client-id / client-secret are not configured. "
                    + "Set PHONEPE_CLIENT_ID and PHONEPE_CLIENT_SECRET, or run with payments.phonepe.mock=true.");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", props.getClientId());
        form.add("client_version", props.getClientVersion());
        form.add("client_secret", props.getClientSecret());
        form.add("grant_type", "client_credentials");

        try {
            PhonePeTokenResponse response = restClient.post()
                    .uri(props.resolveAuthUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(PhonePeTokenResponse.class);

            if (response == null || response.accessToken() == null) {
                throw new PaymentException("PhonePe returned an empty auth token response");
            }
            cachedToken = response.accessToken();
            expiresAt = resolveExpiry(response);
            log.info("Obtained PhonePe access token, valid until {}", expiresAt);
            return cachedToken;
        } catch (RestClientException ex) {
            throw new PaymentException("Could not authenticate with PhonePe: " + ex.getMessage(), ex);
        }
    }

    private Instant resolveExpiry(PhonePeTokenResponse response) {
        if (response.expiresAt() != null && response.expiresAt() > 0) {
            // epoch seconds
            return Instant.ofEpochSecond(response.expiresAt());
        }
        if (response.expiresIn() != null && response.expiresIn() > 0) {
            return Instant.now().plusSeconds(response.expiresIn());
        }
        return Instant.now().plusSeconds(600);
    }
}
