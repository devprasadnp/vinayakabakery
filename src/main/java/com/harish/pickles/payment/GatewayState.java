package com.harish.pickles.payment;

/**
 * Normalised gateway states. PhonePe reports PENDING / COMPLETED / FAILED; Paytm reports
 * TXN_SUCCESS / TXN_FAILURE / PENDING (both on the order-status API and the webhook).
 */
public enum GatewayState {
    PENDING,
    COMPLETED,
    FAILED;

    public static GatewayState from(String raw) {
        if (raw == null) {
            return PENDING;
        }
        return switch (raw.trim().toUpperCase()) {
            case "COMPLETED", "SUCCESS", "PAYMENT_SUCCESS", "TXN_SUCCESS" -> COMPLETED;
            case "FAILED", "FAILURE", "PAYMENT_ERROR", "PAYMENT_DECLINED", "EXPIRED",
                    "TXN_FAILURE", "JSON_ERROR" -> FAILED;
            default -> PENDING;
        };
    }
}
