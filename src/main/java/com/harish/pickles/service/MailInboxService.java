package com.harish.pickles.service;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.config.MailImapConnectionProperties;
import com.harish.pickles.dto.MailDtos.MailFolder;
import com.harish.pickles.dto.MailDtos.MailMessageDetail;
import com.harish.pickles.dto.MailDtos.MailMessageSummary;
import com.harish.pickles.exception.MailAccessException;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Reads the one shared admin mailbox (Inbox and Sent) over IMAP. Opens a fresh connection for
 * every call rather than keeping one open - this is an occasionally-used admin screen, not a
 * high-traffic path, and a short-lived connection never goes stale between visits.
 */
@Service
public class MailInboxService {

    private static final Logger log = LoggerFactory.getLogger(MailInboxService.class);

    private final AppProperties props;
    private final MailImapConnectionProperties imapConnection;
    private final MailService mailService;

    public MailInboxService(AppProperties props, MailImapConnectionProperties imapConnection,
                            MailService mailService) {
        this.props = props;
        this.imapConnection = imapConnection;
        this.mailService = mailService;
    }

    public boolean isConfigured() {
        return props.getMail().getImap().isEnabled()
                && imapConnection.getHost() != null && !imapConnection.getHost().isBlank()
                && imapConnection.getPassword() != null && !imapConnection.getPassword().isBlank();
    }

    public List<MailMessageSummary> list(MailFolder folder) {
        requireConfigured();
        AppProperties.Imap cfg = props.getMail().getImap();
        try (Store store = connect()) {
            try (Folder mailFolder = store.getFolder(folderName(folder))) {
                mailFolder.open(Folder.READ_ONLY);
                int count = mailFolder.getMessageCount();
                if (count == 0) {
                    return List.of();
                }
                int from = Math.max(1, count - cfg.getFetchSize() + 1);
                Message[] messages = mailFolder.getMessages(from, count);

                FetchProfile fetchProfile = new FetchProfile();
                fetchProfile.add(FetchProfile.Item.ENVELOPE);
                fetchProfile.add(FetchProfile.Item.FLAGS);
                mailFolder.fetch(messages, fetchProfile);

                UIDFolder uidFolder = (UIDFolder) mailFolder;
                List<MailMessageSummary> result = new ArrayList<>();
                for (Message message : messages) {
                    result.add(new MailMessageSummary(
                            uidFolder.getUID(message),
                            addressesToString(message.getFrom()),
                            addressesToString(message.getRecipients(Message.RecipientType.TO)),
                            message.getSubject(),
                            toInstant(message.getSentDate()),
                            message.isSet(Flags.Flag.SEEN)));
                }
                Collections.reverse(result);
                return result;
            }
        } catch (MessagingException ex) {
            throw new MailAccessException("Mailbox is temporarily unreachable. Try again shortly.");
        }
    }

    public MailMessageDetail fetch(MailFolder folder, long uid) {
        requireConfigured();
        try (Store store = connect()) {
            try (Folder mailFolder = store.getFolder(folderName(folder))) {
                // Read-write on the inbox so opening a message marks it as read.
                mailFolder.open(folder == MailFolder.INBOX ? Folder.READ_WRITE : Folder.READ_ONLY);
                UIDFolder uidFolder = (UIDFolder) mailFolder;
                Message message = uidFolder.getMessageByUID(uid);
                if (message == null) {
                    throw new MailAccessException("That message is no longer in the mailbox.");
                }
                String text;
                String html;
                try {
                    text = extractBody(message);
                    html = extractHtml(message);
                } catch (IOException ex) {
                    throw new MailAccessException("Could not read the message body.");
                }
                String[] id = message.getHeader("Message-ID");
                String[] refs = message.getHeader("References");
                return new MailMessageDetail(uid, addressesToString(message.getFrom()),
                        addressesToString(message.getRecipients(Message.RecipientType.TO)),
                        addressesToString(message.getRecipients(Message.RecipientType.CC)),
                        addressesToString(message.getReplyTo()),
                        message.getSubject(), toInstant(message.getSentDate()), text, html,
                        id == null || id.length == 0 ? null : id[0],
                        refs == null || refs.length == 0 ? null : refs[0]);
            }
        } catch (MessagingException ex) {
            throw new MailAccessException("Mailbox is temporarily unreachable. Try again shortly.");
        }
    }

