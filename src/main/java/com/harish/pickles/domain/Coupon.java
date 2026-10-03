package com.harish.pickles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A discount code a customer can enter on the cart or checkout page.
 *
 * <p>Either {@code percentOff} (with {@code discountType == PERCENT}) or {@code flatOffPaise}
 * (with {@code discountType == FLAT}) is set, never both. {@code usedCount} is the running
 * total of successful redemptions and is only ever incremented from
 * {@code CouponService.redeem(...)}, inside the same transaction that records the
 * {@link CouponRedemption}, so it always matches the redemption history.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Always stored upper-cased, e.g. SAVE10. */
    @Column(nullable = false, length = 32, unique = true)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private DiscountType discountType;

    /** 1-100. Set only when {@code discountType == PERCENT}. */
    @Column(name = "percent_off")
    private Integer percentOff;

    /** Whole paise. Set only when {@code discountType == FLAT}. */
    @Column(name = "flat_off_paise")
    private Long flatOffPaise;

    /** Optional cap on the discount amount, most useful alongside PERCENT. */
    @Column(name = "max_discount_paise")
    private Long maxDiscountPaise;

    @Column(name = "min_order_paise", nullable = false)
    private long minOrderPaise = 0L;

    /** Overall redemption cap across every customer. Null means unlimited. */
    @Column(name = "total_usage_limit")
    private Integer totalUsageLimit;

    /** Redemption cap per customer. Null means unlimited. */
    @Column(name = "per_customer_limit")
    private Integer perCustomerLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    /** Null means "available immediately". */
    @Column(name = "start_at")
    private LocalDateTime startAt;

    /** Null means "never expires". */
    @Column(name = "end_at")
    private LocalDateTime endAt;

    /** An admin switch independent of the date window - lets a coupon be paused instantly. */
    @Column(nullable = false)
    private boolean active = true;

    /** Hidden from every listing by a super admin, still in the database. */
    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    public boolean isExpired(LocalDateTime asOf) {
        return endAt != null && asOf.isAfter(endAt);
    }

    public boolean isNotYetStarted(LocalDateTime asOf) {
        return startAt != null && asOf.isBefore(startAt);
    }

    public boolean isUsageLimitReached() {
        return totalUsageLimit != null && usedCount >= totalUsageLimit;
    }

    /** One-line summary for admin listings, e.g. "10% off, up to ₹100" or "₹150 off". */
    public String describeDiscount() {
        if (discountType == DiscountType.PERCENT) {
            String base = percentOff + "% off";
            return maxDiscountPaise != null ? base + " (max " + com.harish.pickles.dto.Money.format(maxDiscountPaise) + ")" : base;
        }
        return com.harish.pickles.dto.Money.format(flatOffPaise) + " off";
    }
}
