package com.harish.pickles.dto;

import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.OrderItem;
import com.harish.pickles.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.Hibernate;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CheckoutRequest(
            @NotNull @Valid AddressRequest address,
            @Size(max = 500) String notes) {
    }

    public record AddressRequest(
            String contactName,
            String contactEmail,
            String contactPhone,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String pincode,
            /** Dropped pin from the checkout map, when Google Maps is configured. Optional. */
            Double latitude,
            Double longitude) {

        public AddressRequest(String contactName, String contactEmail, String contactPhone, String addressLine1,
                              String addressLine2, String city, String state, String pincode) {
            this(contactName, contactEmail, contactPhone, addressLine1, addressLine2, city, state, pincode,
                    null, null);
        }
    }

    public record OrderLine(
            String productName,
            String sku,
            String pickleType,
            Integer weightGrams,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineTotal) {

        public static OrderLine from(OrderItem i) {
            return new OrderLine(i.getProductName(), i.getSku(), i.getPickleType().getLabel(),
                    i.getWeightGrams(), Money.toRupees(i.getUnitPricePaise()), i.getQuantity(),
                    Money.toRupees(i.getLineTotalPaise()));
        }
    }

    public record OrderResponse(
            Long id,
            String orderNumber,
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal shipping,
            BigDecimal total,
            String shippingAddress,
            String contactName,
            String contactPhone,
            String notes,
            LocalDateTime placedAt,
            String deliveryPartner,
            List<OrderLine> items) {

        public static OrderResponse from(Order o) {
            return new OrderResponse(
                    o.getId(), o.getOrderNumber(), o.getStatus(),
                    Money.toRupees(o.getSubtotalPaise()), Money.toRupees(o.getShippingPaise()),
                    Money.toRupees(o.getTotalPaise()),
                    o.getShippingAddress() == null ? null : o.getShippingAddress().getSingleLine(),
                    o.getShippingAddress() == null ? null : o.getShippingAddress().getContactName(),
                    o.getShippingAddress() == null ? null : o.getShippingAddress().getContactPhone(),
                    o.getNotes(), o.getPlacedAt(),
                    // Only read when the service already loaded it - this runs outside any transaction.
                    o.getDeliveryPartner() != null && Hibernate.isInitialized(o.getDeliveryPartner())
                            ? o.getDeliveryPartner().getFullName() : null,
                    o.getItems().stream().map(OrderLine::from).toList());
        }
    }

    /** Returned by POST /api/orders/{orderNumber}/pay — send the browser to {@code redirectUrl}. */
    public record PaymentInitResponse(
            String orderNumber,
            String merchantOrderId,
            String providerOrderId,
            BigDecimal amount,
            String redirectUrl,
            String state) {
    }

    public record PaymentStatusResponse(
            String orderNumber,
            OrderStatus orderStatus,
            String paymentStatus,
            String paymentMode,
            String transactionId,
            BigDecimal amount) {
    }

    /** Cheap poll target for the "scan to pay" page — just the order status, no gateway call. */
    public record OrderStatusResponse(
            String orderNumber,
            OrderStatus status) {
    }
}
