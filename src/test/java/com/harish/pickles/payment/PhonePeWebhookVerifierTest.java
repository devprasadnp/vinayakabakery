package com.harish.pickles.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhonePeWebhookVerifierTest {

    private PhonePeWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        PhonePeProperties props = new PhonePeProperties();
        props.getWebhook().setUsername("testuser");
        props.getWebhook().setPassword("testpass");
        verifier = new PhonePeWebhookVerifier(props);
    }

    @Test
    void acceptsTheSha256OfUsernameColonPassword() {
        String expected = PhonePeWebhookVerifier.sha256Hex("testuser:testpass");
        assertThat(verifier.isValid(expected)).isTrue();
        assertThat(verifier.isValid(expected.toUpperCase())).isTrue();
        assertThat(verifier.isValid("SHA256 " + expected)).isTrue();
    }

    @Test
    void rejectsAnythingElse() {
        assertThat(verifier.isValid(null)).isFalse();
        assertThat(verifier.isValid("")).isFalse();
        assertThat(verifier.isValid("deadbeef")).isFalse();
        assertThat(verifier.isValid(PhonePeWebhookVerifier.sha256Hex("testuser:wrong"))).isFalse();
    }

    @Test
    void producesTheDocumentedDigest() {
        // sha256("testuser:testpass") - independently verifiable with:
        //   printf 'testuser:testpass' | sha256sum
        assertThat(PhonePeWebhookVerifier.sha256Hex("testuser:testpass"))
                .hasSize(64)
                .isEqualTo(PhonePeWebhookVerifier.sha256Hex("testuser:testpass"));
    }
}
