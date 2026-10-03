package com.harish.pickles.domain;

public enum SpiceLevel {
    MILD("Mild"),
    MEDIUM("Medium"),
    HOT("Hot"),
    EXTRA_HOT("Extra Hot");

    private final String label;

    SpiceLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
