package com.harish.pickles.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String baseUrl = "http://localhost:8080";
    private Shipping shipping = new Shipping();
    private Security security = new Security();
    private Admin admin = new Admin();
    private SuperAdmin superAdmin = new SuperAdmin();
    private Storage storage = new Storage();
    private Mail mail = new Mail();
    private PasswordReset passwordReset = new PasswordReset();

    /** Outgoing email. The SMTP connection itself is configured under {@code spring.mail.*}. */
    public static class Mail {
        /** The From address on every email we send. */
        private String from = "Sri Vinayaka Bakery Home <hello@srivinayakabakeryhome.com>";
        /**
         * With no SMTP host configured nothing can be emailed. When true the reset link is written
         * to the log instead, so the flow can be walked locally. Keep this false in production:
         * anyone who can read the log could otherwise take over an account.
         */
        private boolean logLinksWhenUnconfigured = true;
        private Imap imap = new Imap();

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public boolean isLogLinksWhenUnconfigured() {
            return logLinksWhenUnconfigured;
        }

        public void setLogLinksWhenUnconfigured(boolean logLinksWhenUnconfigured) {
            this.logLinksWhenUnconfigured = logLinksWhenUnconfigured;
        }

        public Imap getImap() {
            return imap;
        }

        public void setImap(Imap imap) {
            this.imap = imap;
        }
    }

    /** Read access to the admin mailbox. The IMAP connection itself is under {@code spring.mail.imap.*}. */
    public static class Imap {
        private boolean enabled = true;
        /** Server-specific sent-mail folder name ("Sent", "Sent Items", "INBOX.Sent", ...). */
        private String sentFolder = "INBOX.Sent";
        private int connectTimeoutMs = 10000;
        /** Most recent messages fetched per folder listing. */
        private int fetchSize = 50;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSentFolder() {
            return sentFolder;
        }

        public void setSentFolder(String sentFolder) {
            this.sentFolder = sentFolder;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getFetchSize() {
            return fetchSize;
        }

        public void setFetchSize(int fetchSize) {
            this.fetchSize = fetchSize;
        }
    }

    /** "Forgot password" links. */
    public static class PasswordReset {
        /** How long an emailed link stays valid. */
        private long expiryMinutes = 60;
        /** Minimum gap between two links for the same account, so the form cannot be used to spam an inbox. */
        private long resendCooldownSeconds = 60;

        public long getExpiryMinutes() {
            return expiryMinutes;
        }

        public void setExpiryMinutes(long expiryMinutes) {
            this.expiryMinutes = expiryMinutes;
        }

        public long getResendCooldownSeconds() {
            return resendCooldownSeconds;
        }

        public void setResendCooldownSeconds(long resendCooldownSeconds) {
            this.resendCooldownSeconds = resendCooldownSeconds;
        }
    }

    public static class Shipping {
        /** Flat courier charge in paise applied to every order below the free threshold. */
        private long flatFeePaise = 6000;
        private long freeAbovePaise = 99900;

        public long getFlatFeePaise() {
            return flatFeePaise;
        }

        public void setFlatFeePaise(long flatFeePaise) {
            this.flatFeePaise = flatFeePaise;
        }

        public long getFreeAbovePaise() {
            return freeAbovePaise;
        }

        public void setFreeAbovePaise(long freeAbovePaise) {
            this.freeAbovePaise = freeAbovePaise;
        }
    }

    /** Where uploaded product photos are written. */
    public static class Storage {
        /** Directory on disk. Relative paths resolve against the working directory. */
        private String uploadDir = "./uploads/products";
        /** Largest single photo accepted, in bytes. Keep below spring.servlet.multipart.max-file-size. */
        private long maxFileSizeBytes = 5L * 1024 * 1024;
        /** Most photos one food may carry. */
        private int maxImagesPerProduct = 6;

        public String getUploadDir() {
            return uploadDir;
        }

        public void setUploadDir(String uploadDir) {
            this.uploadDir = uploadDir;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public int getMaxImagesPerProduct() {
            return maxImagesPerProduct;
        }

        public void setMaxImagesPerProduct(int maxImagesPerProduct) {
            this.maxImagesPerProduct = maxImagesPerProduct;
        }
    }

    public static class Security {
        private Jwt jwt = new Jwt();

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
        }

        public static class Jwt {
            private String secret = "local-dev-only-secret-change-me-please-32chars-min";
            private long expiryMinutes = 720;
            private String issuer = "sri-vinayaka-bakery-home";

            public String getSecret() {
                return secret;
            }

            public void setSecret(String secret) {
                this.secret = secret;
            }

            public long getExpiryMinutes() {
                return expiryMinutes;
            }

            public void setExpiryMinutes(long expiryMinutes) {
                this.expiryMinutes = expiryMinutes;
            }

            public String getIssuer() {
                return issuer;
            }

            public void setIssuer(String issuer) {
                this.issuer = issuer;
            }
        }
    }

    /** Seeded account details. Applied only when the users table is empty. */
    public static class Account {
        private String email;
        private String password;
        private String name;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Admin extends Account {
        public Admin() {
            setEmail("admin@srivinayakabakeryhome.com");
            setPassword("Admin@12345");
            setName("Store Admin");
        }
    }

    public static class SuperAdmin extends Account {
        public SuperAdmin() {
            setEmail("superadmin@srivinayakabakeryhome.com");
            setPassword("Super@12345");
            setName("Super Admin");
        }
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Shipping getShipping() {
        return shipping;
    }

    public void setShipping(Shipping shipping) {
        this.shipping = shipping;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public SuperAdmin getSuperAdmin() {
        return superAdmin;
    }

    public void setSuperAdmin(SuperAdmin superAdmin) {
        this.superAdmin = superAdmin;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public PasswordReset getPasswordReset() {
        return passwordReset;
    }

    public void setPasswordReset(PasswordReset passwordReset) {
        this.passwordReset = passwordReset;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }
}
