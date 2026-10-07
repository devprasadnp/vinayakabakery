package com.harish.pickles.service;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.domain.AppSetting;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.repository.AppSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivery price. Every order pays the <b>default (base) fee</b> for deliveries within the base
 * radius; with distance pricing switched on, each extra kilometre beyond that radius adds the
 * admin-configured per-km rate. Switched off (the default) the fee is the old flat fee, so an
 * existing shop sees no change until an admin opts in. The free-shipping threshold still applies.
 */
@Service
public class DeliveryPricingService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryPricingService.class);

    static final String ENABLED_KEY = "delivery.pricing.enabled";
    static final String BASE_FEE_KEY = "delivery.pricing.base-fee-paise";
    static final String BASE_KM_KEY = "delivery.pricing.base-km";
    static final String PER_KM_KEY = "delivery.pricing.per-km-paise";
    static final String MAX_KM_KEY = "delivery.pricing.max-km";

    private static final double DEFAULT_BASE_KM = 5.0;

    public record Pricing(boolean enabled, long baseFeePaise, double baseKm, long perKmPaise, Double maxKm) {
    }

    private final AppSettingRepository settingRepository;
    private final AppProperties appProperties;

    public DeliveryPricingService(AppSettingRepository settingRepository, AppProperties appProperties) {
        this.settingRepository = settingRepository;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public Pricing get() {
        return new Pricing(
                "true".equalsIgnoreCase(read(ENABLED_KEY)),
                readLong(BASE_FEE_KEY, appProperties.getShipping().getFlatFeePaise()),
                readDouble(BASE_KM_KEY, DEFAULT_BASE_KM),
                readLong(PER_KM_KEY, 0),
                read(MAX_KM_KEY) == null ? null : readDouble(MAX_KM_KEY, 0));
    }

    @Transactional
    public Pricing set(boolean enabled, long baseFeePaise, double baseKm, long perKmPaise, Double maxKm) {
        if (baseFeePaise < 0 || perKmPaise < 0) {
            throw new BusinessException("Prices cannot be negative");
        }
        if (baseKm < 0) {
            throw new BusinessException("The default delivery radius cannot be negative");
        }
        if (maxKm != null && maxKm <= 0) {
            throw new BusinessException("Maximum delivery distance must be above 0, or left blank for no limit");
        }
        write(ENABLED_KEY, Boolean.toString(enabled));
        write(BASE_FEE_KEY, Long.toString(baseFeePaise));
        write(BASE_KM_KEY, Double.toString(baseKm));
        write(PER_KM_KEY, Long.toString(perKmPaise));
        write(MAX_KM_KEY, maxKm == null ? null : Double.toString(maxKm));
        log.info("Delivery pricing saved: enabled={} base={}p within {} km, then {}p/km", enabled, baseFeePaise,
                baseKm, perKmPaise);
        return get();
    }

    /** Courier fee for a basket. {@code distanceKm} may be null (no pin dropped): base fee applies. */
    @Transactional(readOnly = true)
    public long shippingFor(long subtotalPaise, Double distanceKm) {
        if (subtotalPaise <= 0 || subtotalPaise >= appProperties.getShipping().getFreeAbovePaise()) {
            return 0;
        }
        Pricing p = get();
        if (!p.enabled() || distanceKm == null || distanceKm <= p.baseKm()) {
            return p.baseFeePaise();
        }
        return p.baseFeePaise() + Math.round(Math.ceil(distanceKm - p.baseKm()) * p.perKmPaise());
    }

    /** Refuses a delivery beyond the admin's maximum distance (only while distance pricing is on). */
    @Transactional(readOnly = true)
    public void requireWithinRange(Double distanceKm) {
        Pricing p = get();
        if (p.enabled() && p.maxKm() != null && distanceKm != null && distanceKm > p.maxKm()) {
            throw new BusinessException("Sorry, your pin is " + String.format("%.1f", distanceKm)
                    + " km from our store - we deliver up to " + p.maxKm() + " km.");
        }
    }

    private String read(String key) {
        return settingRepository.findById(key).map(AppSetting::getValue).filter(v -> !v.isBlank()).orElse(null);
    }

    private long readLong(String key, long fallback) {
        String v = read(key);
        return v == null ? fallback : Long.parseLong(v);
    }

    private double readDouble(String key, double fallback) {
        String v = read(key);
        return v == null ? fallback : Double.parseDouble(v);
    }

    private void write(String key, String value) {
        if (value == null) {
            settingRepository.findById(key).ifPresent(settingRepository::delete);
            return;
        }
        AppSetting s = settingRepository.findById(key).orElseGet(() -> new AppSetting(key, value));
        s.setValue(value);
        settingRepository.save(s);
    }
}
