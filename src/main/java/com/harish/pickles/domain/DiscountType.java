package com.harish.pickles.domain;

/** How a coupon's value is interpreted. */
public enum DiscountType {

    PERCENT("% off"),
    FLAT("₹ off");

    private final String label;

    DiscountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
