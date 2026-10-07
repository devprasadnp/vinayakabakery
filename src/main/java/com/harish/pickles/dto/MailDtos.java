package com.harish.pickles.dto;

import jakarta.mail.internet.InternetAddress;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MailDtos {

    private MailDtos() {
    }

    /** The two folders of the one shared admin mailbox that the admin panel exposes. */
    public enum MailFolder {
        INBOX,
        SENT
    }

    /** "Asha Rao &lt;asha@x.com&gt;, b@y.com" -> "Asha Rao, b@y.com", for list and header display. */
    public static String friendly(String header) {
        if (header == null || header.isBlank()) {
            return "";
        }
        try {
            return Arrays.stream(InternetAddress.parse(header, false))
                    .map(a -> a.getPersonal() != null && !a.getPersonal().isBlank() ? a.getPersonal() : a.getAddress())
                    .collect(Collectors.joining(", "));
        } catch (Exception ex) {
            return header;
        }
    }

    public record MailMessageSummary(long uid, String from, String to, String subject,
                                      Instant sentDate, boolean read) {
        public String fromName() {
            return friendly(from);
        }

        public String toName() {
            return friendly(to);
        }
    }

    /**
     * @param bodyHtml the message's HTML part, if it has one; shown only inside a script-free sandbox
     */
    public record MailMessageDetail(long uid, String from, String to, String cc, String replyTo,
                                     String subject, Instant sentDate, String bodyText, String bodyHtml,
                                     String messageId, String references) {
        public String fromName() {
            return friendly(from);
        }
    }
}
