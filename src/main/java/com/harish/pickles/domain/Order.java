package com.harish.pickles.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Customer-facing reference, e.g. HP-20260910-8F3A21. Also used as PhonePe merchantOrderId. */
    @Column(name = "order_number", nullable = false, length = 64, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status = OrderStatus.CREATED;

    @Column(name = "subtotal_paise", nullable = false)
    private Long subtotalPaise = 0L;

    @Column(name = "shipping_paise", nullable = false)
    private Long shippingPaise = 0L;

    @Column(name = "total_paise", nullable = false)
    private Long totalPaise = 0L;

    /** Code of the coupon applied at checkout, if any. Kept even if the coupon is later archived. */
    @Column(name = "coupon_code", length = 32)
    private String couponCode;

    @Column(name = "discount_paise", nullable = false)
    private Long discountPaise = 0L;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(length = 500)
    private String notes;

    @Column(name = "placed_at")
    private LocalDateTime placedAt;

    /** The courier an admin handed this order to. Null until assigned. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_partner_id")
    private User deliveryPartner;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    /** Road distance from the store to the delivery pin, in km. Null when no pin or no store location. */
    @Column(name = "distance_km")
    private Double distanceKm;

    /** The courier's last reported position, refreshed while the order is out for delivery. */
    @Column(name = "partner_latitude")
    private Double partnerLatitude;

    @Column(name = "partner_longitude")
    private Double partnerLongitude;

    @Column(name = "partner_location_at")
    private LocalDateTime partnerLocationAt;

    /** Hidden from the normal order lists by a super admin, still in the database. */
    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void recalculateTotals(long shippingPaise) {
        this.subtotalPaise = items.stream().mapToLong(OrderItem::getLineTotalPaise).sum();
        this.shippingPaise = shippingPaise;
        this.totalPaise = this.subtotalPaise + shippingPaise - this.discountPaise;
    }

    /**
     * Applied once, right after the order is created, by {@code CouponService.redeem(...)}.
     * Recomputes the total so it never drifts from subtotal + shipping - discount.
     */
    public void applyDiscount(long discountPaise, String couponCode) {
        this.couponCode = couponCode;
        this.discountPaise = discountPaise;
        this.totalPaise = Math.max(0, this.subtotalPaise + this.shippingPaise - discountPaise);
    }

    public int getTotalQuantity() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    public boolean isPayable() {
        return !archived && (status == OrderStatus.CREATED
                || status == OrderStatus.PENDING_PAYMENT
                || status == OrderStatus.PAYMENT_FAILED);
    }

    public String getCustomerName() {
        if (shippingAddress != null && shippingAddress.getContactName() != null) {
            return shippingAddress.getContactName();
        }
        return user != null ? user.getFullName() : "Guest";
    }
}
