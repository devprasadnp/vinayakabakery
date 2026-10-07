package com.harish.pickles.web.mvc;

import com.harish.pickles.payment.MockGateway;
import com.harish.pickles.payment.PaymentProperties;
import com.harish.pickles.payment.PhonePeProperties;
import com.harish.pickles.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stand-in for the PhonePe checkout page while PhonePe mock mode is on.
 * It exists purely so the full order -> pay -> callback loop can be walked through
 * before live credentials are issued. It answers 404 as soon as mock mode is off.
 */
@Controller
@RequestMapping("/mock-gateway")
public class MockGatewayController {

    private final MockGateway mockGateway;
    private final PaymentService paymentService;
    private final PaymentProperties paymentProperties;
    private final PhonePeProperties phonePeProperties;

    public MockGatewayController(MockGateway mockGateway, PaymentService paymentService,
                                 PaymentProperties paymentProperties, PhonePeProperties phonePeProperties) {
        this.mockGateway = mockGateway;
        this.paymentService = paymentService;
        this.paymentProperties = paymentProperties;
        this.phonePeProperties = phonePeProperties;
    }

    /** The mock page can settle any order, so it must be unreachable the moment live PhonePe is on. */
    private void requireMockMode() {
        if (paymentProperties.getMode() != PaymentProperties.Mode.PHONEPE || !phonePeProperties.isMock()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/{merchantOrderId}")
    public String checkout(@PathVariable String merchantOrderId, Model model) {
        requireMockMode();
        model.addAttribute("merchantOrderId", merchantOrderId);
        model.addAttribute("payment", paymentService.findByMerchantOrderId(merchantOrderId).orElse(null));
        return "mock-gateway";
    }

    @PostMapping("/{merchantOrderId}")
    public String settle(@PathVariable String merchantOrderId, @RequestParam boolean success) {
        requireMockMode();
        mockGateway.settle(merchantOrderId, success);
        return "redirect:/payment/callback?merchantOrderId=" + merchantOrderId;
    }
}
