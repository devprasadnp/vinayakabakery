package com.harish.pickles.domain;

/** Mirrors the PhonePe order states (PENDING / COMPLETED / FAILED) plus our own bookkeeping. */
public enum PaymentStatus {
    INITIATED,
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
