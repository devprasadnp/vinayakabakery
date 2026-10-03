import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds out which way of connecting to PostgreSQL actually works from THIS machine, and explains
 * the failures. Sends no data other than the login itself and reads nothing but the server version.
 *
 * <p>Run it on the server that hosts Tomcat, using the PostgreSQL driver already inside the WAR:</p>
 * <pre>
 *   cd &lt;tomcat webapp folder&gt;/ROOT/WEB-INF/lib          (the exploded WAR)
 *   export DB_PASSWORD='the database user password'
 *   java -cp postgresql-42.7.4.jar /path/to/DbCheck.java  DATABASE_NAME  DATABASE_USER  [extra hosts...]
 * </pre>
 * Extra hosts are tried in addition to localhost, 127.0.0.1 and [::1] - pass the server's own
 * public IP or host name here, since hosting panels often whitelist that one instead of loopback.
 * The password is read from the DB_PASSWORD environment variable so it never appears in the
 * process list or shell history. Set DB_PORT if the server is not on 5432.
 */
public class DbCheck {

    public static void main(String[] args) {
        String password = System.getenv("DB_PASSWORD");
        if (args.length < 2 || password == null) {
            System.err.println("usage: DB_PASSWORD=... java -cp postgresql.jar DbCheck.java DATABASE USER [extra hosts...]");
            System.exit(2);
        }
        String database = args[0];
        String user = args[1];
        int port = Integer.parseInt(System.getenv().getOrDefault("DB_PORT", "5432"));

        Set<String> hosts = new LinkedHashSet<>(List.of("localhost", "127.0.0.1", "[::1]"));
        for (int i = 2; i < args.length; i++) {
            hosts.add(args[i].contains(":") && !args[i].startsWith("[") ? "[" + args[i] + "]" : args[i]);
        }

        System.out.printf("Trying database '%s' as '%s' on port %d%n%n", database, user, port);
        List<String> working = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String host : hosts) {
            for (String sslmode : new String[] {"disable", "require"}) {
                String url = "jdbc:postgresql://" + host + ":" + port + "/" + database
                        + "?sslmode=" + sslmode + "&connectTimeout=5&loginTimeout=8";
                try (Connection c = DriverManager.getConnection(url, user, password);
                     Statement s = c.createStatement();
                     ResultSet rs = s.executeQuery("select version()")) {
                    rs.next();
                    String v = rs.getString(1);
                    System.out.printf("  OK    %-22s sslmode=%-8s %s%n", host, sslmode, v.substring(0, Math.min(v.length(), 40)));
                    working.add("jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode=" + sslmode);
                } catch (SQLException e) {
                    String message = firstLine(e.getMessage());
                    System.out.printf("  FAIL  %-22s sslmode=%-8s [%s] %s%n", host, sslmode, e.getSQLState(), message);
                    failures.add(message);
                }
            }
        }

        System.out.println();
        if (!working.isEmpty()) {
            System.out.println("These work - put one of them in spring.datasource.url (or SPRING_DATASOURCE_URL):");
            working.forEach(u -> System.out.println("    " + u));
            return;
        }
        System.out.println("Nothing connected.");
        System.out.println(hint(failures));
        System.exit(1);
    }

    /** Turns the raw server messages into the one thing the person should do next. */
    static String hint(List<String> messages) {
        String all = String.join("\n", messages);
        if (all.contains("no pg_hba.conf entry")) {
            return """
                    The server answered, but its access rules (pg_hba.conf) have no entry letting this user
                    reach this database from this address. Nothing in the application can fix that - the
                    database host has to allow it:
                      - cPanel: open "Remote PostgreSQL" (or "PostgreSQL Databases") and add the address named in
                        the error (for example 127.0.0.1 or the server's own IP) as an allowed host, and make sure the
                        user has been added to the database with privileges.
                      - otherwise ask the host to add a line such as
                            host  DATABASE  USER  127.0.0.1/32  scram-sha-256      (md5 on older servers)
                        to pg_hba.conf and reload PostgreSQL.
                      - if only a server IP/host name is allowed, run this tool again passing it as an extra host
                        and use whichever one shows OK.""";
        }
        if (all.contains("password authentication failed")) {
            return "The server reached the login step but rejected the user name or password. Re-check both "
                    + "(hosting panels often prefix the user with the account name).";
        }
        if (all.contains("does not exist")) {
            return "The server is reachable but the database (or user) name is wrong. Hosting panels usually "
                    + "prefix both with the account name, e.g. accountname_dbname.";
        }
        if (all.contains("refused") || all.contains("timed out") || all.contains("Connection attempt failed")) {
            return "Could not reach PostgreSQL at all. Check that it is running, and the port (set DB_PORT if it is "
                    + "not 5432).";
        }
        return "Unrecognised failure - the messages above are exactly what the server or driver returned.";
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "";
        }
        int nl = message.indexOf('\n');
        return (nl < 0 ? message : message.substring(0, nl)).trim();
    }
}
