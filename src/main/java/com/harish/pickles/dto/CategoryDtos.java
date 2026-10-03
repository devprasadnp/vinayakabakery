package com.harish.pickles.dto;

import com.harish.pickles.domain.InventoryCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 8) String icon,
            Boolean veg,
            Integer displayOrder,
            Boolean active) {
    }

    public record CategoryResponse(
            Long id,
            String name,
            String slug,
            String icon,
            Boolean veg,
            int displayOrder,
            boolean active,
            boolean archived,
            long productCount) {

        public static CategoryResponse from(InventoryCategory c, long productCount) {
            return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getIcon(), c.getVeg(),
                    c.getDisplayOrder(), c.isActive(), c.isArchived(), productCount);
        }
    }
}
