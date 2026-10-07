package com.harish.pickles.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How the shop collects money.
 *
 * <p>{@code QR} is the manual flow: the customer scans the store's UPI QR, types the
 * order number into the payment note, and a human confirms the payment afterwards.
 * {@code PHONEPE} hands off to the PhonePe gateway and confirms itself via redirect and webhook.
 * {@code PAYTM} generates a Paytm Dynamic QR for the exact order amount and confirms itself via
 * the order-status API and webhook - no human confirmation needed, unlike {@code QR}.</p>
 *
 * <p>Everything under {@code payments.phonepe.*} stays bound to {@link PhonePeProperties} and
 * {@code payments.paytm.*} to {@link PaytmProperties}; unknown fields are ignored here, so all
 * three classes can share the {@code payments} prefix.</p>
 */
@ConfigurationProperties(prefix = "payments")
public class PaymentProperties {

    public enum Mode {
        /** Scan-and-pay with the store's UPI QR, confirmed by hand. */
        QR,
        /** PhonePe Payment Gateway, confirmed automatically by redirect and webhook. */
        PHONEPE,
        /** Paytm Dynamic QR, confirmed automatically by the order-status API and webhook. */
        PAYTM
    }

    private Mode mode = Mode.QR;

    /** The UPI ID printed under the QR so customers can pay without scanning. */
    private String upiId = "";

    /** Name shown as the payee, so customers can check it matches before paying. */
    private String payeeName = "";

    /** Path under /static to the QR image. */
    private String qrImage = "/images/payment-qr.png";

    /** What customers are told about how long manual confirmation takes. */
    private int confirmationHours = 24;

    /** Customer care mobile number shown for post-payment issues. */
    private String customerCareMobile = "";

    /** Customer care email shown for post-payment issues. */
    private String customerCareEmail = "";

    public boolean isQrMode() {
        return mode == Mode.QR;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public String getUpiId() {
        return upiId;
    }

    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    public String getQrImage() {
        return qrImage;
    }

    public void setQrImage(String qrImage) {
        this.qrImage = qrImage;
    }

    public int getConfirmationHours() {
        return confirmationHours;
    }

    public void setConfirmationHours(int confirmationHours) {
        this.confirmationHours = confirmationHours;
    }

    public String getCustomerCareMobile() {
        return customerCareMobile;
    }

    public void setCustomerCareMobile(String customerCareMobile) {
        this.customerCareMobile = customerCareMobile;
    }

    public String getCustomerCareEmail() {
        return customerCareEmail;
    }

    public void setCustomerCareEmail(String customerCareEmail) {
        this.customerCareEmail = customerCareEmail;
    }
}
