package com.harish.pickles.service;

import com.harish.pickles.domain.AppSetting;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bakery's own location, kept as admin-editable {@link AppSetting} rows rather than a
 * dedicated table - it is a handful of values, edited rarely, exactly what that key/value store
 * exists for. Shown on the storefront footer and used to centre the checkout delivery-pin map.
 */
@Service
public class StoreLocationService {

    static final String LAT_KEY = "store.location.lat";
    static final String LNG_KEY = "store.location.lng";
    static final String ADDRESS_KEY = "store.location.address";
    static final String LABEL_KEY = "store.location.label";

    public record StoreLocation(Double latitude, Double longitude, String address, String label) {
        public boolean hasPin() {
            return latitude != null && longitude != null;
        }
    }

    private final AppSettingRepository settingRepository;

    public StoreLocationService(AppSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Transactional(readOnly = true)
    public StoreLocation get() {
        return new StoreLocation(readDouble(LAT_KEY), readDouble(LNG_KEY), readString(ADDRESS_KEY),
                readString(LABEL_KEY));
    }

    @Transactional
    public StoreLocation set(Double latitude, Double longitude, String address, String label) {
        if ((latitude == null) != (longitude == null)) {
            throw new BusinessException("Enter both latitude and longitude, or leave both blank");
        }
        if (latitude != null && (latitude < -90 || latitude > 90)) {
            throw new BusinessException("Latitude must be between -90 and 90");
        }
        if (longitude != null && (longitude < -180 || longitude > 180)) {
            throw new BusinessException("Longitude must be between -180 and 180");
        }
        write(LAT_KEY, latitude == null ? null : String.valueOf(latitude));
        write(LNG_KEY, longitude == null ? null : String.valueOf(longitude));
        write(ADDRESS_KEY, address == null ? null : address.trim());
        write(LABEL_KEY, label == null ? null : label.trim());
        return get();
    }

    private Double readDouble(String key) {
        String value = readString(key);
        return value == null ? null : Double.valueOf(value);
    }

    private String readString(String key) {
        return settingRepository.findById(key).map(AppSetting::getValue)
                .filter(v -> !v.isBlank()).orElse(null);
    }

    private void write(String key, String value) {
        if (value == null || value.isBlank()) {
            settingRepository.findById(key).ifPresent(settingRepository::delete);
            return;
        }
        AppSetting setting = settingRepository.findById(key).orElseGet(() -> new AppSetting(key, value));
        setting.setValue(value);
        settingRepository.save(setting);
    }
}
