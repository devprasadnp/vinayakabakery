package com.harish.pickles.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IMAP connection details for reading the admin mailbox, under {@code spring.mail.imap.*} -
 * alongside {@code spring.mail.*} (SMTP), which Spring Boot binds itself. This one isn't
 * autoconfigured by Spring Boot, so {@link MailInboxService} reads it directly.
 */
@ConfigurationProperties(prefix = "spring.mail.imap")
public class MailImapConnectionProperties {

    private String host;
    private int port = 993;
    /** Implicit TLS (IMAPS), as opposed to plain IMAP upgraded with STARTTLS. */
    private boolean ssl = true;
    private String username;
    private String password;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isSsl() {
        return ssl;
    }

    public void setSsl(boolean ssl) {
        this.ssl = ssl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
