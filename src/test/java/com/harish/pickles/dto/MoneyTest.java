package com.harish.pickles.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void convertsPaiseToRupees() {
        assertThat(Money.toRupees(24900)).isEqualByComparingTo("249.00");
        assertThat(Money.toRupees(0)).isEqualByComparingTo("0.00");
        assertThat(Money.toRupees(5)).isEqualByComparingTo("0.05");
    }

    @Test
    void convertsRupeesToPaiseWithoutDrift() {
        assertThat(Money.toPaise(new BigDecimal("249.00"))).isEqualTo(24900L);
        assertThat(Money.toPaise(new BigDecimal("0.05"))).isEqualTo(5L);
        assertThat(Money.toPaise(new BigDecimal("1299.99"))).isEqualTo(129999L);
    }

    @Test
    void roundTripsEveryValue() {
        for (long paise : new long[]{1, 99, 100, 34999, 104999}) {
            assertThat(Money.toPaise(Money.toRupees(paise))).isEqualTo(paise);
        }
    }
}
