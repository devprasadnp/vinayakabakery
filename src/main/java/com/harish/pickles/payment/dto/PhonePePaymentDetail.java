package com.harish.pickles.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PhonePePaymentDetail(
        String paymentMode,
        String transactionId,
        Long timestamp,
        Long amount,
        String state,
        String errorCode,
        String detailedErrorCode,
        Map<String, Object> instrument,
        Map<String, Object> rail) {
}
