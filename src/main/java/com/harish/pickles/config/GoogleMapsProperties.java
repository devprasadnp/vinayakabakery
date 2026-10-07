package com.harish.pickles.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Maps keys. The defaults come from {@code GOOGLE_MAPS_API_KEY}; an Admin or Super Admin can
 * change both from the admin panel at runtime ({@code GoogleMapsSettingsService}).
 *
 * <ul>
 *   <li><b>Browser key</b> ({@code apiKey}) - "Maps JavaScript API", shown in page source, so restrict it to
 *       this site's domain(s). Draws the checkout pin map, the store map and the courier tracking map.</li>
 *   <li><b>Server key</b> ({@code serverApiKey}, optional) - "Distance Matrix API" and "Geocoding API",
 *       called from this server, so restrict it by server IP, not by website. A domain-restricted browser
 *       key is refused for those calls; when no server key is set the browser key is tried instead.</li>
 * </ul>
 * Leaving the browser key blank disables every map - checkout falls back to address-only and the
 * server-side checks (distance, PIN/landmark vs pin) are skipped, with nothing broken.
 */
@ConfigurationProperties(prefix = "google.maps")
public class GoogleMapsProperties {

    private String apiKey = "";
    private String serverApiKey = "";

    /** Maps (browser) key present: maps are drawn and a delivery pin is asked for. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** The key for server-to-server calls: the server key, else the browser key. */
    public String effectiveServerKey() {
        return serverApiKey != null && !serverApiKey.isBlank() ? serverApiKey : apiKey;
    }

    public boolean isServerConfigured() {
        String key = effectiveServerKey();
        return key != null && !key.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServerApiKey() {
        return serverApiKey;
    }

    public void setServerApiKey(String serverApiKey) {
        this.serverApiKey = serverApiKey;
    }
}
