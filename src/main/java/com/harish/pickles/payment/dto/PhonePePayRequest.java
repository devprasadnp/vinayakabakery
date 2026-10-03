package com.harish.pickles.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Body of POST /checkout/v2/pay. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PhonePePayRequest(
        String merchantOrderId,
        long amount,
        Integer expireAfter,
        Map<String, String> metaInfo,
        PaymentFlow paymentFlow) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PaymentFlow(String type, String message, MerchantUrls merchantUrls) {
    }

    public record MerchantUrls(String redirectUrl) {
    }

    public static PhonePePayRequest checkout(String merchantOrderId, long amountPaise, String message,
                                             String redirectUrl, Integer expireAfter, Map<String, String> metaInfo) {
        return new PhonePePayRequest(merchantOrderId, amountPaise, expireAfter, metaInfo,
                new PaymentFlow("PG_CHECKOUT", message, new MerchantUrls(redirectUrl)));
    }
}
