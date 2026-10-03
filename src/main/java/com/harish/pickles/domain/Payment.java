package com.harish.pickles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 32)
    private String provider = "PHONEPE";

    /** What we send to PhonePe as merchantOrderId (max 63 chars, [A-Za-z0-9_-]). */
    @Column(name = "merchant_order_id", nullable = false, length = 63, unique = true)
    private String merchantOrderId;

    /** PhonePe's own orderId, e.g. OMO2401… */
    @Column(name = "provider_order_id", length = 120)
    private String providerOrderId;

    @Column(name = "transaction_id", length = 120)
    private String transactionId;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentStatus status = PaymentStatus.INITIATED;

    @Column(name = "payment_mode", length = 32)
    private String paymentMode;

    @Column(name = "redirect_url", columnDefinition = "text")
    private String redirectUrl;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "raw_response", columnDefinition = "text")
    private String rawResponse;

    /** The UPI string behind a gateway-generated QR (Paytm's {@code qrData}); null for PhonePe. */
    @Column(name = "qr_payload", columnDefinition = "text")
    private String qrPayload;

    public boolean isSettled() {
        return status == PaymentStatus.COMPLETED || status == PaymentStatus.FAILED;
    }
}
