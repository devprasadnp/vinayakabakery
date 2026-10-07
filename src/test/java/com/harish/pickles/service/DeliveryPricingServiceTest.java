package com.harish.pickles.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.domain.AppSetting;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.repository.AppSettingRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeliveryPricingServiceTest {

    private DeliveryPricingService service(Map<String, String> settings) {
        AppSettingRepository repo = mock(AppSettingRepository.class);
        when(repo.findById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return settings.containsKey(key) ? Optional.of(new AppSetting(key, settings.get(key))) : Optional.empty();
        });
        return new DeliveryPricingService(repo, new AppProperties());
    }

    @Test
    void disabledPricingKeepsTheFlatFeeWhateverTheDistance() {
        DeliveryPricingService s = service(Map.of());
        assertThat(s.shippingFor(20000, 25.0)).isEqualTo(6000);
        assertThat(s.shippingFor(20000, null)).isEqualTo(6000);
    }

    @Test
    void defaultFeeCoversTheBaseRadiusThenEachStartedKmIsCharged() {
        DeliveryPricingService s = service(Map.of(
                DeliveryPricingService.ENABLED_KEY, "true",
                DeliveryPricingService.BASE_FEE_KEY, "4000",
                DeliveryPricingService.BASE_KM_KEY, "5",
                DeliveryPricingService.PER_KM_KEY, "1000"));
        assertThat(s.shippingFor(20000, 3.0)).isEqualTo(4000);
        assertThat(s.shippingFor(20000, 5.0)).isEqualTo(4000);
        assertThat(s.shippingFor(20000, 7.2)).isEqualTo(4000 + 3 * 1000);
        assertThat(s.shippingFor(20000, null)).isEqualTo(4000);
    }

    @Test
    void freeShippingThresholdAndEmptyBasketStayFree() {
        DeliveryPricingService s = service(Map.of(
                DeliveryPricingService.ENABLED_KEY, "true", DeliveryPricingService.PER_KM_KEY, "1000"));
        assertThat(s.shippingFor(99900, 30.0)).isZero();
        assertThat(s.shippingFor(0, 30.0)).isZero();
    }

    @Test
    void maxDistanceRefusesFarPins() {
        DeliveryPricingService s = service(Map.of(
                DeliveryPricingService.ENABLED_KEY, "true", DeliveryPricingService.MAX_KM_KEY, "10"));
        s.requireWithinRange(9.9);
        s.requireWithinRange(null);
        assertThatThrownBy(() -> s.requireWithinRange(10.5)).isInstanceOf(BusinessException.class);
    }
}
