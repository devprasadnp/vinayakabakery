package com.harish.pickles.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * PhonePe signs webhooks with {@code Authorization: SHA256(username:password)} where the
 * username / password are the ones configured on the PhonePe dashboard webhook screen.
 */
@Component
public class PhonePeWebhookVerifier {

    private final PhonePeProperties props;

    public PhonePeWebhookVerifier(PhonePeProperties props) {
        this.props = props;
    }

    public boolean isValid(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }
        String received = authorizationHeader.startsWith("SHA256 ")
                ? authorizationHeader.substring("SHA256 ".length()).trim()
                : authorizationHeader.trim();
        String expected = expectedSignature();
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    public String expectedSignature() {
        return sha256Hex(props.getWebhook().getUsername() + ":" + props.getWebhook().getPassword());
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
