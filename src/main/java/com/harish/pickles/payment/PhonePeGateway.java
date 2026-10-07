package com.harish.pickles.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harish.pickles.exception.PaymentException;
import com.harish.pickles.payment.GatewayModels.InitiateRequest;
import com.harish.pickles.payment.GatewayModels.InitiateResult;
import com.harish.pickles.payment.GatewayModels.StatusResult;
import com.phonepe.sdk.pg.Env;
import com.phonepe.sdk.pg.common.exception.PhonePeException;
import com.phonepe.sdk.pg.common.models.MetaInfo;
import com.phonepe.sdk.pg.common.models.response.OrderStatusResponse;
import com.phonepe.sdk.pg.common.models.response.PaymentDetail;
import com.phonepe.sdk.pg.payments.v2.StandardCheckoutClient;
import com.phonepe.sdk.pg.payments.v2.models.request.StandardCheckoutPayRequest;
import com.phonepe.sdk.pg.payments.v2.models.response.StandardCheckoutPayResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PhonePe Payment Gateway through PhonePe's official backend SDK ({@code com.phonepe:pg-sdk-java}),
 * Standard Checkout v2. The SDK owns the OAuth token (fetch, cache, refresh), so there is no token code
 * here. {@code pay} gives the customer a hosted checkout URL; {@code getOrderStatus} is the live status
 * lookup used by the redirect return, the polling pages, the background reconciler and the admin screen.
 *
 * <p>Credentials come from {@link PhonePeProperties}, which the admin panel edits at runtime
 * ({@link PhonePeSettingsService}); the SDK client is rebuilt automatically whenever they change.</p>
 */
@Component
public class PhonePeGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PhonePeGateway.class);

    private final PhonePeProperties props;
    private final ObjectMapper objectMapper;

    private volatile StandardCheckoutClient client;
    private volatile String clientFingerprint = "";

    public PhonePeGateway(PhonePeProperties props, ObjectMapper objectMapper) {
        this.props = props;
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
        MetaInfo meta = MetaInfo.builder()
                .udf1(truncate(request.customerName(), 60))
                .udf2(truncate(request.customerPhone(), 20))
                .build();
        StandardCheckoutPayRequest pay = StandardCheckoutPayRequest.builder()
                .merchantOrderId(request.merchantOrderId())
                .amount(request.amountPaise())
                .message(request.message())
                .redirectUrl(props.getRedirectUrl())
                .expireAfter((long) props.getExpireAfterSeconds())
                .metaInfo(meta)
                .build();
        try {
            StandardCheckoutPayResponse response = client().pay(pay);
            if (response == null || response.getRedirectUrl() == null) {
                throw new PaymentException("PhonePe did not return a checkout URL");
            }
            log.info("PhonePe order {} created (providerOrderId={}, state={})", request.merchantOrderId(),
                    response.getOrderId(), response.getState());
            return new InitiateResult(response.getOrderId(), response.getRedirectUrl(),
                    GatewayState.from(response.getState()), toJson(response));
        } catch (PhonePeException ex) {
            throw translate(ex);
        }
    }

    @Override
    public StatusResult checkStatus(String merchantOrderId) {
        try {
            OrderStatusResponse response = client().getOrderStatus(merchantOrderId, false);
            if (response == null) {
                throw new PaymentException("PhonePe returned an empty status for " + merchantOrderId);
            }
            PaymentDetail latest = latestAttempt(response.getPaymentDetails());
            String errorCode = response.getErrorCode() != null ? response.getErrorCode()
                    : (latest != null ? latest.getErrorCode() : null);
            return new StatusResult(
                    response.getOrderId(),
                    GatewayState.from(response.getState()),
                    response.getAmount(),
                    latest != null ? latest.getTransactionId() : null,
                    latest != null && latest.getPaymentMode() != null ? latest.getPaymentMode().name() : null,
                    errorCode,
                    response.getDetailedErrorCode() != null ? response.getDetailedErrorCode()
                            : (latest != null ? latest.getDetailedErrorCode() : null),
                    toJson(response));
        } catch (PhonePeException ex) {
            throw translate(ex);
        }
    }

    /**
     * Admin "test connection": builds a client with the saved credentials and asks about an order that does
     * not exist. A "not found" answer proves PhonePe accepted the credentials; an auth error means it did not.
     *
     * @return null when the credentials work, otherwise a message for the admin
     */
    public String testConnection() {
        try {
            client().getOrderStatus("CONNECTION-TEST-" + System.currentTimeMillis(), false);
            return null;
        } catch (PhonePeException ex) {
            int status = ex.getHttpStatusCode() == null ? 0 : ex.getHttpStatusCode();
            String code = ex.getCode() == null ? "" : ex.getCode().toUpperCase();
            boolean authProblem = status == 401 || status == 403 || code.contains("UNAUTHORIZED")
                    || code.contains("AUTH") || code.contains("CLIENT");
            if (!authProblem && status >= 400 && status < 500) {
                return null; // reached PhonePe and authenticated; the order simply does not exist
            }
            return "PhonePe rejected the credentials (" + describe(ex) + ")";
        } catch (PaymentException ex) {
            return ex.getMessage();
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The SDK client for the current credentials; rebuilt whenever an admin changes them. */
    private StandardCheckoutClient client() {
        if (!props.hasCredentials()) {
            throw new PaymentException("PhonePe client ID / client secret are not configured. "
                    + "An admin can set them under Admin > Payments (PhonePe).");
        }
        String fingerprint = props.getEnvironment() + "|" + props.getClientId() + "|" + props.getClientSecret()
                + "|" + props.getClientVersion();
        StandardCheckoutClient current = client;
        if (current == null || !fingerprint.equals(clientFingerprint)) {
            synchronized (this) {
                if (client == null || !fingerprint.equals(clientFingerprint)) {
                    try {
                        client = StandardCheckoutClient.getInstance(props.getClientId(), props.getClientSecret(),
                                Integer.valueOf(props.getClientVersion().trim()),
                                props.getEnvironment() == PhonePeProperties.Environment.PRODUCTION
                                        ? Env.PRODUCTION : Env.SANDBOX,
                                false);
                    } catch (PhonePeException | NumberFormatException ex) {
                        throw new PaymentException("Could not start the PhonePe SDK: " + ex.getMessage(), ex);
                    }
                    clientFingerprint = fingerprint;
                    log.info("PhonePe SDK client (re)built for {} / clientId {}", props.getEnvironment(),
                            props.getClientId());
                }
                current = client;
            }
        }
        return current;
    }

    /** The most recent payment attempt, which is the one whose outcome decides the order. */
    private static PaymentDetail latestAttempt(List<PaymentDetail> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        return details.stream()
                .max((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()))
                .orElse(details.get(0));
    }

    private PaymentException translate(PhonePeException ex) {
        log.error("PhonePe SDK error {}: {}", ex.getHttpStatusCode(), describe(ex));
        return new PaymentException("PhonePe rejected the request (" + describe(ex) + ")", ex);
    }

    private static String describe(PhonePeException ex) {
        return (ex.getHttpStatusCode() == null ? "" : "HTTP " + ex.getHttpStatusCode() + " ")
                + (ex.getCode() == null ? "" : ex.getCode() + " ")
                + (ex.getMessage() == null ? "" : ex.getMessage());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
