package com.harish.pickles.service;

import com.harish.pickles.domain.Coupon;
import com.harish.pickles.domain.CouponRedemption;
import com.harish.pickles.domain.DiscountType;
import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.User;
import com.harish.pickles.dto.CouponDtos.CouponRequest;
import com.harish.pickles.dto.Money;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.exception.NotFoundException;
import com.harish.pickles.repository.CouponRedemptionRepository;
import com.harish.pickles.repository.CouponRepository;
import com.harish.pickles.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coupon lifecycle for all three journeys: customers preview and redeem a code, admins create
 * and manage coupons, and super admins archive/delete them.
 *
 * <p>Every usage limit is checked against {@link CouponRedemption} rows, not a customer's
 * session or the coupon's own counter, so limits hold even if two checkouts for the same
 * customer land at almost the same time.</p>
 */
@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final OrderRepository orderRepository;

    public CouponService(CouponRepository couponRepository, CouponRedemptionRepository redemptionRepository,
                         OrderRepository orderRepository) {
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
        this.orderRepository = orderRepository;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Page<Coupon> catalogue(String q, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return couponRepository.findByArchivedFalseOrderByIdDesc(pageable);
        }
        return couponRepository.findByArchivedFalseAndCodeContainingIgnoreCaseOrderByIdDesc(q.trim(), pageable);
    }

    @Transactional(readOnly = true)
    public Coupon getById(Long id) {
        return couponRepository.findById(id).orElseThrow(() -> NotFoundException.of("Coupon", id));
    }

    @Transactional(readOnly = true)
    public List<Coupon> archived() {
        return couponRepository.findByArchivedTrueOrderByArchivedAtDesc();
    }

    /**
     * Checks a code against the cart total without redeeming it, so the cart and checkout
     * pages can show the discount before the order exists. Throws {@link BusinessException}
     * with a message safe to show the customer when the code cannot be used right now.
     */
    @Transactional(readOnly = true)
    public CouponPreview preview(String code, long subtotalPaise, User user) {
        Coupon coupon = find(code);
        validate(coupon, subtotalPaise, user);
        long discount = computeDiscount(coupon, subtotalPaise);
        return new CouponPreview(coupon.getCode(), discount);
    }

    // ----------------------------------------------------------------- write

    @Transactional
    public Coupon create(CouponRequest request) {
        String code = normaliseCode(request.code());
        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("A coupon with code " + code + " already exists");
        }
        Coupon coupon = new Coupon();
        apply(coupon, request, code);
        Coupon saved = couponRepository.save(coupon);
        log.info("Coupon {} created", saved.getCode());
        return saved;
    }

    @Transactional
    public Coupon update(Long id, CouponRequest request) {
        Coupon coupon = getById(id);
        String code = normaliseCode(request.code());
        if (!coupon.getCode().equalsIgnoreCase(code) && couponRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("A coupon with code " + code + " already exists");
        }
        apply(coupon, request, code);
        log.info("Coupon {} updated", coupon.getCode());
        return couponRepository.save(coupon);
    }

    @Transactional
    public Coupon setActive(Long id, boolean active) {
        Coupon coupon = getById(id);
        coupon.setActive(active);
        return couponRepository.save(coupon);
    }

    /** Hides the coupon everywhere (customers can no longer apply it) without deleting it. */
    @Transactional
    public Coupon archive(Long id) {
        Coupon coupon = getById(id);
        if (coupon.isArchived()) {
            return coupon;
        }
        coupon.setArchived(true);
        coupon.setArchivedAt(LocalDateTime.now());
        log.info("Coupon {} archived", coupon.getCode());
        return couponRepository.save(coupon);
    }

    @Transactional
    public Coupon restore(Long id) {
        Coupon coupon = getById(id);
        coupon.setArchived(false);
        coupon.setArchivedAt(null);
        log.info("Coupon {} restored", coupon.getCode());
        return couponRepository.save(coupon);
    }

    /** Refused while any order has redeemed this coupon, so past order history stays intact. */
    @Transactional
    public String deletePermanently(Long id) {
        Coupon coupon = getById(id);
        if (!coupon.isArchived()) {
            throw new BusinessException("Archive " + coupon.getCode() + " before deleting it permanently");
        }
        long redemptions = redemptionRepository.countByCouponId(id);
        if (redemptions > 0) {
            throw new BusinessException(coupon.getCode() + " has been used on " + redemptions
                    + " order(s) and cannot be deleted. It stays hidden while archived.");
        }
        couponRepository.delete(coupon);
        log.warn("Coupon {} permanently deleted", coupon.getCode());
        return coupon.getCode();
    }

    /**
     * Applies a coupon to a freshly placed order: validates it once more against the order's
     * own subtotal (in case anything changed since the cart preview), discounts the order's
     * total, and records the redemption. Throws {@link BusinessException} if the coupon can no
     * longer be used - the caller decides whether to fail the checkout or place the order
     * without the discount.
     */
    @Transactional
    public void redeem(String code, Order order, User user) {
        Coupon coupon = find(code);
        validate(coupon, order.getSubtotalPaise(), user);
        long discount = computeDiscount(coupon, order.getSubtotalPaise());

        order.applyDiscount(discount, coupon.getCode());
        orderRepository.save(order);

        redemptionRepository.save(new CouponRedemption(coupon, user, order, discount));

        coupon.setUsedCount(coupon.getUsedCount() + 1);
        couponRepository.save(coupon);
        log.info("Coupon {} redeemed on order {} for {} paise off", coupon.getCode(),
                order.getOrderNumber(), discount);
    }

    // --------------------------------------------------------------- helpers

    private Coupon find(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Enter a coupon code");
        }
        return couponRepository.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new BusinessException("\"" + code.trim() + "\" is not a valid coupon"));
    }

    private void validate(Coupon coupon, long subtotalPaise, User user) {
        if (coupon.isArchived() || !coupon.isActive()) {
            throw new BusinessException(coupon.getCode() + " is no longer active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.isNotYetStarted(now)) {
            throw new BusinessException(coupon.getCode() + " is not active yet");
        }
        if (coupon.isExpired(now)) {
            throw new BusinessException(coupon.getCode() + " has expired");
        }
        if (subtotalPaise < coupon.getMinOrderPaise()) {
            throw new BusinessException(coupon.getCode() + " needs a minimum order of "
                    + Money.format(coupon.getMinOrderPaise()));
        }
        if (coupon.isUsageLimitReached()) {
            throw new BusinessException(coupon.getCode() + " has reached its usage limit");
        }
        if (coupon.getPerCustomerLimit() != null) {
            long usedByCustomer = redemptionRepository.countByCouponIdAndUserId(coupon.getId(), user.getId());
            if (usedByCustomer >= coupon.getPerCustomerLimit()) {
                throw new BusinessException("You have already used " + coupon.getCode()
                        + " the maximum number of times");
            }
        }
    }

    private long computeDiscount(Coupon coupon, long subtotalPaise) {
        long discount = coupon.getDiscountType() == DiscountType.PERCENT
                ? Math.round(subtotalPaise * coupon.getPercentOff() / 100.0)
                : coupon.getFlatOffPaise();
        if (coupon.getMaxDiscountPaise() != null) {
            discount = Math.min(discount, coupon.getMaxDiscountPaise());
        }
        return Math.min(discount, subtotalPaise);
    }

    private void apply(Coupon coupon, CouponRequest request, String code) {
        coupon.setCode(code);
        coupon.setDescription(blankToNull(request.description()));
        coupon.setDiscountType(request.discountType());

        if (request.discountType() == DiscountType.PERCENT) {
            if (request.percentOff() == null || request.percentOff() <= 0 || request.percentOff() > 100) {
                throw new BusinessException("Percentage off must be between 1 and 100");
            }
            coupon.setPercentOff(request.percentOff());
            coupon.setFlatOffPaise(null);
        } else {
            if (request.flatOffRupees() == null || request.flatOffRupees().signum() <= 0) {
                throw new BusinessException("Enter a flat discount amount greater than zero");
            }
            coupon.setFlatOffPaise(Money.toPaise(request.flatOffRupees()));
            coupon.setPercentOff(null);
        }

        coupon.setMaxDiscountPaise(request.maxDiscountRupees() == null || request.maxDiscountRupees().signum() <= 0
                ? null : Money.toPaise(request.maxDiscountRupees()));
        coupon.setMinOrderPaise(request.minOrderRupees() == null ? 0L : Money.toPaise(request.minOrderRupees()));
        coupon.setTotalUsageLimit(request.totalUsageLimit() == null || request.totalUsageLimit() <= 0
                ? null : request.totalUsageLimit());
        coupon.setPerCustomerLimit(request.perCustomerLimit() == null || request.perCustomerLimit() <= 0
                ? null : request.perCustomerLimit());
        coupon.setStartAt(request.startAt());
        coupon.setEndAt(request.endAt());
        if (coupon.getStartAt() != null && coupon.getEndAt() != null
                && coupon.getEndAt().isBefore(coupon.getStartAt())) {
            throw new BusinessException("The end date cannot be before the start date");
        }
        coupon.setActive(request.active() == null || request.active());
    }

    private static String normaliseCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("Coupon code is required");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public record CouponPreview(String code, long discountPaise) {
    }
}
