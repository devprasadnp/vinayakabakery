package com.harish.pickles.payment;

import com.harish.pickles.payment.GatewayModels.InitiateRequest;
import com.harish.pickles.payment.GatewayModels.InitiateResult;
import com.harish.pickles.payment.GatewayModels.StatusResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The gateway {@code PaymentService} talks to. It picks the real implementation on every call from the
 * current {@link PaymentProperties#getMode()} and the PhonePe mock switch, both of which an admin can change
 * at runtime from the admin panel - so switching between the manual QR flow, PhonePe and PhonePe mock needs
 * no restart. (Paytm stays chosen at startup by its own configuration.)
 */
@Primary
@Component
public class PaymentGatewayRouter implements PaymentGateway {

    private final PaymentProperties payments;
    private final PhonePeProperties phonePe;
    private final ManualQrGateway manualQr;
    private final MockGateway phonePeMock;
    private final PhonePeGateway phonePeLive;
    private final ObjectProvider<PaytmGateway> paytmLive;
    private final ObjectProvider<MockPaytmGateway> paytmMock;

    public PaymentGatewayRouter(PaymentProperties payments, PhonePeProperties phonePe, ManualQrGateway manualQr,
                                MockGateway phonePeMock, PhonePeGateway phonePeLive,
                                ObjectProvider<PaytmGateway> paytmLive, ObjectProvider<MockPaytmGateway> paytmMock) {
        this.payments = payments;
        this.phonePe = phonePe;
        this.manualQr = manualQr;
        this.phonePeMock = phonePeMock;
        this.phonePeLive = phonePeLive;
        this.paytmLive = paytmLive;
        this.paytmMock = paytmMock;
    }

    private PaymentGateway current() {
        return switch (payments.getMode()) {
            case QR -> manualQr;
            case PHONEPE -> phonePe.isMock() ? phonePeMock : phonePeLive;
            case PAYTM -> {
                PaymentGateway g = paytmLive.getIfAvailable();
                yield g != null ? g : paytmMock.getObject();
            }
        };
    }

    @Override
    public String name() {
        return current().name();
    }

    @Override
    public InitiateResult initiate(InitiateRequest request) {
        return current().initiate(request);
    }

    @Override
    public StatusResult checkStatus(String merchantOrderId) {
        return current().checkStatus(merchantOrderId);
    }
}
