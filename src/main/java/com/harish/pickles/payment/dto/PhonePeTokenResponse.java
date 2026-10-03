package com.harish.pickles.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response of POST {auth}/v1/oauth/token. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PhonePeTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("encrypted_access_token") String encryptedAccessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("issued_at") Long issuedAt,
        @JsonProperty("expires_at") Long expiresAt,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("session_expires_at") Long sessionExpiresAt) {
}
