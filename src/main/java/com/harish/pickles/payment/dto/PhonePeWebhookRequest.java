package com.harish.pickles.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * Body PhonePe POSTs to the configured webhook URL.
 * Events: checkout.order.completed, checkout.order.failed, pg.refund.completed, pg.refund.failed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PhonePeWebhookRequest(String event, Payload payload) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(
            String orderId,
            String merchantId,
            String merchantOrderId,
            String originalMerchantOrderId,
            String merchantRefundId,
            String refundId,
            String state,
            Long amount,
            Long expireAt,
            String errorCode,
            String detailedErrorCode,
            Map<String, String> metaInfo,
            List<PhonePePaymentDetail> paymentDetails) {

        public PhonePePaymentDetail latestAttempt() {
            if (paymentDetails == null || paymentDetails.isEmpty()) {
                return null;
            }
            return paymentDetails.get(paymentDetails.size() - 1);
        }
    }
}
