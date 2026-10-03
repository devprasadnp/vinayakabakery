package com.harish.pickles.payment;

public final class GatewayModels {

    private GatewayModels() {
    }

    /** What we ask the gateway to charge. */
    public record InitiateRequest(
            String merchantOrderId,
            long amountPaise,
            String message,
            String customerName,
            String customerPhone) {
    }

    /**
     * What the gateway hands back - send the browser to {@code redirectUrl}.
     *
     * <p>{@code qrPayload} is the UPI string behind a gateway-hosted QR (Paytm's {@code qrData}):
     * null for a gateway that hosts its own checkout page instead (PhonePe), in which case
     * {@code redirectUrl} is an external URL rather than one of our own pages.</p>
     */
    public record InitiateResult(
            String providerOrderId,
            String redirectUrl,
            GatewayState state,
            String rawResponse,
            String qrPayload) {

        public InitiateResult(String providerOrderId, String redirectUrl, GatewayState state, String rawResponse) {
            this(providerOrderId, redirectUrl, state, rawResponse, null);
        }
    }

    /** Result of a status enquiry. */
    public record StatusResult(
            String providerOrderId,
            GatewayState state,
            Long amountPaise,
            String transactionId,
            String paymentMode,
            String errorCode,
            String errorMessage,
            String rawResponse) {
    }
}
