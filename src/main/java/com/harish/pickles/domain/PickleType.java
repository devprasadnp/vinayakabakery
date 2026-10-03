package com.harish.pickles.domain;

/** Dietary classification shown as the green / brown dot on every pack. */
public enum PickleType {
    VEG("Veg"),
    NON_VEG("Non-Veg");

    private final String label;

    PickleType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
