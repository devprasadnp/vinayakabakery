package com.harish.pickles.payment;

import com.harish.pickles.domain.Payment;
import com.harish.pickles.domain.PaymentStatus;
import com.harish.pickles.repository.PaymentRepository;
import com.harish.pickles.service.PaymentService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Live status refresh. While PhonePe is the live gateway this asks it (through the SDK) about every still-
 * pending PhonePe payment once a minute, so an order flips to PAID/FAILED within a minute even when the
 * customer closed the tab and the webhook never arrived. It is only a safety net: the redirect return, the
 * customer's status polling and the webhook all reach the same idempotent {@code PaymentService.reconcile}.
 */
@Component
public class PaymentStatusRefresher {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusRefresher.class);
    /** Orders older than this are expired on PhonePe's side too; not worth asking forever. */
    private static final long LOOKBACK_HOURS = 6;

    private final PaymentProperties payments;
    private final PhonePeProperties phonePe;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    public PaymentStatusRefresher(PaymentProperties payments, PhonePeProperties phonePe,
                                  PaymentRepository paymentRepository, PaymentService paymentService) {
        this.payments = payments;
        this.phonePe = phonePe;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void scheduledRefresh() {
        refreshPending();
    }

    /** @return how many pending payments were checked (0 when PhonePe is not the live gateway) */
    public int refreshPending() {
        if (payments.getMode() != PaymentProperties.Mode.PHONEPE || phonePe.isMock() || !phonePe.hasCredentials()) {
            return 0;
        }
        List<Payment> pending = paymentRepository.findByProviderAndStatusAndCreatedAtAfter(
                "PHONEPE", PaymentStatus.PENDING, LocalDateTime.now().minusHours(LOOKBACK_HOURS));
        int checked = 0;
        for (Payment p : pending) {
            try {
                paymentService.reconcile(p.getMerchantOrderId());
                checked++;
            } catch (RuntimeException ex) {
                log.warn("Could not refresh PhonePe payment {}: {}", p.getMerchantOrderId(), ex.getMessage());
            }
        }
        if (checked > 0) {
            log.info("Refreshed {} pending PhonePe payment(s)", checked);
        }
        return checked;
    }
}
