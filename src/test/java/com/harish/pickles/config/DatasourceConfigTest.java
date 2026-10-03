package com.harish.pickles.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * Resolves the real {@code application.yml} the way Spring does, so the nested placeholders in the
 * datasource URL are proven rather than assumed. Uses an isolated environment: whatever DB_* variables
 * happen to be set on the machine running the build cannot leak in.
 */
class DatasourceConfigTest {

    private PropertySourcesPropertyResolver resolver(Map<String, Object> overrides) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        if (!overrides.isEmpty()) {
            sources.addFirst(new MapPropertySource("overrides", overrides));
        }
        for (PropertySource<?> yaml : new YamlPropertySourceLoader().load("application",
                new ClassPathResource("application.yml"))) {
            sources.addLast(yaml);
        }
        return new PropertySourcesPropertyResolver(sources);
    }

    @Test
    void byDefaultUsesLocalhostOnlyBecauseIpv6IsNotAvailableEverywhere() throws IOException {
        // [::1] used to be in this list, but some servers have IPv6 switched off ("Protocol family unavailable"),
        // and the driver then reports that instead of the real refusal from 127.0.0.1.
        assertThat(resolver(Map.of()).getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432/cjgwzcmo_home?sslmode=disable");
    }

    @Test
    void hostListDatabaseNameAndSslModeCanEachBeOverridden() throws IOException {
        var r = resolver(Map.of("DB_HOSTS", "localhost:5432,[::1]:5432", "DB_NAME", "shop", "DB_SSLMODE", "require"));

        assertThat(r.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5432,[::1]:5432/shop?sslmode=require");
    }

    @Test
    void wholeUrlAndLoginCanBeReplacedFromTheEnvironment() throws IOException {
        var r = resolver(Map.of("DB_URL", "jdbc:postgresql://db.internal:6432/x", "DB_USERNAME", "u", "DB_PASSWORD", "p"));

        assertThat(r.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://db.internal:6432/x");
        assertThat(r.getProperty("spring.datasource.username")).isEqualTo("u");
        assertThat(r.getProperty("spring.datasource.password")).isEqualTo("p");
    }

    @Test
    void loginKeepsItsExistingDefaultsWhenNothingIsSet() throws IOException {
        var r = resolver(Map.of());

        assertThat(r.getProperty("spring.datasource.username")).isEqualTo("cjgwzcmo_homeuser");
        // A default is baked into application.yml so the app still starts when the hosting panel
        // offers no way to inject DB_PASSWORD as a real environment variable. Deliberately not
        // asserting the exact value here, so this test file never has to carry the live secret.
        assertThat(r.getProperty("spring.datasource.password")).isNotBlank();
    }
}
