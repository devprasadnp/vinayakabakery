package com.harish.pickles.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

/**
 * A food listing. {@code pricePaise} is the selling price in whole paise so that
 * INR 249.00 is stored as 24900 and no floating point rounding can creep in.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String sku;

    @Column(nullable = false, length = 180, unique = true)
    private String slug;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickle_type", nullable = false, length = 16)
    private PickleType pickleType;

    @Column(nullable = false, length = 64)
    private String category;

    /** The top-level family this product lives under - Veg Pickles, Podi, Snacks, Cakes... */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_category_id", nullable = false)
    private InventoryCategory inventoryCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "spice_level", nullable = false, length = 16)
    private SpiceLevel spiceLevel = SpiceLevel.MEDIUM;

    @Column(name = "price_paise", nullable = false)
    private Long pricePaise;

    @Column(name = "mrp_paise")
    private Long mrpPaise;

    @Column(name = "weight_grams", nullable = false)
    private Integer weightGrams;

    @Column(name = "shelf_life_days", nullable = false)
    private Integer shelfLifeDays = 365;

    @Column(columnDefinition = "text")
    private String ingredients;

    /** Optional external image URL, used only when no photo has been uploaded. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "stock_qty", nullable = false)
    private Integer stockQty = 0;

    /** Listed on the storefront. An archived product is always unlisted too. */
    @Column(nullable = false)
    private boolean active = true;

    /** Removed from every listing by a super admin, but still in the database. */
    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("primary DESC, sortOrder ASC, id ASC")
    private List<ProductImage> images = new ArrayList<>();

    public boolean isInStock() {
        return active && !archived && stockQty != null && stockQty > 0;
    }

    public boolean isVeg() {
        return pickleType == PickleType.VEG;
    }

    /** Discount percentage off MRP, or 0 when there is no MRP or no saving. */
    public int getDiscountPercent() {
        if (mrpPaise == null || mrpPaise <= pricePaise) {
            return 0;
        }
        return (int) Math.round((mrpPaise - pricePaise) * 100.0 / mrpPaise);
    }

    public void addImage(ProductImage image) {
        image.setProduct(this);
        images.add(image);
    }

    public ProductImage getPrimaryImage() {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream().filter(ProductImage::isPrimary).findFirst().orElse(images.get(0));
    }

    /**
     * Uploaded photo first, then any external URL the admin typed, then {@code null}
     * so the template can fall back to its illustrated placeholder.
     */
    public String getDisplayImageUrl() {
        ProductImage primary = getPrimaryImage();
        if (primary != null) {
            return primary.getUrl();
        }
        return (imageUrl != null && !imageUrl.isBlank()) ? imageUrl : null;
    }

    public boolean isHasPhoto() {
        return getDisplayImageUrl() != null;
    }
}
