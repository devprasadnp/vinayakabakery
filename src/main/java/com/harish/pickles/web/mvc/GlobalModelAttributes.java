package com.harish.pickles.web.mvc;

import com.harish.pickles.config.GoogleMapsProperties;
import com.harish.pickles.domain.User;
import com.harish.pickles.payment.PaymentProperties;
import com.harish.pickles.service.CartService;
import com.harish.pickles.service.DeliveryMapSettingsService;
import com.harish.pickles.service.InventoryCategoryService;
import com.harish.pickles.service.StoreLocationService;
import com.harish.pickles.web.CurrentUser;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Puts the signed-in user, the cart badge, the payment mode, the Google Maps key, the store's
 * own location and the storefront category list (for the footer and any other shared chrome)
 * on every page. */
@ControllerAdvice(basePackages = "com.harish.pickles.web.mvc")
public class GlobalModelAttributes {

    private final CartService cartService;
    private final PaymentProperties paymentProperties;
    private final InventoryCategoryService categoryService;
    private final GoogleMapsProperties mapsProperties;
    private final StoreLocationService storeLocationService;
    private final DeliveryMapSettingsService mapSettings;

    public GlobalModelAttributes(CartService cartService, PaymentProperties paymentProperties,
                                 InventoryCategoryService categoryService, GoogleMapsProperties mapsProperties,
                                 StoreLocationService storeLocationService,
                                 DeliveryMapSettingsService mapSettings) {
        this.cartService = cartService;
        this.paymentProperties = paymentProperties;
        this.categoryService = categoryService;
        this.mapsProperties = mapsProperties;
        this.storeLocationService = storeLocationService;
        this.mapSettings = mapSettings;
    }

    @ModelAttribute
    public void addCommonAttributes(Model model) {
        User user = CurrentUser.find().orElse(null);
        model.addAttribute("currentUser", user);
        model.addAttribute("cartCount", user == null ? 0 : cartService.summary(user).totalItems());
        model.addAttribute("qrMode", paymentProperties.isQrMode());
        model.addAttribute("footerCategories", categoryService.browsable());
        model.addAttribute("googleMapsApiKey", mapSettings.get().showMaps() ? mapsProperties.getApiKey() : "");
        model.addAttribute("storeLocation", storeLocationService.get());
    }
}
