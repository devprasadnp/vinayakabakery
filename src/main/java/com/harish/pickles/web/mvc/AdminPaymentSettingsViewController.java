package com.harish.pickles.web.mvc;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.payment.PaymentStatusRefresher;
import com.harish.pickles.payment.PhonePeGateway;
import com.harish.pickles.payment.PhonePeProperties.Environment;
import com.harish.pickles.payment.PhonePeSettingsService;
import com.harish.pickles.payment.PhonePeSettingsService.Update;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin and super admin: PhonePe credentials, mode and webhook settings, plus connection test and refresh. */
@Controller
@RequestMapping("/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentSettingsViewController {

    private final PhonePeSettingsService settingsService;
    private final PhonePeGateway phonePeGateway;
    private final PaymentStatusRefresher refresher;
    private final AppProperties appProperties;

    public AdminPaymentSettingsViewController(PhonePeSettingsService settingsService, PhonePeGateway phonePeGateway,
                                              PaymentStatusRefresher refresher, AppProperties appProperties) {
        this.settingsService = settingsService;
        this.phonePeGateway = phonePeGateway;
        this.refresher = refresher;
        this.appProperties = appProperties;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("phonepe", settingsService.view());
        model.addAttribute("environments", Environment.values());
        model.addAttribute("webhookUrl", appProperties.getBaseUrl().replaceAll("/+$", "") + "/api/payments/phonepe/webhook");
        model.addAttribute("activePage", "payments");
        return "admin/payment-settings";
    }

    @PostMapping
    public String save(@RequestParam(defaultValue = "false") boolean usePhonePe,
                       @RequestParam(defaultValue = "false") boolean mock,
                       @RequestParam Environment environment,
                       @RequestParam(required = false) String clientId,
                       @RequestParam(required = false) String clientSecret,
                       @RequestParam(required = false) String clientVersion,
                       @RequestParam(required = false) String redirectUrl,
                       @RequestParam(defaultValue = "1200") int expireAfterSeconds,
                       @RequestParam(required = false) String webhookUsername,
                       @RequestParam(required = false) String webhookPassword,
                       RedirectAttributes flash) {
        try {
            settingsService.save(new Update(usePhonePe, mock, environment, clientId, clientSecret, clientVersion,
                    redirectUrl, expireAfterSeconds, webhookUsername, webhookPassword));
            flash.addFlashAttribute("message", "PhonePe settings saved and applied");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/payments";
    }

    /** Asks PhonePe (through the SDK) whether the saved credentials are accepted. */
    @PostMapping("/test")
    public String test(RedirectAttributes flash) {
        String problem = phonePeGateway.testConnection();
        if (problem == null) {
            flash.addFlashAttribute("message", "Connected: PhonePe accepted the saved credentials");
        } else {
            flash.addFlashAttribute("error", problem);
        }
        return "redirect:/admin/payments";
    }

    /** Re-checks every pending PhonePe payment right now instead of waiting for the next minute. */
    @PostMapping("/refresh")
    public String refresh(RedirectAttributes flash) {
        int checked = refresher.refreshPending();
        flash.addFlashAttribute("message", checked == 0
                ? "No pending PhonePe payments to refresh (or PhonePe is not the live gateway)"
                : "Refreshed " + checked + " pending PhonePe payment(s)");
        return "redirect:/admin/payments";
    }
}
