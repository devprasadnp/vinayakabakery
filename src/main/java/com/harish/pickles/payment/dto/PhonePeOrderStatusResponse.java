package com.harish.pickles.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/** Response of GET /checkout/v2/order/{merchantOrderId}/status. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PhonePeOrderStatusResponse(
        String orderId,
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
