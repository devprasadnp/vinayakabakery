package com.harish.pickles.web.mvc;

import com.harish.pickles.payment.MockPaytmGateway;
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
 * Stand-in for the real Paytm QR checkout while {@code payments.mode=PAYTM} and
 * {@code payments.paytm.mock=true}. Exists purely so the full order -> pay -> callback loop
 * can be walked through before a merchant account is live. Disappears in production.
 */
@Controller
@RequestMapping("/mock-gateway/paytm")
@ConditionalOnExpression("'${payments.mode:QR}' == 'PAYTM' and ${payments.paytm.mock:true} == true")
public class MockPaytmGatewayController {

    private final MockPaytmGateway mockGateway;
    private final PaymentService paymentService;

    public MockPaytmGatewayController(MockPaytmGateway mockGateway, PaymentService paymentService) {
        this.mockGateway = mockGateway;
        this.paymentService = paymentService;
    }

    @GetMapping("/{merchantOrderId}")
    public String checkout(@PathVariable String merchantOrderId, Model model) {
        model.addAttribute("merchantOrderId", merchantOrderId);
        model.addAttribute("payment", paymentService.findByMerchantOrderId(merchantOrderId).orElse(null));
        return "mock-gateway-paytm";
    }

    @PostMapping("/{merchantOrderId}")
    public String settle(@PathVariable String merchantOrderId, @RequestParam boolean success) {
        mockGateway.settle(merchantOrderId, success);
        return "redirect:/payment/callback?merchantOrderId=" + merchantOrderId;
    }
}