    /** Sends from the admin mailbox, then files a copy in the Sent folder (SMTP does not do that itself). */
    public void sendMail(String to, String cc, String bcc, String subject, String body,
                         String inReplyTo, String references) {
        MimeMessage sent = mailService.sendNow(to, cc, bcc, subject, body, inReplyTo, references);
        if (!isConfigured()) {
            return;
        }
        try (Store store = connect(); Folder sentFolder = store.getFolder(folderName(MailFolder.SENT))) {
            if (!sentFolder.exists()) {
                sentFolder.create(Folder.HOLDS_MESSAGES);
            }
            sentFolder.appendMessages(new Message[] {sent});
        } catch (MessagingException | RuntimeException ex) {
            log.warn("Mail was sent but could not be copied to the Sent folder: {}", ex.getMessage());
        }
    }

    /**
     * Who a reply goes to. Reply: the Reply-To (else From). Reply all: also everyone else on To and Cc,
     * minus this mailbox and duplicates. Returns {to, cc}.
     */
    public String[] replyRecipients(MailMessageDetail m, boolean all) {
        String self = mailService.fromAddress().toLowerCase(java.util.Locale.ROOT);
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        seen.add(self);
        List<String> to = new ArrayList<>();
        List<String> cc = new ArrayList<>();
        String primary = m.replyTo() != null && !m.replyTo().isBlank() ? m.replyTo() : m.from();
        addAll(to, seen, primary);
        if (all) {
            addAll(cc, seen, m.to());
            addAll(cc, seen, m.cc());
        }
        return new String[] {String.join(", ", to), String.join(", ", cc)};
    }

    private static void addAll(List<String> out, java.util.Set<String> seen, String header) {
        if (header == null || header.isBlank()) {
            return;
        }
        try {
            for (InternetAddress a : InternetAddress.parse(header, false)) {
                if (seen.add(a.getAddress().toLowerCase(java.util.Locale.ROOT))) {
                    out.add(a.toString());
                }
            }
        } catch (MessagingException ignored) {
            // an unparseable header contributes no recipients
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new MailAccessException("Mail is not configured, so the inbox cannot be shown.");
        }
    }

    /**
     * {@code imaps} (implicit TLS, port 993) when {@link MailImapConnectionProperties#isSsl()} is
     * on - the connection is encrypted from the first byte. Otherwise plain {@code imap} upgraded
     * with STARTTLS (the older port-143 style).
     */
    private Store connect() throws MessagingException {
        AppProperties.Imap cfg = props.getMail().getImap();
        String protocol = imapConnection.isSsl() ? "imaps" : "imap";
        Properties sessionProps = new Properties();
        sessionProps.put("mail." + protocol + ".ssl.enable", String.valueOf(imapConnection.isSsl()));
        sessionProps.put("mail." + protocol + ".starttls.enable", "true");
        sessionProps.put("mail." + protocol + ".connectiontimeout", String.valueOf(cfg.getConnectTimeoutMs()));
        sessionProps.put("mail." + protocol + ".timeout", String.valueOf(cfg.getConnectTimeoutMs()));
        Session session = Session.getInstance(sessionProps);
        Store store = session.getStore(protocol);
        store.connect(imapConnection.getHost(), imapConnection.getPort(),
                imapConnection.getUsername(), imapConnection.getPassword());
        return store;
    }

    private String folderName(MailFolder folder) {
        return folder == MailFolder.INBOX ? "INBOX" : props.getMail().getImap().getSentFolder();
    }

    private String addressesToString(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        return Arrays.stream(addresses).map(Address::toString).collect(Collectors.joining(", "));
    }

    private Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    /** Prefers the plain-text part of a message; falls back to a best-effort HTML tag strip. */
    static String extractBody(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            Object content = part.getContent();
            return content == null ? "" : content.toString();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            String htmlFallback = null;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/plain")) {
                    return extractBody(bodyPart);
                }
                if (bodyPart.isMimeType("multipart/*")) {
                    String nested = extractBody(bodyPart);
                    if (nested != null && !nested.isBlank()) {
                        return nested;
                    }
                } else if (bodyPart.isMimeType("text/html") && htmlFallback == null) {
                    htmlFallback = stripHtml(bodyPart);
                }
            }
            return htmlFallback != null ? htmlFallback : "";
        }
        if (part.isMimeType("text/html")) {
            return stripHtml(part);
        }
        return "";
    }

    private static String stripHtml(Part htmlPart) throws MessagingException, IOException {
        Object content = htmlPart.getContent();
        String html = content == null ? "" : content.toString();
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    /** The message's HTML part (searching nested multiparts), or null when it has none. */
    static String extractHtml(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/html")) {
            Object content = part.getContent();
            return content == null ? null : content.toString();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                String found = extractHtml(multipart.getBodyPart(i));
                if (found != null && !found.isBlank()) {
                    return found;
                }
            }
        }
        return null;
    }
}
