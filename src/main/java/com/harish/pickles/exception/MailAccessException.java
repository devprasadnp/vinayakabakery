package com.harish.pickles.exception;

/** The admin mailbox (IMAP or SMTP) could not be reached or used. */
public class MailAccessException extends BusinessException {

    public MailAccessException(String message) {
        super(message);
    }
}
