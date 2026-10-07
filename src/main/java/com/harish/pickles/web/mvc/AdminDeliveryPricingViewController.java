package com.harish.pickles.web.mvc;

import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.DeliveryPricingService;
import com.harish.pickles.service.StoreLocationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin and super admin: the default delivery price and the per-kilometre rate beyond it. */
@Controller
@RequestMapping("/admin/delivery-pricing")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryPricingViewController {

    private final DeliveryPricingService pricingService;
    private final StoreLocationService storeLocationService;

    public AdminDeliveryPricingViewController(DeliveryPricingService pricingService,
                                              StoreLocationService storeLocationService) {
        this.pricingService = pricingService;
        this.storeLocationService = storeLocationService;
    }

    @GetMapping
    public String index(Model model) {
        var p = pricingService.get();
        model.addAttribute("pricing", p);
        model.addAttribute("baseFee", rupees(p.baseFeePaise()));
        model.addAttribute("perKm", rupees(p.perKmPaise()));
        model.addAttribute("storeHasPin", storeLocationService.get().hasPin());
        model.addAttribute("activePage", "pricing");
        return "admin/delivery-pricing";
    }

    @PostMapping
    public String save(@RequestParam(defaultValue = "false") boolean enabled,
                       @RequestParam BigDecimal baseFee, @RequestParam BigDecimal baseKm,
                       @RequestParam BigDecimal perKm, @RequestParam(required = false) BigDecimal maxKm,
                       RedirectAttributes flash) {
        try {
            pricingService.set(enabled, paise(baseFee), baseKm.doubleValue(), paise(perKm),
                    maxKm == null ? null : maxKm.doubleValue());
            flash.addFlashAttribute("message", "Delivery pricing saved");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/delivery-pricing";
    }

    private static long paise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal rupees(long paise) {
        return BigDecimal.valueOf(paise, 2);
    }
}
