package com.harish.pickles.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.mock.env.MockEnvironment;

class DatabaseAccessFailureAnalyzerTest {

    private static final String REFUSAL = "FATAL: no pg_hba.conf entry for host \"127.0.0.1\", "
            + "user \"wgcxihgz_harifoodsuser\", database \"wgcxihgz_harifoods\", no encryption";
    private static final String SECRET = "s3cret-p@ss";

    /** The shape of the real startup failure: bean creation -> Flyway -> the driver's exception. */
    private static Throwable startupFailureCausedBy(Throwable driverException) {
        return new IllegalStateException("Unable to start web server",
                new BeanCreationException("jwtAuthenticationFilter",
                        new BeanCreationException("flywayInitializer",
                                new RuntimeException("Unable to obtain connection from database", driverException))));
    }

    private static MockEnvironment environment(String url) {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", url)
                .withProperty("spring.datasource.username", "wgcxihgz_harifoodsuser")
                .withProperty("spring.datasource.password", SECRET);
    }

    /** A fake database: each host either accepts the login or fails with the given exception. */
    private static class FakeServer implements DatabaseAccessFailureAnalyzer.Connector {
        final List<String> tried = new ArrayList<>();
        final Map<String, SQLException> failures;

        FakeServer(Map<String, SQLException> failures) {
            this.failures = failures;
        }

        @Override
        public void connect(String jdbcUrl, String user, String password) throws SQLException {
            String host = jdbcUrl.substring("jdbc:postgresql://".length(), jdbcUrl.indexOf('/', "jdbc:postgresql://".length()));
            tried.add(host);
            SQLException failure = failures.get(host);
            if (failure != null) {
                throw failure;
            }
        }
    }

    private static SQLException refusedByPgHba() {
        return new SQLException(REFUSAL, "28000");
    }

    private static SQLException ipv6Unavailable() {
        return new SQLException("The connection attempt failed.", "08001", new SocketException("Protocol family unavailable"));
    }

    private static SQLException noPasswordProvided() {
        return new SQLException("The server requested SCRAM-based authentication, but no password was provided.", "08004");
    }

    private static MockEnvironment environmentWithBlankPassword(String url) {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", url)
                .withProperty("spring.datasource.username", "wgcxihgz_harifoodsuser")
                .withProperty("spring.datasource.password", "");
    }

    // ------------------------------------------------------------------ the diagnosis

