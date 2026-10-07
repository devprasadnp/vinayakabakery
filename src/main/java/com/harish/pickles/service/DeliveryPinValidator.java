package com.harish.pickles.service;

import com.harish.pickles.dto.OrderDtos.AddressRequest;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.GeocodingService.Place;
import com.harish.pickles.service.GeocodingService.Point;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Checks the delivery address against the pin the customer dropped, so the delivery partner is not sent
 * to a pin that contradicts the written address:
 * <ul>
 *   <li>a pin is required whenever Google Maps is configured;</li>
 *   <li>the PIN code must be one Google lists for the pin's location, or - at a boundary - the pin must
 *       lie within the admin-set tolerance of that PIN code's centre;</li>
 *   <li>the nearest landmark, if given, must be within the admin-set tolerance of the pin.</li>
 * </ul>
 * Every check and tolerance is admin-configurable ({@link DeliveryMapSettingsService}).
 * With no Maps key, or when Google cannot answer, the checks are skipped rather than blocking an order.
 */
@Service
public class DeliveryPinValidator {

    private final GeocodingService geocoding;
    private final DeliveryMapSettingsService mapSettings;

    public DeliveryPinValidator(GeocodingService geocoding, DeliveryMapSettingsService mapSettings) {
        this.geocoding = geocoding;
        this.mapSettings = mapSettings;
    }

    /** @return the pin's readable address (for the delivery partner), or null when it could not be looked up */
    public String validate(AddressRequest a) {
        var rules = mapSettings.get();
        if (!rules.showMaps() || !geocoding.isConfigured()) {
            return null;
        }
        if (a.latitude() == null || a.longitude() == null) {
            if (!rules.pinRequired()) {
                return null;
            }
            throw new BusinessException("Please drop a pin on the map at your delivery location, "
                    + "so our delivery partner can find you");
        }
        double lat = a.latitude();
        double lng = a.longitude();
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BusinessException("The delivery pin is not a valid location");
        }

        Optional<Place> place = geocoding.reverse(lat, lng);
        String pincode = a.pincode() == null ? "" : a.pincode().trim();
        if (rules.verifyPincode() && place.isPresent() && !place.get().postalCodes().isEmpty()
                && !place.get().postalCodes().contains(pincode)) {
            Optional<Point> centre = geocoding.locate(pincode + ", India");
            boolean nearby = centre.isPresent()
                    && GeocodingService.crowKm(lat, lng, centre.get().lat(), centre.get().lng()) <= rules.pincodeToleranceKm();
            if (centre.isPresent() && !nearby) {
                throw new BusinessException("Your pin is in PIN code " + String.join("/", place.get().postalCodes())
                        + " but you entered " + pincode + ". Please move the pin or correct the PIN code.");
            }
        }

        String landmark = a.addressLine2() == null ? "" : a.addressLine2().trim();
        if (rules.verifyLandmark() && !landmark.isEmpty()) {
            String query = landmark + ", " + nullToEmpty(a.city()) + " " + pincode;
            Optional<Point> found = geocoding.locate(query);
            if (found.isPresent()
                    && GeocodingService.crowKm(lat, lng, found.get().lat(), found.get().lng()) > rules.landmarkToleranceKm()) {
                throw new BusinessException("The nearest location \"" + landmark + "\" is far from your pin. "
                        + "Please move the pin or correct the landmark.");
            }
        }
        return place.map(Place::formattedAddress).orElse(null);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
