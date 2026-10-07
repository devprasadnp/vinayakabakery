package com.harish.pickles.service;

import com.harish.pickles.domain.AppSetting;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How strictly checkout treats the delivery pin, all editable by an admin (Admin &gt; Google Maps) instead of
 * being fixed in code. Stored as {@link AppSetting} rows; every value has a safe default.
 */
@Service
public class DeliveryMapSettingsService {

    static final String SHOW_MAPS = "delivery.map.show-maps";
    static final String PIN_REQUIRED = "delivery.map.pin-required";
    static final String VERIFY_PINCODE = "delivery.map.verify-pincode";
    static final String VERIFY_LANDMARK = "delivery.map.verify-landmark";
    static final String PINCODE_KM = "delivery.map.pincode-tolerance-km";
    static final String LANDMARK_KM = "delivery.map.landmark-tolerance-km";
    static final String DRIVING = "delivery.map.driving-distance";
    static final String ROAD_FACTOR = "delivery.map.road-factor";

    /**
     * @param showMaps master switch: false hides every Google map on the site and skips the pin checks
     * @param pinRequired customer must drop a pin to order (when maps are on)
     * @param verifyPincode reject a PIN code that disagrees with the pin
     * @param verifyLandmark reject a landmark far from the pin
     * @param drivingDistance true = Google driving distance; false = straight line x road factor
     */
    public record Rules(boolean showMaps, boolean pinRequired, boolean verifyPincode, boolean verifyLandmark,
                        double pincodeToleranceKm, double landmarkToleranceKm,
                        boolean drivingDistance, double roadFactor) {
        public static Rules defaults() {
            return new Rules(true, true, true, true, 12, 10, true, 1.3);
        }
    }

    private final AppSettingRepository settings;

    public DeliveryMapSettingsService(AppSettingRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Rules get() {
        Rules d = Rules.defaults();
        return new Rules(bool(SHOW_MAPS, d.showMaps()), bool(PIN_REQUIRED, d.pinRequired()), bool(VERIFY_PINCODE, d.verifyPincode()),
                bool(VERIFY_LANDMARK, d.verifyLandmark()), num(PINCODE_KM, d.pincodeToleranceKm()),
                num(LANDMARK_KM, d.landmarkToleranceKm()), bool(DRIVING, d.drivingDistance()),
                num(ROAD_FACTOR, d.roadFactor()));
    }

    @Transactional
    public Rules save(Rules r) {
        if (r.pincodeToleranceKm() < 0 || r.landmarkToleranceKm() < 0) {
            throw new BusinessException("Tolerances cannot be negative");
        }
        if (r.roadFactor() < 1 || r.roadFactor() > 3) {
            throw new BusinessException("Road factor must be between 1.0 and 3.0");
        }
        put(SHOW_MAPS, r.showMaps());
        put(PIN_REQUIRED, r.pinRequired());
        put(VERIFY_PINCODE, r.verifyPincode());
        put(VERIFY_LANDMARK, r.verifyLandmark());
        put(PINCODE_KM, r.pincodeToleranceKm());
        put(LANDMARK_KM, r.landmarkToleranceKm());
        put(DRIVING, r.drivingDistance());
        put(ROAD_FACTOR, r.roadFactor());
        return get();
    }

    private boolean bool(String key, boolean fallback) {
        return settings.findById(key).map(AppSetting::getValue).map(Boolean::parseBoolean).orElse(fallback);
    }

    private double num(String key, double fallback) {
        try {
            return settings.findById(key).map(AppSetting::getValue).map(Double::parseDouble).orElse(fallback);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void put(String key, Object value) {
        String text = String.valueOf(value);
        AppSetting s = settings.findById(key).orElseGet(() -> new AppSetting(key, text));
        s.setValue(text);
        settings.save(s);
    }
}
