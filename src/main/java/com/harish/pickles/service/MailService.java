package com.harish.pickles.service;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.exception.MailAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends the emails the shop originates. Runs on a background thread so a slow mail server never
 * holds up a web request - which also means the "forgot password" form takes the same time
 * whether or not the address belongs to an account.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    // Spring Boot only creates a JavaMailSender when spring.mail.host is set.
    private final ObjectProvider<JavaMailSender> mailSender;
    private final AppProperties props;

    public MailService(ObjectProvider<JavaMailSender> mailSender, AppProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Async
    public void sendPasswordReset(String toEmail, String fullName, String resetLink, long validForMinutes) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            if (props.getMail().isLogLinksWhenUnconfigured()) {
                log.warn("Mail is not configured (spring.mail.host). Password reset link for {}: {}",
                        toEmail, resetLink);
            } else {
                log.error("Mail is not configured (spring.mail.host), so the password reset email for {} was NOT sent",
                        toEmail);
            }
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.getMail().getFrom());
        message.setTo(toEmail);
        message.setSubject("Reset your Sri Vinayaka Bakery Home password");
        message.setText("""
                Hi %s,

                We received a request to reset the password for your Sri Vinayaka Bakery Home account.
                Open the link below to choose a new one. It works once and expires in %d minutes.

                %s

                If you did not ask for this, you can ignore this email - your password stays as it is.

                Sri Vinayaka Bakery Home
                """.formatted(fullName, validForMinutes, resetLink));
        try {
            sender.send(message);
            log.info("Password reset email sent to {}", toEmail);
        } catch (MailException ex) {
            log.error("Could not send the password reset email to {}: {}", toEmail, ex.getMessage());
        }
    }

    /**
     * Sends immediately and throws on failure, unlike {@link #sendPasswordReset}: the admin
     * compose form needs to tell the admin right away whether the mail went out.
     */
    public void sendNow(String toEmail, String subject, String bodyText) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new MailAccessException("Mail is not configured, so this message was not sent.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.getMail().getFrom());
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(bodyText);
        try {
            sender.send(message);
            log.info("Mail sent to {}", toEmail);
        } catch (MailException ex) {
            throw new MailAccessException("Could not send the email: " + ex.getMessage());
        }
    }
}
