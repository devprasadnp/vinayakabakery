package com.harish.pickles.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Maps JavaScript/Embed API key, used to drop a delivery pin at checkout and to show the
 * store's own location on the storefront and in the admin panel. Create a key in Google Cloud
 * Console with the "Maps JavaScript API" enabled and billing on, then restrict it to this site's
 * domain(s). Leaving it blank disables every map - the checkout address form and the store
 * location admin screen both fall back to plain text/number fields, with nothing broken.
 */
@ConfigurationProperties(prefix = "google.maps")
public class GoogleMapsProperties {

    private String apiKey = "";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
