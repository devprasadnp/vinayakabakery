package com.harish.pickles.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials and endpoints for PhonePe Payment Gateway - Standard Checkout v2.
 * Client id / secret / version are issued in the PhonePe Business dashboard
 * (Developer Settings -> API keys). Never commit real values.
 */
@ConfigurationProperties(prefix = "payments.phonepe")
public class PhonePeProperties {

    public enum Environment {
        /** UAT / pre-production sandbox. */
        SANDBOX("https://api-preprod.phonepe.com/apis/pg-sandbox/v1/oauth/token",
                "https://api-preprod.phonepe.com/apis/pg-sandbox"),
        PRODUCTION("https://api.phonepe.com/apis/identity-manager/v1/oauth/token",
                "https://api.phonepe.com/apis/pg");

        private final String authUrl;
        private final String apiBaseUrl;

        Environment(String authUrl, String apiBaseUrl) {
            this.authUrl = authUrl;
            this.apiBaseUrl = apiBaseUrl;
        }

        public String getAuthUrl() {
            return authUrl;
        }

        public String getApiBaseUrl() {
            return apiBaseUrl;
        }
    }

    /** When true no HTTP call is made - a local mock checkout screen stands in for PhonePe. */
    private boolean mock = true;

    private Environment environment = Environment.SANDBOX;
    private String clientId;
    private String clientSecret;
    private String clientVersion = "1";

    /** Where PhonePe sends the customer's browser back to after the payment attempt. */
    private String redirectUrl = "http://localhost:8080/payment/callback";

    /** Order validity handed to PhonePe. Allowed range 300-3600 seconds. */
    private int expireAfterSeconds = 1200;

    /** Optional overrides; leave blank to use the values baked into {@link Environment}. */
    private String authUrlOverride;
    private String apiBaseUrlOverride;

    private Webhook webhook = new Webhook();

    public static class Webhook {
        /** Same username configured on the PhonePe dashboard webhook screen. */
        private String username = "vinayakabakery";
        private String password = "change-me";

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public String resolveAuthUrl() {
        return hasText(authUrlOverride) ? authUrlOverride : environment.getAuthUrl();
    }

    public String resolveApiBaseUrl() {
        return hasText(apiBaseUrlOverride) ? apiBaseUrlOverride : environment.getApiBaseUrl();
    }

    public String payUrl() {
        return resolveApiBaseUrl() + "/checkout/v2/pay";
    }

    public String orderStatusUrl(String merchantOrderId) {
        return resolveApiBaseUrl() + "/checkout/v2/order/" + merchantOrderId + "/status?details=false&errorContext=true";
    }

    public boolean hasCredentials() {
        return hasText(clientId) && hasText(clientSecret);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public int getExpireAfterSeconds() {
        return expireAfterSeconds;
    }

    public void setExpireAfterSeconds(int expireAfterSeconds) {
        this.expireAfterSeconds = expireAfterSeconds;
    }

    public String getAuthUrlOverride() {
        return authUrlOverride;
    }

    public void setAuthUrlOverride(String authUrlOverride) {
        this.authUrlOverride = authUrlOverride;
    }

    public String getApiBaseUrlOverride() {
        return apiBaseUrlOverride;
    }

    public void setApiBaseUrlOverride(String apiBaseUrlOverride) {
        this.apiBaseUrlOverride = apiBaseUrlOverride;
    }

    public Webhook getWebhook() {
        return webhook;
    }

    public void setWebhook(Webhook webhook) {
        this.webhook = webhook;
    }
}
