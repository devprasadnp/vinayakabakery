package com.harish.pickles.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.harish.pickles.dto.OrderDtos.AddressRequest;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.service.GeocodingService.Place;
import com.harish.pickles.service.GeocodingService.Point;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeliveryPinValidatorTest {

    private static final double LAT = 16.9891;
    private static final double LNG = 82.2475;

    private final GeocodingService geocoding = mock(GeocodingService.class);
    private final DeliveryPinValidator validator = new DeliveryPinValidator(geocoding, rules());

    private static DeliveryMapSettingsService rules() {
        DeliveryMapSettingsService svc = mock(DeliveryMapSettingsService.class);
        when(svc.get()).thenReturn(DeliveryMapSettingsService.Rules.defaults());
        return svc;
    }

    private static AddressRequest address(String pincode, String landmark, Double lat, Double lng) {
        return new AddressRequest("A", "a@b.in", "9876543210", "1-2-3 Main Rd", landmark, "Kakinada",
                "Andhra Pradesh", pincode, lat, lng);
    }

    @Test
    void doesNothingWithoutMapsKeys() {
        when(geocoding.isConfigured()).thenReturn(false);
        assertThat(validator.validate(address("533001", null, null, null))).isNull();
    }

    @Test
    void requiresAPinWhenMapsAreOn() {
        when(geocoding.isConfigured()).thenReturn(true);
        assertThatThrownBy(() -> validator.validate(address("533001", null, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("pin");
    }

    @Test
    void acceptsAMatchingPinCodeAndReturnsTheReadableAddress() {
        when(geocoding.isConfigured()).thenReturn(true);
        when(geocoding.reverse(LAT, LNG)).thenReturn(Optional.of(new Place(Set.of("533001"), "Main Rd, Kakinada")));
        assertThat(validator.validate(address("533001", null, LAT, LNG))).isEqualTo("Main Rd, Kakinada");
    }

    @Test
    void rejectsAPinCodeFarFromThePin() {
        when(geocoding.isConfigured()).thenReturn(true);
        when(geocoding.reverse(LAT, LNG)).thenReturn(Optional.of(new Place(Set.of("533001"), "Kakinada")));
        when(geocoding.locate("500001, India")).thenReturn(Optional.of(new Point(17.38, 78.48))); // Hyderabad
        assertThatThrownBy(() -> validator.validate(address("500001", null, LAT, LNG)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("533001");
    }

    @Test
    void toleratesABoundaryPinCodeNearThePin() {
        when(geocoding.isConfigured()).thenReturn(true);
        when(geocoding.reverse(LAT, LNG)).thenReturn(Optional.of(new Place(Set.of("533001"), "Kakinada")));
        when(geocoding.locate("533003, India")).thenReturn(Optional.of(new Point(LAT + 0.03, LNG + 0.03)));
        assertThat(validator.validate(address("533003", null, LAT, LNG))).isEqualTo("Kakinada");
    }

    @Test
    void rejectsALandmarkFarFromThePinButNotWhenItCannotBeFound() {
        when(geocoding.isConfigured()).thenReturn(true);
        when(geocoding.reverse(LAT, LNG)).thenReturn(Optional.of(new Place(Set.of("533001"), "Kakinada")));
        when(geocoding.locate(anyString())).thenReturn(Optional.empty());
        validator.validate(address("533001", "Near Clock Tower", LAT, LNG));

        when(geocoding.locate("Near Clock Tower, Kakinada 533001")).thenReturn(Optional.of(new Point(17.38, 78.48)));
        assertThatThrownBy(() -> validator.validate(address("533001", "Near Clock Tower", LAT, LNG)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Near Clock Tower");
    }
}
