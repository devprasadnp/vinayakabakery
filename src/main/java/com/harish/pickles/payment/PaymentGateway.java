package com.harish.pickles.payment;

import com.harish.pickles.payment.GatewayModels.InitiateRequest;
import com.harish.pickles.payment.GatewayModels.InitiateResult;
import com.harish.pickles.payment.GatewayModels.StatusResult;

/**
 * Everything the store needs from a payment provider. Implemented by
 * {@link PhonePeGateway} and, for credential-free local development, {@link MockGateway}.
 * Adding Razorpay or Cashfree later means one more implementation, nothing else changes.
 */
public interface PaymentGateway {

    String name();

    InitiateResult initiate(InitiateRequest request);

    StatusResult checkStatus(String merchantOrderId);
}
