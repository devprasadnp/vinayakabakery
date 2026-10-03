package com.harish.pickles.service;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MailInboxServiceTest {

    private final Session session = Session.getDefaultInstance(new Properties());

    @Test
    void extractsPlainTextBodyDirectly() throws Exception {
        MimeMessage message = new MimeMessage(session);
        message.setText("Hello from a plain-text message.");

        assertThat(MailInboxService.extractBody(message)).isEqualTo("Hello from a plain-text message.");
    }

    @Test
    void prefersThePlainTextPartOfAMultipartMessage() throws Exception {
        MimeMessage message = new MimeMessage(session);
        MimeMultipart multipart = new MimeMultipart("alternative");

        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<p>Hello <b>world</b></p>", "text/html");
        multipart.addBodyPart(html);

        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("Hello world");
        multipart.addBodyPart(plain);

        message.setContent(multipart);
        message.saveChanges();

        assertThat(MailInboxService.extractBody(message)).isEqualTo("Hello world");
    }

    @Test
    void fallsBackToStrippedHtmlWhenNoPlainTextPartExists() throws Exception {
        MimeMessage message = new MimeMessage(session);
        MimeMultipart multipart = new MimeMultipart("alternative");

        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<p>Hello <b>world</b></p>", "text/html");
        multipart.addBodyPart(html);

        message.setContent(multipart);
        message.saveChanges();

        assertThat(MailInboxService.extractBody(message)).isEqualTo("Hello world");
    }
}
