package com.harish.pickles.payment;

import com.harish.pickles.payment.GatewayModels.InitiateRequest;
import com.harish.pickles.payment.GatewayModels.InitiateResult;
import com.harish.pickles.payment.GatewayModels.StatusResult;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Stand-in for Paytm so the QR checkout can be exercised before a merchant account is live.
 * Active while {@code payments.mode=PAYTM} and {@code payments.paytm.mock=true} (the default).
 * Redirects to an in-app page ({@code /mock-gateway/paytm/{merchantOrderId}}) offering "pay" and
 * "fail" buttons, exactly like {@link MockGateway} does for PhonePe.
 */
@Component
@ConditionalOnExpression("'${payments.mode:QR}' == 'PAYTM' and ${payments.paytm.mock:true} == true")
public class MockPaytmGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaytmGateway.class);

    private final Map<String, GatewayState> states = new ConcurrentHashMap<>();
    private final Map<String, Long> amounts = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "PAYTM_MOCK";
    }

    @Override
    public InitiateResult initiate(InitiateRequest request) {
        states.put(request.merchantOrderId(), GatewayState.PENDING);
        amounts.put(request.merchantOrderId(), request.amountPaise());
        String providerOrderId = "MOCKQR" + request.merchantOrderId();
        log.warn("Paytm MOCK mode: order {} will not be charged for real", request.merchantOrderId());
        return new InitiateResult(providerOrderId,
                "/mock-gateway/paytm/" + request.merchantOrderId(),
                GatewayState.PENDING,
                "{\"mock\":true}");
    }

    @Override
    public StatusResult checkStatus(String merchantOrderId) {
        GatewayState state = states.getOrDefault(merchantOrderId, GatewayState.PENDING);
        return new StatusResult("MOCKQR" + merchantOrderId, state, amounts.get(merchantOrderId),
                state == GatewayState.COMPLETED ? "MOCKTXN" + merchantOrderId : null,
                state == GatewayState.COMPLETED ? "UPI" : null,
                state == GatewayState.FAILED ? "MOCK_DECLINED" : null,
                state == GatewayState.FAILED ? "Payment declined in mock mode" : null,
                "{\"mock\":true,\"state\":\"" + state + "\"}");
    }

    /** Called by the mock checkout screen. */
    public void settle(String merchantOrderId, boolean success) {
        states.put(merchantOrderId, success ? GatewayState.COMPLETED : GatewayState.FAILED);
    }
}
