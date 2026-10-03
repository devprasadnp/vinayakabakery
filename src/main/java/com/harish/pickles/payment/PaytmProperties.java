package com.harish.pickles.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials and endpoints for Paytm Payment Gateway - Dynamic QR Code. Issued on the
 * Paytm Business dashboard (Developer Settings -> API Keys). Never commit real values -
 * the merchant key lives in the git-ignored {@code .env} file, same as the DB password.
 */
@ConfigurationProperties(prefix = "payments.paytm")
public class PaytmProperties {

    public enum Environment {
        STAGING("https://securegw-stage.paytmpayments.in"),
        PRODUCTION("https://securegw.paytmpayments.in");

        private final String baseUrl;

        Environment(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getBaseUrl() {
            return baseUrl;
        }
    }

    /** When true no HTTP call is made - a local mock checkout screen stands in for Paytm. */
    private boolean mock = true;

    private Environment environment = Environment.STAGING;

    /** Merchant ID (MID), from the Paytm dashboard. */
    private String mid;

    /** Merchant Key, used to sign every request/response. Server-side only, never shared. */
    private String merchantKey;

    /** The merchant "Website" configured on the dashboard, e.g. WEBSTAGING or DEFAULT. */
    private String website = "WEBSTAGING";

    /** Industry type configured on the dashboard, e.g. Retail. */
    private String industryType = "Retail";

    /** "WEB" or "WAP" - the channel the checkout runs on. */
    private String channelId = "WEB";

    /** Unique per billing point, format "StoreId_PosId" is common; a single online store can use one. */
    private String posId = "STORE1";

    /** Where Paytm posts the server-to-server payment notification. */
    private String callbackUrl = "http://localhost:8080/api/payments/paytm/webhook";

    /** How long a generated QR stays valid, handed to Paytm as the QR's own expiry. */
    private int expireAfterSeconds = 600;

    public String qrCreateUrl() {
        return environment.getBaseUrl() + "/paymentservices/qr/create";
    }

    public String orderStatusUrl() {
        return environment.getBaseUrl() + "/v3/order/status";
    }

    public boolean hasCredentials() {
        return hasText(mid) && hasText(merchantKey);
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

    public String getMid() {
        return mid;
    }

    public void setMid(String mid) {
        this.mid = mid;
    }

    public String getMerchantKey() {
        return merchantKey;
    }

    public void setMerchantKey(String merchantKey) {
        this.merchantKey = merchantKey;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getIndustryType() {
        return industryType;
    }

    public void setIndustryType(String industryType) {
        this.industryType = industryType;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getPosId() {
        return posId;
    }

    public void setPosId(String posId) {
        this.posId = posId;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public int getExpireAfterSeconds() {
        return expireAfterSeconds;
    }

    public void setExpireAfterSeconds(int expireAfterSeconds) {
        this.expireAfterSeconds = expireAfterSeconds;
    }
}
