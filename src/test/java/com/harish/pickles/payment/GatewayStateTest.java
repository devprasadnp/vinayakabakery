package com.harish.pickles.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayStateTest {

    @Test
    void mapsPhonePeStates() {
        assertThat(GatewayState.from("COMPLETED")).isEqualTo(GatewayState.COMPLETED);
        assertThat(GatewayState.from("completed")).isEqualTo(GatewayState.COMPLETED);
        assertThat(GatewayState.from("FAILED")).isEqualTo(GatewayState.FAILED);
        assertThat(GatewayState.from("EXPIRED")).isEqualTo(GatewayState.FAILED);
        assertThat(GatewayState.from("PENDING")).isEqualTo(GatewayState.PENDING);
    }

    @Test
    void treatsUnknownAndNullAsPending() {
        assertThat(GatewayState.from(null)).isEqualTo(GatewayState.PENDING);
        assertThat(GatewayState.from("SOMETHING_NEW")).isEqualTo(GatewayState.PENDING);
    }
}
