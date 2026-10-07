package com.harish.pickles.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts gateway secrets (PhonePe client secret, webhook password) before they are written to the
 * database, so a database dump alone does not reveal them. AES-256-GCM; the key is derived from
 * {@code PAYMENTS_SECRET_KEY} when set, otherwise from the JWT signing secret. Changing that key makes
 * stored secrets unreadable - an admin simply re-enters them on the PhonePe settings screen.
 */
@Component
public class SecretCipher {

    private static final String PREFIX = "enc:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SecretCipher(@Value("${payments.secret-key:}") String configuredKey,
                        @Value("${app.security.jwt.secret}") String jwtSecret) {
        String material = configuredKey == null || configuredKey.isBlank() ? jwtSecret : configuredKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt secret", ex);
        }
    }

    /** Returns null when the value cannot be decrypted (key changed), so callers treat it as "not set". */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return null;
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, in, 0, 12));
            return new String(cipher.doFinal(in, 12, in.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            return null;
        }
    }
}
