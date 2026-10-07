package com.harish.pickles.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.harish.pickles.exception.BusinessException;
import com.harish.pickles.payment.PaymentProperties.Mode;
import com.harish.pickles.payment.PhonePeProperties.Environment;
import com.harish.pickles.payment.PhonePeSettingsService.Update;
import com.harish.pickles.repository.AppSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:phonepesettings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class PhonePeSettingsServiceTest {

    @Autowired private PhonePeSettingsService service;
    @Autowired private PhonePeProperties phonePe;
    @Autowired private PaymentProperties payments;
    @Autowired private AppSettingRepository settings;
    @Autowired private SecretCipher cipher;

    @Test
    void cipherRoundTripsAndRejectsGarbage() {
        String stored = cipher.encrypt("s3cret-value");
        assertThat(stored).startsWith("enc:").doesNotContain("s3cret");
        assertThat(cipher.decrypt(stored)).isEqualTo("s3cret-value");
        assertThat(cipher.decrypt("enc:AAAA")).isNull();
        assertThat(cipher.decrypt("plain")).isNull();
    }

    @Test
    void savedSettingsGoLiveImmediatelyAndSecretsAreStoredEncrypted() {
        service.save(new Update(true, false, Environment.SANDBOX, "CLIENT123", "topsecret", "1",
                "https://shop.example/payment/callback", 900, "hookuser", "hookpass"));

        assertThat(payments.getMode()).isEqualTo(Mode.PHONEPE);
        assertThat(phonePe.isMock()).isFalse();
        assertThat(phonePe.getClientId()).isEqualTo("CLIENT123");
        assertThat(phonePe.getClientSecret()).isEqualTo("topsecret");
        assertThat(phonePe.getWebhook().getPassword()).isEqualTo("hookpass");
        assertThat(settings.findById(PhonePeSettingsService.CLIENT_SECRET).orElseThrow().getValue())
                .startsWith("enc:").doesNotContain("topsecret");
        assertThat(service.view().secretSet()).isTrue();

        // A blank secret on a later save keeps the stored one.
        service.save(new Update(true, false, Environment.SANDBOX, "CLIENT123", "", "1",
                "https://shop.example/payment/callback", 900, "hookuser", ""));
        assertThat(phonePe.getClientSecret()).isEqualTo("topsecret");

        // Switching PhonePe off returns to the manual QR flow.
        service.save(new Update(false, true, Environment.SANDBOX, "CLIENT123", "", "1",
                "https://shop.example/payment/callback", 900, "hookuser", ""));
        assertThat(payments.getMode()).isEqualTo(Mode.QR);
        assertThat(phonePe.isMock()).isTrue();
    }

    @Test
    void goingLiveWithoutCredentialsIsRefused() {
        assertThatThrownBy(() -> service.save(new Update(true, false, Environment.PRODUCTION, "", "", "1",
                "https://shop.example/payment/callback", 900, "u", "p")))
                .isInstanceOf(BusinessException.class);
    }
}
