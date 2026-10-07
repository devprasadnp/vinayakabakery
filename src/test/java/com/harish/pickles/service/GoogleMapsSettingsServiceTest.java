package com.harish.pickles.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.harish.pickles.config.GoogleMapsProperties;
import com.harish.pickles.repository.AppSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:mapssettings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class GoogleMapsSettingsServiceTest {

    @Autowired private GoogleMapsSettingsService service;
    @Autowired private GoogleMapsProperties maps;
    @Autowired private AppSettingRepository settings;

    @Test
    void savedKeysApplyImmediatelyAndTheServerKeyIsEncrypted() {
        service.save("BROWSER-KEY", "SERVER-KEY", false);
        assertThat(maps.getApiKey()).isEqualTo("BROWSER-KEY");
        assertThat(maps.effectiveServerKey()).isEqualTo("SERVER-KEY");
        assertThat(settings.findById(GoogleMapsSettingsService.SERVER_KEY).orElseThrow().getValue())
                .startsWith("enc:").doesNotContain("SERVER-KEY");

        // Blank server key keeps the saved one.
        service.save("BROWSER-2", "", false);
        assertThat(maps.getApiKey()).isEqualTo("BROWSER-2");
        assertThat(maps.effectiveServerKey()).isEqualTo("SERVER-KEY");

        // Removing it falls back to the browser key for server calls.
        service.save("BROWSER-2", "", true);
        assertThat(service.view().serverKeySet()).isFalse();
        assertThat(maps.effectiveServerKey()).isEqualTo("BROWSER-2");

        // A blank browser key switches maps off.
        service.save("", "", false);
        assertThat(maps.isConfigured()).isFalse();
        assertThat(maps.isServerConfigured()).isFalse();
    }
}
