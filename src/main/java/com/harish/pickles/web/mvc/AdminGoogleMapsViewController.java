package com.harish.pickles.web.mvc;

import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.DeliveryMapSettingsService;
import com.harish.pickles.service.DeliveryMapSettingsService.Rules;
import com.harish.pickles.service.GeocodingService;
import com.harish.pickles.service.GoogleMapsSettingsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin and super admin: the Google Maps keys behind the checkout pin map, distance pricing and address checks. */
@Controller
@RequestMapping("/admin/google-maps")
@PreAuthorize("hasRole('ADMIN')")
public class AdminGoogleMapsViewController {

    private final GoogleMapsSettingsService settingsService;
    private final GeocodingService geocodingService;

    private final DeliveryMapSettingsService mapSettings;

    public AdminGoogleMapsViewController(GoogleMapsSettingsService settingsService,
                                         GeocodingService geocodingService,
                                         DeliveryMapSettingsService mapSettings) {
        this.mapSettings = mapSettings;
        this.settingsService = settingsService;
        this.geocodingService = geocodingService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("maps", settingsService.view());
        model.addAttribute("rules", mapSettings.get());
        model.addAttribute("activePage", "google-maps");
        return "admin/google-maps";
    }

    @PostMapping
    public String save(@RequestParam(required = false) String browserKey,
                       @RequestParam(required = false) String serverKey,
                       @RequestParam(defaultValue = "false") boolean clearServerKey,
                       RedirectAttributes flash) {
        try {
            settingsService.save(browserKey, serverKey, clearServerKey);
            flash.addFlashAttribute("message", "Google Maps keys saved and applied");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/google-maps";
    }

    @PostMapping("/rules")
    public String saveRules(@RequestParam(defaultValue = "false") boolean showMaps,
                            @RequestParam(defaultValue = "false") boolean pinRequired,
                            @RequestParam(defaultValue = "false") boolean verifyPincode,
                            @RequestParam(defaultValue = "false") boolean verifyLandmark,
                            @RequestParam double pincodeToleranceKm,
                            @RequestParam double landmarkToleranceKm,
                            @RequestParam(defaultValue = "false") boolean drivingDistance,
                            @RequestParam double roadFactor,
                            RedirectAttributes flash) {
        try {
            mapSettings.save(new Rules(showMaps, pinRequired, verifyPincode, verifyLandmark, pincodeToleranceKm,
                    landmarkToleranceKm, drivingDistance, roadFactor));
            flash.addFlashAttribute("message", "Delivery map rules saved");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/google-maps";
    }

    /** Asks Google's Geocoding API (with the server key) whether the saved key works. */
    @PostMapping("/test")
    public String test(RedirectAttributes flash) {
        String problem = geocodingService.probe();
        if (problem == null) {
            flash.addFlashAttribute("message", "Google accepted the server key (Geocoding API works)");
        } else {
            flash.addFlashAttribute("error", problem);
        }
        return "redirect:/admin/google-maps";
    }
}
