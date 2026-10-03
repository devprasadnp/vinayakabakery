package com.harish.pickles.domain;

public enum OrderStatus {
    /** Created in our DB, payment not started yet. */
    CREATED,
    /** Handed off to PhonePe, waiting for the customer to finish paying. */
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED;

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED || this == REFUNDED;
    }
}
