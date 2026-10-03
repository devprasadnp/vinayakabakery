package com.harish.pickles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One successful use of a {@link Coupon} on one {@link Order}. This is the source of truth
 * for usage limits: {@code CouponService} counts rows here rather than trusting a customer's
 * session, so limits hold even across concurrent checkouts or a retried request.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "coupon_redemptions")
public class CouponRedemption extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "discount_paise", nullable = false)
    private long discountPaise;

    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt = LocalDateTime.now();

    public CouponRedemption(Coupon coupon, User user, Order order, long discountPaise) {
        this.coupon = coupon;
        this.user = user;
        this.order = order;
        this.discountPaise = discountPaise;
        this.redeemedAt = LocalDateTime.now();
    }
}
