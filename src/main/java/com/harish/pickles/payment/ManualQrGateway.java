package com.harish.pickles.payment;

import com.harish.pickles.payment.GatewayModels.InitiateRequest;
import com.harish.pickles.payment.GatewayModels.InitiateResult;
import com.harish.pickles.payment.GatewayModels.StatusResult;
import org.springframework.stereotype.Component;

/**
 * Active while {@code payments.mode=QR} (the default) - the manual "scan the store's UPI QR,
 * a human confirms it afterwards" flow. {@code PaymentService.initiate/checkStatus} are never
 * called in this mode ({@code StorefrontController} branches on {@code PaymentProperties.isQrMode()}
 * before reaching them); this class exists only so a {@link PaymentGateway} bean is always present
 * to satisfy {@code PaymentService}'s constructor, whichever mode is configured.
 */
@Component
public class ManualQrGateway implements PaymentGateway {

    @Override
    public String name() {
        return "MANUAL_QR";
    }

    @Override
    public InitiateResult initiate(InitiateRequest request) {
        throw new UnsupportedOperationException(
                "payments.mode=QR is the manual flow - PaymentService.initiate() should never be called in this mode");
    }

    @Override
    public StatusResult checkStatus(String merchantOrderId) {
        throw new UnsupportedOperationException(
                "payments.mode=QR is the manual flow - PaymentService.checkStatus() should never be called in this mode");
    }
}
