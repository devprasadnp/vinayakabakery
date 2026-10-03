package com.harish.pickles.web.mvc;

import com.harish.pickles.config.GoogleMapsProperties;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.StoreLocationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Admin and super admin: set the bakery's own location, shown on the storefront footer and
 * used to centre the checkout delivery-pin map. */
@Controller
@RequestMapping("/admin/store-location")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStoreLocationViewController {

    private final StoreLocationService storeLocationService;
    private final GoogleMapsProperties mapsProperties;

    public AdminStoreLocationViewController(StoreLocationService storeLocationService,
                                             GoogleMapsProperties mapsProperties) {
        this.storeLocationService = storeLocationService;
        this.mapsProperties = mapsProperties;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("location", storeLocationService.get());
        model.addAttribute("mapsConfigured", mapsProperties.isConfigured());
        model.addAttribute("activePage", "store-location");
        return "admin/store-location";
    }

    @PostMapping
    public String save(@RequestParam(required = false) Double latitude,
                       @RequestParam(required = false) Double longitude,
                       @RequestParam(required = false) String address,
                       @RequestParam(required = false) String label,
                       RedirectAttributes flash) {
        try {
            storeLocationService.set(latitude, longitude, address, label);
            flash.addFlashAttribute("message", "Store location saved");
        } catch (BusinessException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/store-location";
    }
}
