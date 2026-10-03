package com.harish.pickles.dto;

import com.harish.pickles.domain.Coupon;
import com.harish.pickles.domain.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class CouponDtos {

    private CouponDtos() {
    }

    /**
     * Admin payload. Rupee amounts are accepted the way an admin would type them and
     * converted to paise by {@code CouponService}; only one of {@code percentOff} /
     * {@code flatOffRupees} is used, matching {@code discountType}.
     */
    public record CouponRequest(
            @NotBlank @Size(max = 32) String code,
            @Size(max = 255) String description,
            @NotNull DiscountType discountType,
            Integer percentOff,
            BigDecimal flatOffRupees,
            BigDecimal maxDiscountRupees,
            BigDecimal minOrderRupees,
            Integer totalUsageLimit,
            Integer perCustomerLimit,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Boolean active) {
    }

    public record CouponResponse(
            Long id,
            String code,
            String description,
            DiscountType discountType,
            Integer percentOff,
            BigDecimal flatOffRupees,
            BigDecimal maxDiscountRupees,
            BigDecimal minOrderRupees,
            String discountLabel,
            Integer totalUsageLimit,
            Integer perCustomerLimit,
            int usedCount,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean active,
            boolean archived) {

        public static CouponResponse from(Coupon c) {
            return new CouponResponse(
                    c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(),
                    c.getPercentOff(),
                    c.getFlatOffPaise() == null ? null : Money.toRupees(c.getFlatOffPaise()),
                    c.getMaxDiscountPaise() == null ? null : Money.toRupees(c.getMaxDiscountPaise()),
                    Money.toRupees(c.getMinOrderPaise()),
                    c.describeDiscount(),
                    c.getTotalUsageLimit(), c.getPerCustomerLimit(), c.getUsedCount(),
                    c.getStartAt(), c.getEndAt(), c.isActive(), c.isArchived());
        }
    }
}
