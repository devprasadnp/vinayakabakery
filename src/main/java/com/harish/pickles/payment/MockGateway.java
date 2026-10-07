package com.harish.pickles.payment;

import com.harish.pickles.payment.GatewayModels.InitiateRequest;
import com.harish.pickles.payment.GatewayModels.InitiateResult;
import com.harish.pickles.payment.GatewayModels.StatusResult;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Stand-in for PhonePe so the whole checkout can be exercised before credentials arrive.
 * Active while {@code payments.phonepe.mock=true}. It redirects to an in-app page
 * ({@code /mock-gateway/{merchantOrderId}}) offering "pay" and "fail" buttons.
 */
@Component
public class MockGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockGateway.class);

    private final Map<String, GatewayState> states = new ConcurrentHashMap<>();
    private final Map<String, Long> amounts = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "PHONEPE_MOCK";
    }

    @Override
    public InitiateResult initiate(InitiateRequest request) {
        states.put(request.merchantOrderId(), GatewayState.PENDING);
        amounts.put(request.merchantOrderId(), request.amountPaise());
        String providerOrderId = "MOCK" + request.merchantOrderId();
        log.warn("PhonePe MOCK mode: order {} will not be charged for real", request.merchantOrderId());
        return new InitiateResult(providerOrderId,
                "/mock-gateway/" + request.merchantOrderId(),
                GatewayState.PENDING,
                "{\"mock\":true}");
    }

    @Override
    public StatusResult checkStatus(String merchantOrderId) {
        GatewayState state = states.getOrDefault(merchantOrderId, GatewayState.PENDING);
        return new StatusResult("MOCK" + merchantOrderId, state, amounts.get(merchantOrderId),
                state == GatewayState.COMPLETED ? "MOCKTXN" + merchantOrderId : null,
                state == GatewayState.COMPLETED ? "UPI_INTENT" : null,
                state == GatewayState.FAILED ? "MOCK_DECLINED" : null,
                state == GatewayState.FAILED ? "Payment declined in mock mode" : null,
                "{\"mock\":true,\"state\":\"" + state + "\"}");
    }

    /** Called by the mock checkout screen. */
    public void settle(String merchantOrderId, boolean success) {
        states.put(merchantOrderId, success ? GatewayState.COMPLETED : GatewayState.FAILED);
    }
}
