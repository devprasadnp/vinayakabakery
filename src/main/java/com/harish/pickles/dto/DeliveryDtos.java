package com.harish.pickles.dto;

import com.harish.pickles.domain.DeliveryPincode;
import com.harish.pickles.domain.Order;
import com.harish.pickles.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    public record PincodeResponse(Long id, String pincode, String areaName, boolean active) {

        public static PincodeResponse from(DeliveryPincode p) {
            return new PincodeResponse(p.getId(), p.getPincode(), p.getAreaName(), p.isActive());
        }
    }

    /** Admin adding PIN codes: several at once, separated by commas, spaces or new lines. */
    public record AddPincodesRequest(String pincodes, String areaName) {
    }

    public record DeliverySettings(boolean restricted, long activePincodes) {
    }

    /** What a delivery partner sees of an order: where to go and what they are carrying. */
    public record DeliveryOrderResponse(
            String orderNumber,
            OrderStatus status,
            String customerName,
            String customerPhone,
            String address,
            String pincode,
            String notes,
            int jars,
            BigDecimal total,
            LocalDateTime assignedAt,
            List<String> items) {

        public static DeliveryOrderResponse from(Order o) {
            var a = o.getShippingAddress();
            return new DeliveryOrderResponse(
                    o.getOrderNumber(), o.getStatus(),
                    a == null ? null : a.getContactName(),
                    a == null ? null : a.getContactPhone(),
                    a == null ? null : a.getSingleLine(),
                    a == null ? null : a.getPincode(),
                    o.getNotes(), o.getTotalQuantity(), Money.toRupees(o.getTotalPaise()), o.getAssignedAt(),
                    o.getItems().stream().map(i -> i.getProductName() + " x " + i.getQuantity()).toList());
        }
    }
}
