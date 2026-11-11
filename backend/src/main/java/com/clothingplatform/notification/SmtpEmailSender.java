package com.clothingplatform.notification;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender mailer;
    public SmtpEmailSender(JavaMailSender mailer) { this.mailer = mailer; }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setTo(to); m.setSubject(subject); m.setText(body); m.setFrom("no-reply@atelier.local");
        mailer.send(m);
    }
}
