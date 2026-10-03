package com.harish.pickles.web.mvc;

import com.harish.pickles.payment.MockGateway;
import com.harish.pickles.service.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Stand-in for the PhonePe checkout page while {@code payments.phonepe.mock=true}.
 * It exists purely so the full order -> pay -> callback loop can be walked through
 * before live credentials are issued. It disappears in production.
 */
@Controller
@RequestMapping("/mock-gateway")
@ConditionalOnExpression("'${payments.mode:QR}' == 'PHONEPE' and ${payments.phonepe.mock:true} == true")
public class MockGatewayController {

    private final MockGateway mockGateway;
    private final PaymentService paymentService;

    public MockGatewayController(MockGateway mockGateway, PaymentService paymentService) {
        this.mockGateway = mockGateway;
        this.paymentService = paymentService;
    }

    @GetMapping("/{merchantOrderId}")
    public String checkout(@PathVariable String merchantOrderId, Model model) {
        model.addAttribute("merchantOrderId", merchantOrderId);
        model.addAttribute("payment", paymentService.findByMerchantOrderId(merchantOrderId).orElse(null));
        return "mock-gateway";
    }

    @PostMapping("/{merchantOrderId}")
    public String settle(@PathVariable String merchantOrderId, @RequestParam boolean success) {
        mockGateway.settle(merchantOrderId, success);
        return "redirect:/payment/callback?merchantOrderId=" + merchantOrderId;
    }
}
