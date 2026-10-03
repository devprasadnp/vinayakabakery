package com.harish.pickles.dto;

import java.time.Instant;

public class MailDtos {

    private MailDtos() {
    }

    /** The two folders of the one shared admin mailbox that the admin panel exposes. */
    public enum MailFolder {
        INBOX,
        SENT
    }

    public record MailMessageSummary(long uid, String from, String to, String subject,
                                      Instant sentDate, boolean read) {
    }

    public record MailMessageDetail(long uid, String from, String to, String subject,
                                     Instant sentDate, String bodyText) {
    }
}
