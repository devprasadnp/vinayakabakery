package com.harish.pickles.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A top-level product family shown as an accordion section on the storefront and as a
 * filter everywhere else - "Veg Pickles", "Non-Veg Pickles", "Podi", "Snacks", "Cakes"...
 * Admins can add more of these from the Categories screen without a code change.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventory_categories")
public class InventoryCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80, unique = true)
    private String name;

    @Column(nullable = false, length = 100, unique = true)
    private String slug;

    /** A single emoji shown next to the name. Falls back to a generic tag icon. */
    @Column(length = 8)
    private String icon;

    /** true = always vegetarian, false = always non-veg, null = not dietary-specific. */
    private Boolean veg;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    /** Shown to shoppers. An archived category is always inactive too. */
    @Column(nullable = false)
    private boolean active = true;

    /** Removed from every listing by a super admin, but still in the database. */
    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    public String getBadge() {
        return (icon != null && !icon.isBlank()) ? icon : "🏷️";
    }
}
