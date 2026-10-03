package com.harish.pickles.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harish.pickles.exception.PaymentException;
import com.harish.pickles.payment.GatewayModels.InitiateRequest;
import com.harish.pickles.payment.GatewayModels.InitiateResult;
import com.harish.pickles.payment.GatewayModels.StatusResult;
import com.harish.pickles.payment.dto.PhonePeOrderStatusResponse;
import com.harish.pickles.payment.dto.PhonePePayRequest;
import com.harish.pickles.payment.dto.PhonePePayResponse;
import com.harish.pickles.payment.dto.PhonePePaymentDetail;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * PhonePe Payment Gateway - Standard Checkout v2.
 *
 * <pre>
 * POST {base}/checkout/v2/pay                              -> redirectUrl for the customer
 * GET  {base}/checkout/v2/order/{merchantOrderId}/status   -> PENDING | COMPLETED | FAILED
 * </pre>
 *
 * Every call carries {@code Authorization: O-Bearer <token>} from {@link PhonePeTokenManager}.
 */
@Component
@ConditionalOnExpression("'${payments.mode:QR}' == 'PHONEPE' and ${payments.phonepe.mock:true} == false")
public class PhonePeGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PhonePeGateway.class);
    private static final String AUTH_PREFIX = "O-Bearer ";

    private final PhonePeProperties props;
    private final PhonePeTokenManager tokenManager;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PhonePeGateway(PhonePeProperties props, PhonePeTokenManager tokenManager,
                          RestClient.Builder builder, ObjectMapper objectMapper) {
        this.props = props;
        this.tokenManager = tokenManager;
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "PHONEPE";
    }

    @Override
    public InitiateResult initiate(InitiateRequest request) {
        if (request.amountPaise() < 100) {
            throw new PaymentException("PhonePe requires a minimum order value of INR 1.00");
        }
        Map<String, String> metaInfo = new LinkedHashMap<>();
        if (request.customerName() != null) {
            metaInfo.put("udf1", truncate(request.customerName(), 60));
        }
        if (request.customerPhone() != null) {
            metaInfo.put("udf2", truncate(request.customerPhone(), 20));
        }

        PhonePePayRequest body = PhonePePayRequest.checkout(
                request.merchantOrderId(),
                request.amountPaise(),
                request.message(),
                props.getRedirectUrl(),
                props.getExpireAfterSeconds(),
                metaInfo.isEmpty() ? null : metaInfo);

        PhonePePayResponse response = post(props.payUrl(), body, PhonePePayResponse.class, true);
        if (response == null || response.redirectUrl() == null) {
            throw new PaymentException("PhonePe did not return a checkout URL");
        }
        log.info("PhonePe order {} created (providerOrderId={}, state={})",
                request.merchantOrderId(), response.orderId(), response.state());

        return new InitiateResult(response.orderId(), response.redirectUrl(),
                GatewayState.from(response.state()), toJson(response));
    }

    @Override
    public StatusResult checkStatus(String merchantOrderId) {
        PhonePeOrderStatusResponse response = get(props.orderStatusUrl(merchantOrderId),
                PhonePeOrderStatusResponse.class, true);
        if (response == null) {
            throw new PaymentException("PhonePe returned an empty status for " + merchantOrderId);
        }
        PhonePePaymentDetail latest = response.latestAttempt();
        String errorCode = response.errorCode() != null ? response.errorCode()
                : (latest != null ? latest.errorCode() : null);

        return new StatusResult(
                response.orderId(),
                GatewayState.from(response.state()),
                response.amount(),
                latest != null ? latest.transactionId() : null,
                latest != null ? latest.paymentMode() : null,
                errorCode,
                response.detailedErrorCode(),
                toJson(response));
    }

    // ---------------------------------------------------------------- helpers

    private <T> T post(String url, Object body, Class<T> type, boolean retryOnUnauthorized) {
        try {
            return restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, AUTH_PREFIX + tokenManager.getAccessToken())
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 && retryOnUnauthorized) {
                tokenManager.invalidate();
                return post(url, body, type, false);
            }
            throw translate(ex);
        }
    }

    private <T> T get(String url, Class<T> type, boolean retryOnUnauthorized) {
        try {
            return restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, AUTH_PREFIX + tokenManager.getAccessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 && retryOnUnauthorized) {
                tokenManager.invalidate();
                return get(url, type, false);
            }
            throw translate(ex);
        }
    }

    private PaymentException translate(RestClientResponseException ex) {
        log.error("PhonePe API error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        return new PaymentException("PhonePe rejected the request (HTTP " + ex.getStatusCode().value() + ")", ex);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
