package com.harish.pickles.dto;

import com.harish.pickles.domain.PickleType;
import com.harish.pickles.domain.Product;
import com.harish.pickles.domain.ProductImage;
import com.harish.pickles.domain.SpiceLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class ProductDtos {

    private ProductDtos() {
    }

    /** Admin payload. Prices are accepted in rupees and stored as paise. */
    public record ProductRequest(
            @NotBlank @Size(max = 64) String sku,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 4000) String description,
            @NotNull PickleType pickleType,
            @NotBlank @Size(max = 64) String category,
            @NotNull Long inventoryCategoryId,
            @NotNull SpiceLevel spiceLevel,
            @NotNull @Min(1) BigDecimal price,
            BigDecimal mrp,
            @NotNull @Min(1) Integer weightGrams,
            @Min(1) Integer shelfLifeDays,
            @Size(max = 2000) String ingredients,
            @Size(max = 500) String imageUrl,
            @NotNull @Min(0) Integer stockQty,
            Boolean active) {
    }

    public record ImageResponse(Long id, String url, String originalName, boolean primary) {

        public static ImageResponse from(ProductImage i) {
            return new ImageResponse(i.getId(), i.getUrl(), i.getOriginalName(), i.isPrimary());
        }
    }

    public record ProductResponse(
            Long id,
            String sku,
            String slug,
            String name,
            String description,
            PickleType pickleType,
            String pickleTypeLabel,
            String category,
            Long inventoryCategoryId,
            String inventoryCategoryName,
            String inventoryCategoryIcon,
            SpiceLevel spiceLevel,
            BigDecimal price,
            BigDecimal mrp,
            int discountPercent,
            Integer weightGrams,
            Integer shelfLifeDays,
            String ingredients,
            String imageUrl,
            List<ImageResponse> images,
            Integer stockQty,
            boolean inStock,
            boolean active,
            boolean archived) {

        public static ProductResponse from(Product p) {
            List<ImageResponse> images = p.getImages() == null
                    ? List.of()
                    : p.getImages().stream().map(ImageResponse::from).toList();
            return new ProductResponse(
                    p.getId(), p.getSku(), p.getSlug(), p.getName(), p.getDescription(),
                    p.getPickleType(), p.getPickleType().getLabel(), p.getCategory(),
                    p.getInventoryCategory().getId(), p.getInventoryCategory().getName(),
                    p.getInventoryCategory().getBadge(), p.getSpiceLevel(),
                    Money.toRupees(p.getPricePaise()),
                    p.getMrpPaise() == null ? null : Money.toRupees(p.getMrpPaise()),
                    p.getDiscountPercent(), p.getWeightGrams(), p.getShelfLifeDays(),
                    p.getIngredients(), p.getDisplayImageUrl(), images,
                    p.getStockQty(), p.isInStock(), p.isActive(), p.isArchived());
        }
    }
}
