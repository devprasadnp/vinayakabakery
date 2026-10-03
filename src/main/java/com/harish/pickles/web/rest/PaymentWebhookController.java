package com.harish.pickles.web.rest;

import com.harish.pickles.payment.PaytmProperties;
import com.harish.pickles.payment.PhonePeWebhookVerifier;
import com.harish.pickles.payment.dto.PhonePeWebhookRequest;
import com.harish.pickles.service.PaymentService;
import com.paytm.pg.merchant.PaytmChecksum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-to-server callbacks from the payment gateways.
 *
 * <pre>
 * PhonePe: https://your-domain.in/api/payments/phonepe/webhook - authenticated by the
 *          Authorization: SHA256(username:password) header configured on its dashboard.
 * Paytm:   https://your-domain.in/api/payments/paytm/webhook   - authenticated by the
 *          CHECKSUMHASH field Paytm signs every posted form with, verified against the
 *          merchant key. Configure this URL with Paytm support (payments.paytm.callback-url).
 * </pre>
 */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Orders & payment")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentService paymentService;
    private final PhonePeWebhookVerifier verifier;
    private final PaytmProperties paytmProperties;

    public PaymentWebhookController(PaymentService paymentService, PhonePeWebhookVerifier verifier,
                                    PaytmProperties paytmProperties) {
        this.paymentService = paymentService;
        this.verifier = verifier;
        this.paytmProperties = paytmProperties;
    }

    @PostMapping("/phonepe/webhook")
    @Operation(summary = "PhonePe payment callback (authenticated by SHA256(username:password))")
    public ResponseEntity<Map<String, String>> receivePhonePe(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody PhonePeWebhookRequest request) {

        if (!verifier.isValid(authorization)) {
            log.warn("Rejected PhonePe webhook with an invalid Authorization header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "unauthorized"));
        }

        log.info("PhonePe webhook received: event={}", request.event());
        paymentService.handleWebhook(request);
        // Always 200 once authenticated, otherwise PhonePe keeps retrying.
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping(value = "/paytm/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "Paytm payment callback (authenticated by CHECKSUMHASH)")
    public ResponseEntity<Map<String, String>> receivePaytm(@RequestParam Map<String, String> params) {
        String checksum = params.get("CHECKSUMHASH");
        Map<String, String> toVerify = new HashMap<>(params);
        toVerify.remove("CHECKSUMHASH");

        boolean valid = false;
        try {
            valid = checksum != null && !checksum.isBlank()
                    && PaytmChecksum.verifySignature(new TreeMap<>(toVerify), paytmProperties.getMerchantKey(), checksum);
        } catch (Exception ex) {
            log.warn("Could not verify Paytm webhook checksum: {}", ex.getMessage());
        }
        if (!valid) {
            log.warn("Rejected Paytm webhook with an invalid CHECKSUMHASH");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "unauthorized"));
        }

        log.info("Paytm webhook received: orderId={}, status={}", params.get("ORDERID"), params.get("STATUS"));
        paymentService.handlePaytmWebhook(params);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
