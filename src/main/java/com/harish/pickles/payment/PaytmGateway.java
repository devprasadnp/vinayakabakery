package com.harish.pickles.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harish.pickles.exception.PaymentException;
import com.harish.pickles.payment.GatewayModels.InitiateRequest;
import com.harish.pickles.payment.GatewayModels.InitiateResult;
import com.harish.pickles.payment.GatewayModels.StatusResult;
import com.paytm.pg.merchant.PaytmChecksum;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Paytm Payment Gateway - Dynamic QR Code. Active when {@code payments.mode=PAYTM} and
 * {@code payments.paytm.mock=false}; {@link MockPaytmGateway} stands in otherwise.
 *
 * <pre>
 * POST {base}/paymentservices/qr/create  -> qrData (a upi://pay URI) + qrCodeId
 * POST {base}/v3/order/status            -> TXN_SUCCESS | TXN_FAILURE | PENDING
 * </pre>
 *
 * Unlike PhonePe's Standard Checkout, Paytm never hands us a hosted checkout page to redirect
 * to - {@code qrData} is rendered into our own {@code /orders/{orderNumber}/pay-qr} page as a
 * QR code (the same page the manual UPI-QR flow uses), so {@link InitiateResult#redirectUrl()}
 * here is one of our own relative paths, not an external URL.
 *
 * <p>Every request body is signed with the official {@code paytm-checksum} library using the
 * merchant key; Paytm signs its response back the same way, but that response arrives over the
 * TLS connection we opened to Paytm's own host, so it is trusted without a second check here -
 * the signature matters most on the {@link com.harish.pickles.web.rest.PaymentWebhookController}
 * side, where the server-to-server callback could otherwise be forged by anyone who finds the URL.</p>
 */
@Component
@ConditionalOnExpression("'${payments.mode:QR}' == 'PAYTM' and ${payments.paytm.mock:true} == false")
public class PaytmGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PaytmGateway.class);

    private final PaytmProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PaytmGateway(PaytmProperties props, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.props = props;
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "PAYTM";
    }

    @Override
    public InitiateResult initiate(InitiateRequest request) {
        if (!props.hasCredentials()) {
            throw new PaymentException("Paytm is not configured (payments.paytm.mid / merchant-key missing)");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("mid", props.getMid());
        body.put("orderId", request.merchantOrderId());
        body.put("amount", toAmountString(request.amountPaise()));
        body.put("businessType", "UPI_QR_CODE");
        body.put("posId", props.getPosId());
        if (request.message() != null) {
            body.put("orderDetails", truncate(request.message(), 100));
        }
        if (hasText(request.customerPhone())) {
            body.put("contactPhoneNo", request.customerPhone());
        }

        JsonNode response = post(props.qrCreateUrl(), envelope(body, true));
        JsonNode respBody = response.path("body");
        String resultStatus = respBody.path("resultInfo").path("resultStatus").asText("");
        if (!"SUCCESS".equalsIgnoreCase(resultStatus)) {
            throw new PaymentException("Paytm did not create a QR code: "
                    + respBody.path("resultInfo").path("resultMsg").asText("unknown error"));
        }
        String qrCodeId = textOrNull(respBody, "qrCodeId");
        String qrData = textOrNull(respBody, "qrData");
        if (qrData == null) {
            throw new PaymentException("Paytm did not return QR data for order " + request.merchantOrderId());
        }
        log.info("Paytm QR created for order {} (qrCodeId={})", request.merchantOrderId(), qrCodeId);

        return new InitiateResult(qrCodeId, "/orders/" + request.merchantOrderId() + "/pay-qr",
                GatewayState.PENDING, response.toString(), qrData);
    }

    @Override
    public StatusResult checkStatus(String merchantOrderId) {
        Map<String, Object> body = Map.of("mid", props.getMid(), "orderId", merchantOrderId);
        JsonNode response = post(props.orderStatusUrl(), envelope(body, false));
        JsonNode respBody = response.path("body");
        String resultStatus = respBody.path("resultInfo").path("resultStatus").asText("PENDING");

        Long amountPaise = null;
        String txnAmount = textOrNull(respBody, "txnAmount");
        if (txnAmount != null) {
            amountPaise = new BigDecimal(txnAmount).multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        boolean failed = GatewayState.from(resultStatus) == GatewayState.FAILED;

        return new StatusResult(
                textOrNull(respBody, "orderId") != null ? textOrNull(respBody, "orderId") : merchantOrderId,
                GatewayState.from(resultStatus),
                amountPaise,
                textOrNull(respBody, "txnId"),
                textOrNull(respBody, "paymentMode"),
                failed ? respBody.path("resultInfo").path("resultCode").asText(null) : null,
                failed ? respBody.path("resultInfo").path("resultMsg").asText(null) : null,
                response.toString());
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> envelope(Map<String, Object> body, boolean includeClientId) {
        String bodyJson = toJson(body);
        Map<String, Object> head = new LinkedHashMap<>();
        if (includeClientId) {
            head.put("clientId", "C11");
        }
        head.put("version", "v1");
        head.put("requestTimestamp", String.valueOf(Instant.now().getEpochSecond()));
        head.put("channelId", props.getChannelId());
        head.put("signature", sign(bodyJson));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("head", head);
        envelope.put("body", body);
        return envelope;
    }

    private JsonNode post(String url, Object body) {
        try {
            String raw = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(raw);
        } catch (RestClientResponseException ex) {
            log.error("Paytm API error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new PaymentException("Paytm rejected the request (HTTP " + ex.getStatusCode().value() + ")", ex);
        } catch (PaymentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentException("Could not read Paytm's response", ex);
        }
    }

    private String sign(String bodyJson) {
        try {
            return PaytmChecksum.generateSignature(bodyJson, props.getMerchantKey());
        } catch (Exception ex) {
            throw new PaymentException("Could not sign the Paytm request", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new PaymentException("Could not build the Paytm request", ex);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String toAmountString(long amountPaise) {
        return BigDecimal.valueOf(amountPaise, 2).toPlainString();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
