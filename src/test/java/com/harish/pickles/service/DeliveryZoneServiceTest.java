package com.harish.pickles.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.harish.pickles.domain.AppSetting;
import com.harish.pickles.domain.DeliveryPincode;
import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.repository.AppSettingRepository;
import com.harish.pickles.repository.DeliveryPincodeRepository;
import com.harish.pickles.service.DeliveryZoneService.Availability;
import com.harish.pickles.service.DeliveryZoneService.BulkResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeliveryZoneServiceTest {

    private DeliveryPincodeRepository pincodes;
    private AppSettingRepository settings;
    private DeliveryZoneService service;

    @BeforeEach
    void setUp() {
        pincodes = mock(DeliveryPincodeRepository.class);
        settings = mock(AppSettingRepository.class);
        service = new DeliveryZoneService(pincodes, settings);
    }

    private void restrictionIs(boolean on) {
        when(settings.findById(DeliveryZoneService.RESTRICT_KEY))
                .thenReturn(Optional.of(new AppSetting(DeliveryZoneService.RESTRICT_KEY, Boolean.toString(on))));
    }

    private void listed(String pincode, String area, boolean active) {
        DeliveryPincode p = new DeliveryPincode(pincode, area);
        p.setActive(active);
        when(pincodes.findByPincode(pincode)).thenReturn(Optional.of(p));
    }

    // ---------------------------------------------------------------- the switch

    @Test
    void restrictionDefaultsToOffSoAnUpgradedShopKeepsTakingOrders() {
        when(settings.findById(DeliveryZoneService.RESTRICT_KEY)).thenReturn(Optional.empty());

        assertThat(service.isRestricted()).isFalse();
        assertThatCode(() -> service.requireDeliverable("999999")).doesNotThrowAnyException();
    }

    @Test
    void whileOffEveryWellFormedPincodeIsDeliverable() {
        restrictionIs(false);

        assertThat(service.check("560001").deliverable()).isTrue();
    }

    // ------------------------------------------------------------- restriction on

    @Test
    void listedActivePincodeIsAccepted() {
        restrictionIs(true);
        listed("533001", "Kakinada", true);

        Availability result = service.check("533001");

        assertThat(result.deliverable()).isTrue();
        assertThat(result.message()).contains("Kakinada");
        assertThatCode(() -> service.requireDeliverable("533001")).doesNotThrowAnyException();
    }

    @Test
    void unlistedPincodeIsRejectedWithTheNoDeliveryPartnerMessage() {
        restrictionIs(true);
        when(pincodes.findByPincode("560001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireDeliverable("560001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("don't have a delivery partner")
                .hasMessageContaining("560001");
    }

    @Test
    void pausedPincodeIsRejectedJustLikeAnUnlistedOne() {
        restrictionIs(true);
        listed("533002", null, false);

        assertThat(service.check("533002").deliverable()).isFalse();
        assertThatThrownBy(() -> service.requireDeliverable("533002")).isInstanceOf(BusinessException.class);
    }

    @Test
    void surroundingWhitespaceDoesNotDefeatTheCheck() {
        restrictionIs(true);
        listed("533001", null, true);

        assertThat(service.check("  533001 ").deliverable()).isTrue();
    }

    @Test
    void malformedPincodesAreRejectedBeforeTheDatabaseIsAsked() {
        restrictionIs(true);

        for (String bad : new String[] {null, "", "abc", "53300", "5330011", "033001", "5330 1"}) {
            Availability result = service.check(bad);
            assertThat(result.deliverable()).as("input %s", bad).isFalse();
            assertThat(result.message()).contains("valid 6 digit");
        }
        verify(pincodes, never()).findByPincode(any());
    }

    @Test
    void missingPincodeIsRefusedWhileRestricted() {
        restrictionIs(true);

        assertThatThrownBy(() -> service.requireDeliverable(null)).isInstanceOf(BusinessException.class);
    }

    // -------------------------------------------------------------- turning it on

    @Test
    void cannotSwitchTheRestrictionOnWithNothingToDeliverTo() {
        when(pincodes.countByActiveTrue()).thenReturn(0L);

        assertThatThrownBy(() -> service.setRestricted(true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one active PIN code");
        verify(settings, never()).save(any());
    }

    @Test
    void switchingOnStoresTheSetting() {
        when(pincodes.countByActiveTrue()).thenReturn(2L);
        when(settings.findById(DeliveryZoneService.RESTRICT_KEY)).thenReturn(Optional.empty());

        service.setRestricted(true);

        org.mockito.ArgumentCaptor<AppSetting> saved = org.mockito.ArgumentCaptor.forClass(AppSetting.class);
        verify(settings).save(saved.capture());
        assertThat(saved.getValue().getValue()).isEqualTo("true");
    }

    @Test
    void switchingOffIsAlwaysAllowed() {
        when(pincodes.countByActiveTrue()).thenReturn(0L);
        when(settings.findById(DeliveryZoneService.RESTRICT_KEY)).thenReturn(Optional.empty());

        assertThatCode(() -> service.setRestricted(false)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ bulk add

    @Test
    void addAllSplitsOnCommasSpacesAndNewlinesAndReportsEveryOutcome() {
        DeliveryPincode existing = new DeliveryPincode("533002", null);
        when(pincodes.findByPincodeIn(any(Collection.class))).thenReturn(List.of(existing));

        BulkResult result = service.addAll("533001, 533002;533003\n533003  12345 abc 033009", "Kakinada");

        assertThat(result.added()).containsExactly("533001", "533003");
        assertThat(result.alreadyListed()).containsExactly("533002");
        assertThat(result.invalid()).containsExactly("12345", "abc", "033009");
        verify(pincodes, org.mockito.Mockito.times(2)).save(any(DeliveryPincode.class));
    }

    @Test
    void addAllAppliesTheAreaNameToNewRows() {
        when(pincodes.findByPincodeIn(any(Collection.class))).thenReturn(List.of());

        service.addAll("533001", "  Kakinada  ");

        org.mockito.ArgumentCaptor<DeliveryPincode> saved = org.mockito.ArgumentCaptor.forClass(DeliveryPincode.class);
        verify(pincodes).save(saved.capture());
        assertThat(saved.getValue().getAreaName()).isEqualTo("Kakinada");
        assertThat(saved.getValue().isActive()).isTrue();
    }

    @Test
    void addAllRejectsEmptyInput() {
        assertThatThrownBy(() -> service.addAll("  ,  ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one");
        assertThatThrownBy(() -> service.addAll(null, null)).isInstanceOf(BusinessException.class);
    }
}
