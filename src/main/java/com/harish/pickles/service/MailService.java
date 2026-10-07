package com.harish.pickles.service;

import com.harish.pickles.config.AppProperties;
import com.harish.pickles.exception.MailAccessException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.web.util.HtmlUtils;
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
     * Sends a branded HTML email (with a plain-text alternative) from the admin mailbox and returns the
     * sent message so the caller can file it in the Sent folder. Throws on failure so the admin is told.
     *
     * @param inReplyTo / references thread the message under the one it answers; both may be null
     */
    public MimeMessage sendNow(String to, String cc, String bcc, String subject, String bodyText,
                               String inReplyTo, String references) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new MailAccessException("Mail is not configured, so this message was not sent.");
        }
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(new jakarta.mail.internet.InternetAddress(fromAddress(), fromName(), "UTF-8"));
            helper.setTo(jakarta.mail.internet.InternetAddress.parse(to));
            if (cc != null && !cc.isBlank()) {
                helper.setCc(jakarta.mail.internet.InternetAddress.parse(cc));
            }
            if (bcc != null && !bcc.isBlank()) {
                helper.setBcc(jakarta.mail.internet.InternetAddress.parse(bcc));
            }
            helper.setSubject(subject);
            helper.setText(bodyText, brandedHtml(bodyText));
            if (inReplyTo != null && !inReplyTo.isBlank()) {
                mime.setHeader("In-Reply-To", inReplyTo);
                mime.setHeader("References", references == null || references.isBlank()
                        ? inReplyTo : references + " " + inReplyTo);
            }
            sender.send(mime);
            log.info("Mail sent to {}", to);
            return mime;
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new MailAccessException("That is not a valid email address or message: " + ex.getMessage());
        } catch (MailException ex) {
            throw new MailAccessException("Could not send the email: " + ex.getMessage());
        }
    }

    /** The mailbox address mail goes out from, e.g. admin@srivinayakabakeryhome.com. */
    public String fromAddress() {
        try {
            return jakarta.mail.internet.InternetAddress.parse(props.getMail().getFrom())[0].getAddress();
        } catch (MessagingException | RuntimeException ex) {
            return props.getMail().getFrom();
        }
    }

    private String fromName() {
        try {
            String n = jakarta.mail.internet.InternetAddress.parse(props.getMail().getFrom())[0].getPersonal();
            return n == null ? "Sri Vinayaka Bakery Home" : n;
        } catch (MessagingException | RuntimeException ex) {
            return "Sri Vinayaka Bakery Home";
        }
    }

    /** Wraps the admin's plain text in a clean, email-client-safe (table + inline style) template. */
    String brandedHtml(String bodyText) {
        String safe = HtmlUtils.htmlEscape(bodyText == null ? "" : bodyText)
                .lines().collect(java.util.stream.Collectors.joining("<br>"));
        return """
                <!DOCTYPE html><html><body style="margin:0;padding:0;background:#f4f1ec">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f1ec;padding:24px 12px">
                <tr><td align="center">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%%;background:#ffffff;border-radius:10px;overflow:hidden;font-family:Segoe UI,Arial,sans-serif">
                <tr><td style="background:#9a3412;color:#ffffff;padding:14px 28px;font-size:20px;font-weight:700"><img src="%s/images/logo.png" alt="" width="44" height="44" style="vertical-align:middle;margin-right:12px;border-radius:8px;background:#ffffff">Sri Vinayaka Bakery Home</td></tr>
                <tr><td style="padding:28px;color:#222222;font-size:15px;line-height:1.6">%s</td></tr>
                <tr><td style="padding:16px 28px;background:#faf7f2;color:#777777;font-size:12px;line-height:1.5;border-top:1px solid #eee">
                Sri Vinayaka Bakery Home &middot; admin@srivinayakabakeryhome.com &middot; srivinayakabakeryhome.com</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(props.getBaseUrl().replaceAll("/+$", ""), safe);
    }
}
