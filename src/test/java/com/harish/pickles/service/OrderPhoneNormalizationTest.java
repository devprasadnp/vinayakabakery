package com.harish.pickles.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harish.pickles.exception.BusinessException;
import org.junit.jupiter.api.Test;

class OrderPhoneNormalizationTest {

    @Test
    void storesOnlyTheTenDigitNumber() {
        assertThat(OrderService.normalizePhone("9876543210")).isEqualTo("9876543210");
        assertThat(OrderService.normalizePhone("+91 98765 43210")).isEqualTo("9876543210");
        assertThat(OrderService.normalizePhone("+91-9876543210")).isEqualTo("9876543210");
        assertThat(OrderService.normalizePhone("919876543210")).isEqualTo("9876543210");
        assertThat(OrderService.normalizePhone("09876543210")).isEqualTo("9876543210");
    }

    @Test
    void rejectsNumbersThatAreNotIndianMobiles() {
        assertThatThrownBy(() -> OrderService.normalizePhone("12345")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OrderService.normalizePhone("+91 5876543210")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> OrderService.normalizePhone(null)).isInstanceOf(BusinessException.class);
    }
}
