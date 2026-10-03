package com.harish.pickles.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Tiny helper so paise never leak into the UI as raw integers. */
public final class Money {

    private static final Locale INDIA = new Locale("en", "IN");

    private Money() {
    }

    public static BigDecimal toRupees(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    public static long toPaise(BigDecimal rupees) {
        return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** e.g. 24900 -> "₹249.00" */
    public static String format(long paise) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(INDIA);
        return nf.format(toRupees(paise));
    }
}
