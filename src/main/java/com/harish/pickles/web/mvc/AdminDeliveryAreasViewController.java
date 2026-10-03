package com.harish.pickles.web.mvc;

import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.DeliveryZoneService;
import com.harish.pickles.service.DeliveryZoneService.BulkResult;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin and super admin: choose which PIN codes the store delivers to. */
@Controller
@RequestMapping("/admin/delivery-areas")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryAreasViewController {

    private final DeliveryZoneService deliveryZoneService;

    public AdminDeliveryAreasViewController(DeliveryZoneService deliveryZoneService) {
        this.deliveryZoneService = deliveryZoneService;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String q,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pincodes", deliveryZoneService.list(q, PageRequest.of(page, 50)));
        model.addAttribute("q", q);
        model.addAttribute("restricted", deliveryZoneService.isRestricted());
        model.addAttribute("activeCount", deliveryZoneService.activeCount());
        model.addAttribute("activePage", "areas");
        return "admin/delivery-areas";
    }

    @PostMapping
    public String add(@RequestParam String pincodes, @RequestParam(required = false) String areaName,
                      RedirectAttributes flash) {
        try {
            BulkResult result = deliveryZoneService.addAll(pincodes, areaName);
            StringBuilder note = new StringBuilder();
            if (!result.added().isEmpty()) {
                note.append(result.added().size()).append(" PIN code(s) added");
            }
            if (!result.alreadyListed().isEmpty()) {
                append(note, "already listed: " + String.join(", ", result.alreadyListed()));
            }
            flash.addFlashAttribute("message", note.length() == 0 ? "Nothing new to add" : note.toString());
            if (!result.invalid().isEmpty()) {
                flash.addFlashAttribute("error", "Not a valid 6 digit PIN code, so skipped: "
                        + String.join(", ", limit(result.invalid())));
            }
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/delivery-areas";
    }

    /** The on/off switch. While on, only the active PIN codes below can place an order. */
    @PostMapping("/restriction")
    public String setRestriction(@RequestParam boolean enabled, RedirectAttributes flash) {
        try {
            deliveryZoneService.setRestricted(enabled);
            flash.addFlashAttribute("message", enabled
                    ? "Delivery is now limited to the PIN codes below"
                    : "Delivery restriction is off - orders are accepted from any PIN code");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/delivery-areas";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes flash) {
        var saved = deliveryZoneService.setActive(id, active);
        flash.addFlashAttribute("message", saved.getPincode() + (active ? " is active again" : " is paused"));
        return "redirect:/admin/delivery-areas";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes flash) {
        flash.addFlashAttribute("message", deliveryZoneService.remove(id) + " was removed");
        return "redirect:/admin/delivery-areas";
    }

    private static void append(StringBuilder note, String part) {
        note.append(note.length() == 0 ? "" : " · ").append(part);
    }

    /** Keeps the flash message readable if someone pastes a very long bad list. */
    private static List<String> limit(List<String> values) {
        return values.size() <= 10 ? values : values.subList(0, 10);
    }
}
