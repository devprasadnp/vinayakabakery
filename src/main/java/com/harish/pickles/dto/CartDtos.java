package com.harish.pickles.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record CartItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity) {
    }

    public record CartLine(
            Long itemId,
            Long productId,
            String name,
            String slug,
            String pickleType,
            Integer weightGrams,
            String imageUrl,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineTotal,
            Integer stockQty) {
    }

    public record CartResponse(
            List<CartLine> lines,
            int totalItems,
            BigDecimal subtotal,
            BigDecimal shipping,
            BigDecimal total,
            BigDecimal freeShippingAbove,
            boolean freeShippingApplied) {
    }
}
