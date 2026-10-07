package com.harish.pickles.domain;

/**
 * Four roles. USER, ADMIN and SUPER_ADMIN are layered, each a superset of the one below:
 * {@code AppUserDetails} grants the lower authorities too, so a SUPER_ADMIN can use every
 * ADMIN screen without a role hierarchy bean.
 *
 * <p>DELIVERY_PARTNER sits beside that ladder rather than on it. It carries no admin
 * authority and is deliberately not "staff": a courier sees only the orders an admin
 * assigned to them, never the whole order book.</p>
 */
public enum Role {
    USER("Customer"),
    ADMIN("Admin"),
    SUPER_ADMIN("Super Admin"),
    DELIVERY_PARTNER("Delivery Partner"),
    /** Runs inventory (foods, categories) and orders; no configuration, accounts or coupons. */
    STORE_MANAGER("Store Manager");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Accounts that may look at any order: admins and store managers. Delivery partners are excluded. */
    public boolean isStaff() {
        return this == ADMIN || this == SUPER_ADMIN || this == STORE_MANAGER;
    }

    public boolean isDeliveryPartner() {
        return this == DELIVERY_PARTNER;
    }
}