    @Test
    void recognisesTheRefusalEvenWhenBuriedUnderSpringFrames() {
        var analyzer = new DatabaseAccessFailureAnalyzer(null, null, List::of);

        FailureAnalysis analysis = analyzer.analyze(startupFailureCausedBy(refusedByPgHba()));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription())
                .contains("no pg_hba.conf entry for host \"127.0.0.1\"")
                .contains("wgcxihgz_harifoodsuser")
                .doesNotContain("Unable to obtain connection");     // Spring's wrapper text is left out
        assertThat(analysis.getAction()).contains("pg_hba.conf").contains("cPanel");
    }

    @Test
    void showsWhatEveryRouteDidAndNamesTheOneThatWorks() {
        // Their server: 127.0.0.1 is refused, IPv6 is switched off - but the machine's own address is allowed.
        var server = new FakeServer(Map.of(
                "localhost:5432", refusedByPgHba(),
                "127.0.0.1:5432", refusedByPgHba(),
                "[::1]:5432", ipv6Unavailable()));
        var analyzer = new DatabaseAccessFailureAnalyzer(
                environment("jdbc:postgresql://localhost:5432,[::1]:5432/wgcxihgz_harifoods?sslmode=disable"),
                server, () -> List.of("203.0.113.7"));

        // The driver reports only the LAST host's failure - the IPv6 one - hiding the real refusal entirely.
        FailureAnalysis analysis = analyzer.analyze(startupFailureCausedBy(ipv6Unavailable()));

        assertThat(analysis.getDescription())
                .contains("localhost:5432").contains("refused by the server's access rules (pg_hba.conf)")
                .contains("[::1]:5432").contains("IPv6 is not available on this server")
                .contains("203.0.113.7:5432").contains("OK");
        assertThat(analysis.getAction()).contains("DB_HOSTS=203.0.113.7:5432").contains("No rebuild is needed");
        assertThat(server.tried).containsExactly("localhost:5432", "[::1]:5432", "127.0.0.1:5432", "203.0.113.7:5432");
    }

    @Test
    void whenNothingWorksItSaysTheDatabaseHostMustAllowIt() {
        var server = new FakeServer(Map.of(
                "localhost:5432", refusedByPgHba(), "127.0.0.1:5432", refusedByPgHba(), "[::1]:5432", ipv6Unavailable()));
        var analyzer = new DatabaseAccessFailureAnalyzer(
                environment("jdbc:postgresql://localhost:5432/db"), server, List::of);

        FailureAnalysis analysis = analyzer.analyze(startupFailureCausedBy(refusedByPgHba()));

        assertThat(analysis.getAction()).doesNotContain("DB_HOSTS=").contains("cannot grant").contains("database host has to allow it").contains("127.0.0.1");
    }

    @Test
    void explainsAMissingDbPasswordInsteadOfProbingEveryRouteWithTheSameEmptyOne() {
        var server = new FakeServer(Map.of());
        var analyzer = new DatabaseAccessFailureAnalyzer(
                environmentWithBlankPassword("jdbc:postgresql://localhost:5432/db"), server, () -> List.of("203.0.113.7"));

        FailureAnalysis analysis = analyzer.analyze(startupFailureCausedBy(noPasswordProvided()));

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("no password was provided");
        assertThat(analysis.getAction()).contains("DB_PASSWORD").contains("environment variable")
                .contains(".env").contains("restart");
        // Every route would fail the exact same way with an empty password - probing is pointless noise.
        assertThat(server.tried).isEmpty();
    }

    @Test
    void classifiesTheCommonOutcomesInPlainEnglish() {
        assertThat(DatabaseAccessFailureAnalyzer.reason(refusedByPgHba())).contains("pg_hba.conf");
        assertThat(DatabaseAccessFailureAnalyzer.reason(ipv6Unavailable())).isEqualTo("IPv6 is not available on this server");
        assertThat(DatabaseAccessFailureAnalyzer.reason(new SQLException("The connection attempt failed.", "08001",
                new ConnectException("Connection refused")))).contains("nothing is listening");
        assertThat(DatabaseAccessFailureAnalyzer.reason(
                new SQLException("FATAL: password authentication failed for user \"u\"", "28P01"))).contains("password is wrong");
        assertThat(DatabaseAccessFailureAnalyzer.reason(
                new SQLException("FATAL: database \"nope\" does not exist", "3D000"))).contains("database \"nope\" does not exist");
        assertThat(DatabaseAccessFailureAnalyzer.reason(noPasswordProvided())).contains("DB_PASSWORD is not set");
    }

    // ------------------------------------------------------------------ safety and scope

    @Test
    void neverPrintsThePassword() {
        var server = new FakeServer(Map.of("localhost:5432", refusedByPgHba()));
        var analyzer = new DatabaseAccessFailureAnalyzer(environment("jdbc:postgresql://localhost:5432/db"), server, List::of);

        FailureAnalysis analysis = analyzer.analyze(startupFailureCausedBy(refusedByPgHba()));

        assertThat(analysis.getDescription() + analysis.getAction()).doesNotContain(SECRET);
    }

    @Test
    void probesOnlyTheConfiguredHostsLoopbacksAndThisMachineWithAHardCap() {
        var server = new FakeServer(Map.of());
        var many = List.of("10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4", "10.0.0.5", "10.0.0.6");
        var analyzer = new DatabaseAccessFailureAnalyzer(environment("jdbc:postgresql://db.example:5555/db"), server, () -> many);

        analyzer.analyze(startupFailureCausedBy(ipv6Unavailable()));

        assertThat(server.tried).hasSize(8).startsWith("db.example:5555", "localhost:5555", "127.0.0.1:5555", "[::1]:5555");
    }

    @Test
    void ignoresEveryOtherFailureAndNeverTouchesTheDatabaseForThem() {
        var server = new FakeServer(Map.of());
        var analyzer = new DatabaseAccessFailureAnalyzer(environment("jdbc:postgresql://localhost:5432/db"), server, List::of);

        assertThat(analyzer.analyze(new IllegalStateException("something else entirely"))).isNull();
        assertThat(analyzer.analyze(new RuntimeException("wrapper",
                new SQLException("FATAL: password authentication failed for user \"x\"", "28P01")))).isNull();
        assertThat(analyzer.analyze(new RuntimeException((String) null))).isNull();
        assertThat(server.tried).isEmpty();
    }

    @Test
    void doesNotProbeWhenTheUrlIsNotPostgres() {
        var server = new FakeServer(Map.of());
        var analyzer = new DatabaseAccessFailureAnalyzer(environment("jdbc:h2:mem:test"), server, List::of);

        FailureAnalysis analysis = analyzer.analyze(startupFailureCausedBy(refusedByPgHba()));

        assertThat(analysis).isNotNull();
        assertThat(server.tried).isEmpty();
    }
}
