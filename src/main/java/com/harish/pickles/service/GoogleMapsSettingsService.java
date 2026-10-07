package com.harish.pickles.service;

import com.harish.pickles.config.GoogleMapsProperties;
import com.harish.pickles.domain.AppSetting;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.payment.SecretCipher;
import com.harish.pickles.repository.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Google Maps keys an admin can change without a redeploy. Saved in {@code app_settings} (the server key
 * encrypted) and applied straight onto the live {@link GoogleMapsProperties}, over the environment default.
 * A saved blank browser key means "maps off" and is kept as an empty value, so it is not overridden by the
 * environment default on the next start.
 */
@Service
public class GoogleMapsSettingsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleMapsSettingsService.class);

    static final String BROWSER_KEY = "google.maps.browser-key";
    static final String SERVER_KEY = "google.maps.server-key";

    public record View(String browserKey, boolean serverKeySet) {
    }

    private final AppSettingRepository settings;
    private final GoogleMapsProperties maps;
    private final SecretCipher cipher;

    public GoogleMapsSettingsService(AppSettingRepository settings, GoogleMapsProperties maps, SecretCipher cipher) {
        this.settings = settings;
        this.maps = maps;
        this.cipher = cipher;
    }

    /** Applies keys an admin saved earlier, over the environment default. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void applyStored() {
        try {
            settings.findById(BROWSER_KEY).map(AppSetting::getValue)
                    .map(v -> "-".equals(v) ? "" : v).ifPresent(maps::setApiKey);
            settings.findById(SERVER_KEY).map(AppSetting::getValue).map(cipher::decrypt)
                    .ifPresent(maps::setServerApiKey);
        } catch (RuntimeException ex) {
            log.warn("Could not load stored Google Maps keys, using configuration defaults: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public View view() {
        String server = maps.getServerApiKey();
        return new View(maps.getApiKey() == null ? "" : maps.getApiKey(), server != null && !server.isBlank());
    }

    /**
     * @param browserKey blank switches maps off
     * @param serverKey blank keeps the saved server key
     * @param clearServerKey true removes it (server calls then use the browser key)
     */
    @Transactional
    public void save(String browserKey, String serverKey, boolean clearServerKey) {
        String browser = browserKey == null ? "" : browserKey.trim();
        String server = serverKey == null ? "" : serverKey.trim();
        if (browser.length() > 200 || server.length() > 150) {
            throw new BusinessException("That does not look like a Google API key");
        }
        // "-" marks an explicit "off", so the environment default does not come back after a restart.
        put(BROWSER_KEY, browser.isEmpty() ? "-" : browser);
        maps.setApiKey(browser);
        if (clearServerKey) {
            settings.findById(SERVER_KEY).ifPresent(settings::delete);
            maps.setServerApiKey("");
        } else if (!server.isEmpty()) {
            put(SERVER_KEY, cipher.encrypt(server));
            maps.setServerApiKey(server);
        }
        log.info("Google Maps keys updated: browser key {}, server key {}", browser.isEmpty() ? "off" : "set",
                maps.isServerConfigured() ? "available" : "none");
    }

    private void put(String key, String value) {
        AppSetting s = settings.findById(key).orElseGet(() -> new AppSetting(key, value));
        s.setValue(value);
        settings.save(s);
    }
}
