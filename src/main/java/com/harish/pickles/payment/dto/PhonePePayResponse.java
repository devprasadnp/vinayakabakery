package com.harish.pickles.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response of POST /checkout/v2/pay. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PhonePePayResponse(
        String orderId,
        String state,
        Long expireAt,
        String redirectUrl) {
}
