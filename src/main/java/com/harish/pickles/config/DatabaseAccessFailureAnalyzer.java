package com.harish.pickles.config;

import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * Explains a startup failure caused by not being able to reach PostgreSQL - and finds a way round it.
 *
 * <p>The PostgreSQL driver only reports the <em>last</em> host it tried, so with several routes the
 * useful refusal ("no pg_hba.conf entry for host 127.0.0.1") can be hidden behind an unrelated one
 * ("Protocol family unavailable"), and either way it arrives buried under 200 lines of Spring, Flyway and
 * Hikari frames. When startup has already failed like this, this analyzer re-tries the login on each route
 * from this very server - the configured hosts, the loopbacks and the machine's own addresses - using the
 * application's own credentials, and reports what each one did. If one works it names the exact
 * {@code DB_HOSTS} value to use, so the fix can be read straight out of the log.</p>
 *
 * <p>It runs only after startup has failed, only ever connects to this machine's own addresses and the hosts
 * already configured, and never prints the password. Registered in {@code META-INF/spring.factories}.</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseAccessFailureAnalyzer extends AbstractFailureAnalyzer<Throwable> {

    private static final String PG_HBA = "no pg_hba.conf entry";
    private static final String NO_PASSWORD = "no password was provided";
    private static final int MAX_ROUTES = 8;
    private static final Pattern POSTGRES_URL = Pattern.compile("^jdbc:postgresql://([^/?]+)(?:/([^?]*))?(?:\\?(.*))?$");
    private static final Pattern SSLMODE = Pattern.compile("(?:^|&)sslmode=([^&]+)");

    /** Tries one login and throws if it does not work. */
    @FunctionalInterface
    interface Connector {
        void connect(String jdbcUrl, String user, String password) throws SQLException;
    }

    private record Route(String hostPort, String outcome, boolean works) {
    }

    private final Environment environment;
    private final Connector connector;
    private final Supplier<List<String>> machineAddresses;

    /**
     * The one public constructor - Spring Boot only uses a single public constructor, and hands it the
     * environment so the analyzer can see the datasource settings the failed startup used.
     */
    public DatabaseAccessFailureAnalyzer(Environment environment) {
        this(environment, (url, user, password) -> DriverManager.getConnection(url, user, password).close(),
                DatabaseAccessFailureAnalyzer::thisMachine);
    }

    DatabaseAccessFailureAnalyzer(Environment environment, Connector connector, Supplier<List<String>> machineAddresses) {
        this.environment = environment;
        this.connector = connector;
        this.machineAddresses = machineAddresses;
    }

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, Throwable cause) {
        List<Throwable> everything = flatten(rootFailure);
        if (everything.stream().noneMatch(DatabaseAccessFailureAnalyzer::isConnectionProblem)) {
            return null;
        }
        boolean passwordMissing = isPasswordMissing(everything);
        List<Route> routes = passwordMissing ? List.of() : tryEveryRoute();
        String refusal = everything.stream().map(Throwable::getMessage)
                .filter(m -> m != null && m.contains(PG_HBA)).reduce((first, deeper) -> deeper)
                .map(DatabaseAccessFailureAnalyzer::fatalLine).orElse(null);
        boolean refused = refusal != null || routes.stream().anyMatch(r -> r.outcome().contains("pg_hba.conf"));

        StringBuilder description = new StringBuilder("The application could not connect to the database.\n");
        if (passwordMissing) {
            description.append("\nThe server said:\n\n    ")
                    .append("FATAL: The server requested SCRAM-based authentication, but no password was provided.\n");
        }
        if (refusal != null) {
            description.append("\nThe server said:\n\n    ").append(refusal).append('\n');
        }
        if (!routes.isEmpty()) {
            description.append("\nEach route was tried again from this server with the application's own login:\n\n");
            int width = routes.stream().mapToInt(r -> r.hostPort().length()).max().orElse(0);
            for (Route r : routes) {
                description.append(String.format("    %-" + width + "s  %s%n", r.hostPort(), r.outcome()));
            }
        }

        List<String> working = routes.stream().filter(Route::works).map(Route::hostPort).toList();
        return new FailureAnalysis(description.toString(), action(working, refused, passwordMissing), deepest(rootFailure));
    }

    /**
     * Postgres refuses an empty password outright under SCRAM auth, the same way from every host, so
     * re-probing routes with that same blank password would only repeat the identical failure and hide
     * the real fix behind a wall of noise. This is almost always a missing {@code DB_PASSWORD} environment
     * variable under an external Tomcat (the git-ignored {@code .env} file is never read there).
     */
    private boolean isPasswordMissing(List<Throwable> everything) {
        if (everything.stream().anyMatch(t -> t.getMessage() != null && t.getMessage().contains(NO_PASSWORD))) {
            return true;
        }
        boolean sqlState08004 = everything.stream().anyMatch(
                t -> t instanceof SQLException sql && "08004".equals(sql.getSQLState()));
        String password = environment == null ? null : environment.getProperty("spring.datasource.password");
        return sqlState08004 && (password == null || password.isBlank());
    }

    // ------------------------------------------------------------------ probing

    private List<Route> tryEveryRoute() {
        if (environment == null) {
            return List.of();
        }
        String url = environment.getProperty("spring.datasource.url");
        String user = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        Matcher m = url == null ? null : POSTGRES_URL.matcher(url);
        if (m == null || !m.matches() || user == null) {
            return List.of();
        }
        String database = m.group(2) == null ? "" : m.group(2);
        Matcher ssl = SSLMODE.matcher(m.group(3) == null ? "" : m.group(3));
        String sslmode = ssl.find() ? ssl.group(1) : "disable";

        List<Route> routes = new ArrayList<>();
        for (String hostPort : candidateRoutes(m.group(1))) {
            String probeUrl = "jdbc:postgresql://" + hostPort + "/" + database
                    + "?sslmode=" + sslmode + "&connectTimeout=3&loginTimeout=4";
            try {
                connector.connect(probeUrl, user, password);
                routes.add(new Route(hostPort, "OK - this login works here", true));
            } catch (Exception ex) {
                routes.add(new Route(hostPort, reason(ex), false));
            }
        }
        return routes;
    }

    /** The configured hosts first, then the usual local routes, capped so a failing start stays quick. */
    private List<String> candidateRoutes(String configuredHosts) {
        Set<String> routes = new LinkedHashSet<>();
        String port = "5432";
        for (String host : configuredHosts.split(",")) {
            String withPort = hasPort(host) ? host.trim() : host.trim() + ":5432";
            routes.add(withPort);
            if (routes.size() == 1) {
                port = withPort.substring(withPort.lastIndexOf(':') + 1);
            }
        }
        for (String host : List.of("localhost", "127.0.0.1", "[::1]")) {
            routes.add(host + ":" + port);
        }
        for (String address : machineAddresses.get()) {
            routes.add(address + ":" + port);
        }
        return routes.stream().limit(MAX_ROUTES).toList();
    }

    private static boolean hasPort(String host) {
        String h = host.trim();
        return h.startsWith("[") ? h.contains("]:") : h.contains(":");
    }

    /** This machine's own non-loopback addresses and host name - the ones a hosting panel may have allowed. */
    static List<String> thisMachine() {
        Set<String> found = new LinkedHashSet<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress a : Collections.list(nic.getInetAddresses())) {
                    if (a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()) {
                        continue;
                    }
                    if (a instanceof Inet4Address) {
                        found.add(a.getHostAddress());
                    } else if (a instanceof Inet6Address) {
                        String text = a.getHostAddress();
                        found.add("[" + (text.contains("%") ? text.substring(0, text.indexOf('%')) : text) + "]");
                    }
                }
            }
        } catch (SocketException ignored) {
            // best effort only
        }
        try {
            found.add(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException ignored) {
            // best effort only
        }
        return new ArrayList<>(found);
    }

    // ------------------------------------------------------------------ wording

    private static String action(List<String> working, boolean refused, boolean passwordMissing) {
        StringBuilder sb = new StringBuilder();
        if (passwordMissing) {
            sb.append("DB_PASSWORD resolved to empty, so the login carried no password at all - PostgreSQL refuses\n")
                    .append("that outright under SCRAM authentication, the same way no matter which host answers, so\n")
                    .append("routes were not re-probed here.\n\n")
                    .append("The git-ignored .env file is only read when the app is started with\n")
                    .append("`mvn spring-boot:run` or `java -jar ...` from the project directory - it is never read by\n")
                    .append("an external Tomcat, including a cPanel \"Application Manager\" Java app. Set DB_PASSWORD as\n")
                    .append("a real environment variable for that process instead:\n")
                    .append("  - cPanel Application Manager (\"Setup Java App\"): open the app's page -> \"Environment\n")
                    .append("    variables\" -> add DB_PASSWORD (and SPRING_MAIL_PASSWORD, JWT_SECRET, etc. the same way)\n")
                    .append("    -> Save, then restart the app from that page.\n")
                    .append("  - A Tomcat you manage yourself: export it in bin/setenv.sh, or add\n")
                    .append("    -DDB_PASSWORD=... to CATALINA_OPTS, then restart Tomcat.\n")
                    .append("Then restart the application.");
            return sb.toString();
        }
        if (!working.isEmpty()) {
            sb.append("This server CAN log in through: ").append(String.join(", ", working)).append("\n\n")
                    .append("Tell the application to use it - set the environment variable\n\n")
                    .append("    DB_HOSTS=").append(String.join(",", working)).append("\n\n")
                    .append("(or -DDB_HOSTS=... in CATALINA_OPTS) and restart. No rebuild is needed.");
            return sb.toString();
        }
        if (refused) {
            sb.append("The database server is reachable but its access rules (pg_hba.conf) do not let this user in from\n")
                    .append("this server, and none of the routes above works. The application cannot grant itself access -\n")
                    .append("the database host has to allow it (127.0.0.1 is the usual address to allow):\n")
                    .append("  - cPanel: \"Remote PostgreSQL\" / \"PostgreSQL Databases\" -> allow 127.0.0.1 (or the server's own\n")
                    .append("    address), and make sure the user is added to the database with privileges.\n")
                    .append("  - or ask the host to add:  host  <database>  <user>  127.0.0.1/32  scram-sha-256\n")
                    .append("    (md5 on older servers) to pg_hba.conf and reload PostgreSQL.\n")
                    .append("Then restart the application. If the host uses SSL only, set DB_SSLMODE=require.");
            return sb.toString();
        }
        return "Check that PostgreSQL is running and that DB_HOSTS / DB_URL, DB_NAME, DB_USERNAME and DB_PASSWORD are"
                + " right for this server. Set DB_SSLMODE=require if the database host insists on SSL.";
    }

    /** One short, plain-English outcome for a failed login attempt. */
    static String reason(Throwable failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (message.contains(PG_HBA)) {
            return "refused by the server's access rules (pg_hba.conf) - no entry allows this user from here";
        }
        if (message.contains(NO_PASSWORD)) {
            return "rejected - no password was provided (DB_PASSWORD is not set for this process)";
        }
        if (message.contains("password authentication failed")) {
            return "reached the server, but the user name or password is wrong";
        }
        if (message.contains("does not exist")) {
            return "reached the server, but " + firstLine(message).replace("FATAL: ", "");
        }
        Throwable root = deepest(failure);
        String rootMessage = root.getMessage() == null ? "" : root.getMessage();
        if (rootMessage.contains("Protocol family unavailable")) {
            return "IPv6 is not available on this server";
        }
        if (root instanceof ConnectException) {
            return "nothing is listening there (connection refused)";
        }
        if (root instanceof SocketTimeoutException || rootMessage.contains("timed out")) {
            return "timed out";
        }
        if (root instanceof UnknownHostException) {
            return "unknown host name";
        }
        String line = firstLine(message.isBlank() ? root.toString() : message);
        return line.length() > 110 ? line.substring(0, 110) + "..." : line;
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isConnectionProblem(Throwable t) {
        String message = t.getMessage() == null ? "" : t.getMessage();
        if (message.contains(PG_HBA) || message.contains("Protocol family unavailable") || message.contains(NO_PASSWORD)) {
            return true;
        }
        if (t instanceof SQLException sql && sql.getSQLState() != null
                && (sql.getSQLState().startsWith("08") || sql.getSQLState().equals("28000"))) {
            return true;
        }
        return t instanceof SocketException || t instanceof UnknownHostException;
    }

    /** Every throwable reachable through causes and suppressed exceptions, outermost first. */
    private static List<Throwable> flatten(Throwable root) {
        List<Throwable> out = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(root, out, seen);
        return out;
    }

    private static void collect(Throwable t, List<Throwable> out, Set<Throwable> seen) {
        if (t == null || !seen.add(t)) {
            return;
        }
        out.add(t);
        collect(t.getCause(), out, seen);
        for (Throwable s : t.getSuppressed()) {
            collect(s, out, seen);
        }
    }

    private static Throwable deepest(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** Spring wraps the driver's text inside its own messages; keep only the driver's "FATAL: ..." line. */
    static String fatalLine(String message) {
        int marker = message.indexOf(PG_HBA);
        int fatal = message.lastIndexOf("FATAL:", marker);
        return firstLine(message.substring(fatal >= 0 ? fatal : marker));
    }

    private static String firstLine(String text) {
        return text.lines().findFirst().orElse(text).trim();
    }
}
