package com.harish.pickles.payment;

import com.harish.pickles.domain.AppSetting;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.payment.PaymentProperties.Mode;
import com.harish.pickles.payment.PhonePeProperties.Environment;
import com.harish.pickles.repository.AppSettingRepository;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PhonePe configuration that an admin can change from the admin panel without a redeploy.
 *
 * <p>Values are kept in {@code app_settings} (secrets encrypted by {@link SecretCipher}) and applied to the
 * live {@link PhonePeProperties} / {@link PaymentProperties} beans, so the gateway, the webhook verifier and
 * the checkout pages all see a change immediately. Anything an admin has not set falls back to the value from
 * {@code application.yml} / environment variables, which is also what the first start uses.</p>
 */
@Service
public class PhonePeSettingsService {

    private static final Logger log = LoggerFactory.getLogger(PhonePeSettingsService.class);

    static final String MODE = "payments.mode";
    static final String MOCK = "payments.phonepe.mock";
    static final String ENVIRONMENT = "payments.phonepe.environment";
    static final String CLIENT_ID = "payments.phonepe.client-id";
    static final String CLIENT_SECRET = "payments.phonepe.client-secret";
    static final String CLIENT_VERSION = "payments.phonepe.client-version";
    static final String REDIRECT_URL = "payments.phonepe.redirect-url";
    static final String EXPIRE_AFTER = "payments.phonepe.expire-seconds";
    static final String WEBHOOK_USER = "payments.phonepe.webhook-user";
    static final String WEBHOOK_PASSWORD = "payments.phonepe.webhook-password";

    /** What the admin form submits. Blank secrets mean "keep the stored one". */
    public record Update(boolean usePhonePe, boolean mock, Environment environment, String clientId,
                         String clientSecret, String clientVersion, String redirectUrl, int expireAfterSeconds,
                         String webhookUsername, String webhookPassword) {
    }

    /** What the admin screen shows; never contains a secret. */
    public record View(boolean usePhonePe, Mode mode, boolean mock, Environment environment, String clientId,
                       boolean secretSet, String clientVersion, String redirectUrl, int expireAfterSeconds,
                       String webhookUsername, boolean webhookPasswordSet) {
    }

    private final AppSettingRepository settings;
    private final PhonePeProperties phonePe;
    private final PaymentProperties payments;
    private final SecretCipher cipher;

    public PhonePeSettingsService(AppSettingRepository settings, PhonePeProperties phonePe,
                                  PaymentProperties payments, SecretCipher cipher) {
        this.settings = settings;
        this.phonePe = phonePe;
        this.payments = payments;
        this.cipher = cipher;
    }

