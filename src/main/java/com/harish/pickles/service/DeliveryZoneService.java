package com.harish.pickles.service;

import com.harish.pickles.domain.AppSetting;
import com.harish.pickles.domain.DeliveryPincode;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.exception.NotFoundException;
import com.harish.pickles.repository.AppSettingRepository;
import com.harish.pickles.repository.DeliveryPincodeRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where the store will deliver. Admins keep a list of PIN codes and one on/off switch:
 *
 * <ul>
 *   <li><b>Switch off</b> (the default) - every address is accepted, exactly as before.</li>
 *   <li><b>Switch on</b> - an order is accepted only if its PIN code is on the list and active.
 *       Anything else is refused in {@code OrderService.placeOrder}, before stock is reserved
 *       and before the customer is ever shown a payment screen.</li>
 * </ul>
 *
 * The switch exists so that an empty list can never silently mean "deliver nowhere", and so an
 * existing shop is not locked out the moment this feature is deployed.
 */
@Service
public class DeliveryZoneService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryZoneService.class);

    static final String RESTRICT_KEY = "delivery.restrict-to-pincodes";
    /** Indian PIN codes are six digits and never start with 0. */
    private static final Pattern PINCODE = Pattern.compile("^[1-9][0-9]{5}$");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s,;]+");
    private static final int MAX_BATCH = 500;
    private static final int MAX_AREA_NAME = 120;

    /** What the checkout page and the API tell a customer about one PIN code. */
    public record Availability(String pincode, boolean deliverable, String message) {
    }

    /** Outcome of adding several PIN codes in one go. */
    public record BulkResult(List<String> added, List<String> alreadyListed, List<String> invalid) {
    }

    private final DeliveryPincodeRepository pincodeRepository;
    private final AppSettingRepository settingRepository;

    public DeliveryZoneService(DeliveryPincodeRepository pincodeRepository, AppSettingRepository settingRepository) {
        this.pincodeRepository = pincodeRepository;
        this.settingRepository = settingRepository;
    }

    // ------------------------------------------------------------------ rules

    @Transactional(readOnly = true)
    public boolean isRestricted() {
        return settingRepository.findById(RESTRICT_KEY)
                .map(s -> "true".equalsIgnoreCase(s.getValue()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Availability check(String rawPincode) {
        String pincode = normalise(rawPincode);
        if (!PINCODE.matcher(pincode).matches()) {
            return new Availability(pincode, false, "Enter a valid 6 digit PIN code");
        }
        if (!isRestricted()) {
            return new Availability(pincode, true, "We deliver to " + pincode);
        }
        return pincodeRepository.findByPincode(pincode)
                .filter(DeliveryPincode::isActive)
                .map(p -> new Availability(pincode, true, "Good news - we deliver to " + pincode
                        + (p.getAreaName() == null ? "" : " (" + p.getAreaName() + ")")))
                .orElseGet(() -> new Availability(pincode, false, notServiced(pincode)));
    }

    /** True while no restriction applies, or when the PIN code is on the list and active. */
    @Transactional(readOnly = true)
    public boolean isServiceable(String rawPincode) {
        return !isRestricted() || check(rawPincode).deliverable();
    }

    /**
     * The authoritative gate, called from {@code OrderService.placeOrder}. Does nothing while the
     * restriction is off, so orders behave exactly as they did before this feature existed.
     */
    @Transactional(readOnly = true)
    public void requireDeliverable(String rawPincode) {
        if (!isRestricted()) {
            return;
        }
        Availability result = check(rawPincode);
        if (!result.deliverable()) {
            throw new BusinessException(result.message());
        }
    }

    public static String notServiced(String pincode) {
        return "Sorry, we don't have a delivery partner for PIN code " + pincode
                + " yet. Please use a different delivery address.";
    }

    // ------------------------------------------------------------------ admin

    @Transactional
    public void setRestricted(boolean restricted) {
        if (restricted && pincodeRepository.countByActiveTrue() == 0) {
            throw new BusinessException(
                    "Add at least one active PIN code first, otherwise no customer could check out");
        }
        AppSetting setting = settingRepository.findById(RESTRICT_KEY)
                .orElseGet(() -> new AppSetting(RESTRICT_KEY, "false"));
        setting.setValue(Boolean.toString(restricted));
        settingRepository.save(setting);
        log.info("Delivery restriction switched {}", restricted ? "ON" : "OFF");
    }

    @Transactional(readOnly = true)
    public Page<DeliveryPincode> list(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return pincodeRepository.findAllByOrderByPincodeAsc(pageable);
        }
        return pincodeRepository.search(query.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public long activeCount() {
        return pincodeRepository.countByActiveTrue();
    }

    /**
     * Adds every PIN code found in {@code rawInput} (separated by commas, spaces or new lines).
     * Bad entries and ones already on the list are reported back rather than failing the batch.
     */
    @Transactional
    public BulkResult addAll(String rawInput, String areaName) {
        Set<String> valid = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String token : SEPARATORS.split(rawInput == null ? "" : rawInput.trim())) {
            if (token.isEmpty()) {
                continue;
            }
            if (PINCODE.matcher(token).matches()) {
                valid.add(token);
            } else if (!invalid.contains(token)) {
                invalid.add(token);
            }
        }
        if (valid.isEmpty() && invalid.isEmpty()) {
            throw new BusinessException("Enter at least one PIN code");
        }
        if (valid.size() > MAX_BATCH) {
            throw new BusinessException("Add at most " + MAX_BATCH + " PIN codes at a time");
        }

        String label = (areaName == null || areaName.isBlank()) ? null : areaName.trim();
        if (label != null && label.length() > MAX_AREA_NAME) {
            throw new BusinessException("Area name must be " + MAX_AREA_NAME + " characters or fewer");
        }

        Set<String> existing = new LinkedHashSet<>();
        pincodeRepository.findByPincodeIn(valid).forEach(p -> existing.add(p.getPincode()));

        List<String> added = new ArrayList<>();
        List<String> alreadyListed = new ArrayList<>();
        for (String pincode : valid) {
            if (existing.contains(pincode)) {
                alreadyListed.add(pincode);
            } else {
                pincodeRepository.save(new DeliveryPincode(pincode, label));
                added.add(pincode);
            }
        }
        log.info("Delivery PIN codes added: {} new, {} already listed, {} rejected",
                added.size(), alreadyListed.size(), invalid.size());
        return new BulkResult(added, alreadyListed, invalid);
    }

    @Transactional
    public DeliveryPincode setActive(Long id, boolean active) {
        DeliveryPincode pincode = getById(id);
        pincode.setActive(active);
        return pincodeRepository.save(pincode);
    }

    /** Orders keep their own copy of the address, so nothing points at this row and it can simply go. */
    @Transactional
    public String remove(Long id) {
        DeliveryPincode pincode = getById(id);
        String value = pincode.getPincode();
        pincodeRepository.delete(pincode);
        return value;
    }

    private DeliveryPincode getById(Long id) {
        return pincodeRepository.findById(id).orElseThrow(() -> NotFoundException.of("PIN code", id));
    }

    static String normalise(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