    /** Applies whatever an admin saved earlier, over the yml/env defaults. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void applyStored() {
        try {
            loadInto();
            log.info("PhonePe settings loaded: mode={}, mock={}, environment={}", payments.getMode(),
                    phonePe.isMock(), phonePe.getEnvironment());
        } catch (RuntimeException ex) {
            log.warn("Could not load stored PhonePe settings, using configuration defaults: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public View view() {
        return new View(payments.getMode() == Mode.PHONEPE, payments.getMode(), phonePe.isMock(),
                phonePe.getEnvironment(), nullToEmpty(phonePe.getClientId()), notBlank(phonePe.getClientSecret()),
                nullToEmpty(phonePe.getClientVersion()), nullToEmpty(phonePe.getRedirectUrl()),
                phonePe.getExpireAfterSeconds(), nullToEmpty(phonePe.getWebhook().getUsername()),
                notBlank(phonePe.getWebhook().getPassword()));
    }

    @Transactional
    public void save(Update u) {
        String clientId = trim(u.clientId());
        String version = trim(u.clientVersion());
        String redirect = trim(u.redirectUrl());
        String webhookUser = trim(u.webhookUsername());
        if (u.expireAfterSeconds() < 300 || u.expireAfterSeconds() > 3600) {
            throw new BusinessException("Order expiry must be between 300 and 3600 seconds");
        }
        if (!version.isEmpty() && !version.matches("\\d{1,4}")) {
            throw new BusinessException("Client version must be a number, e.g. 1");
        }
        if (!redirect.isEmpty() && !redirect.matches("(?i)https?://\\S+")) {
            throw new BusinessException("Redirect URL must start with http:// or https://");
        }
        boolean live = u.usePhonePe() && !u.mock();
        String secret = trim(u.clientSecret()).isEmpty() ? phonePe.getClientSecret() : trim(u.clientSecret());
        String webhookPassword = trim(u.webhookPassword()).isEmpty()
                ? phonePe.getWebhook().getPassword() : trim(u.webhookPassword());
        if (live) {
            if (clientId.isEmpty() || secret == null || secret.isBlank()) {
                throw new BusinessException("Enter the PhonePe client ID and client secret to go live");
            }
            if (redirect.isEmpty()) {
                throw new BusinessException("Enter the redirect URL PhonePe should send customers back to");
            }
            if (u.environment() == Environment.PRODUCTION && !redirect.toLowerCase().startsWith("https://")) {
                throw new BusinessException("The production redirect URL must use https://");
            }
            if (webhookUser.isEmpty() || webhookPassword == null || webhookPassword.isBlank()) {
                throw new BusinessException("Enter the webhook username and password you set on the PhonePe dashboard");
            }
        }

        write(MOCK, Boolean.toString(u.mock()));
        write(ENVIRONMENT, u.environment().name());
        write(CLIENT_ID, clientId);
        write(CLIENT_VERSION, version.isEmpty() ? "1" : version);
        write(REDIRECT_URL, redirect);
        write(EXPIRE_AFTER, Integer.toString(u.expireAfterSeconds()));
        write(WEBHOOK_USER, webhookUser);
        if (!trim(u.clientSecret()).isEmpty()) {
            write(CLIENT_SECRET, cipher.encrypt(trim(u.clientSecret())));
        }
        if (!trim(u.webhookPassword()).isEmpty()) {
            write(WEBHOOK_PASSWORD, cipher.encrypt(trim(u.webhookPassword())));
        }
        // Switching PhonePe off returns to the manual QR flow, but never overrides a Paytm shop.
        if (u.usePhonePe()) {
            write(MODE, Mode.PHONEPE.name());
        } else if (payments.getMode() == Mode.PHONEPE) {
            write(MODE, Mode.QR.name());
        }
        loadInto();
        log.info("PhonePe settings updated: mode={}, mock={}, environment={}, clientId={}", payments.getMode(),
                phonePe.isMock(), phonePe.getEnvironment(), clientId);
    }

    // ----------------------------------------------------------------- internals

    private void loadInto() {
        read(MODE, v -> payments.setMode(Mode.valueOf(v)));
        read(MOCK, v -> phonePe.setMock(Boolean.parseBoolean(v)));
        read(ENVIRONMENT, v -> phonePe.setEnvironment(Environment.valueOf(v)));
        read(CLIENT_ID, phonePe::setClientId);
        read(CLIENT_VERSION, phonePe::setClientVersion);
        read(REDIRECT_URL, phonePe::setRedirectUrl);
        read(EXPIRE_AFTER, v -> phonePe.setExpireAfterSeconds(Integer.parseInt(v)));
        read(WEBHOOK_USER, v -> phonePe.getWebhook().setUsername(v));
        readSecret(CLIENT_SECRET, phonePe::setClientSecret);
        readSecret(WEBHOOK_PASSWORD, v -> phonePe.getWebhook().setPassword(v));
    }

    private void read(String key, Consumer<String> apply) {
        settings.findById(key).map(AppSetting::getValue).filter(v -> !v.isBlank()).ifPresent(v -> {
            try {
                apply.accept(v);
            } catch (RuntimeException ex) {
                log.warn("Ignoring unreadable stored setting {}", key);
            }
        });
    }

    private void readSecret(String key, Consumer<String> apply) {
        settings.findById(key).map(AppSetting::getValue).map(cipher::decrypt).ifPresentOrElse(apply, () -> {
            if (settings.existsById(key)) {
                log.warn("Stored secret {} cannot be decrypted (encryption key changed?) - re-enter it in the admin panel", key);
            }
        });
    }

    private void write(String key, String value) {
        if (value == null || value.isBlank()) {
            settings.findById(key).ifPresent(settings::delete);
            return;
        }
        AppSetting s = settings.findById(key).orElseGet(() -> new AppSetting(key, value));
        s.setValue(value);
        settings.save(s);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
