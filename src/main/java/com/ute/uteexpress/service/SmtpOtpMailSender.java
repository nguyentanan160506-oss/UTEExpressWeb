package com.ute.uteexpress.service;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class SmtpOtpMailSender implements OtpMailSender {
    private final ObjectProvider<JavaMailSender> senders;
    private final String host;
    private final String from;

    public SmtpOtpMailSender(ObjectProvider<JavaMailSender> senders,
            @Value("${spring.mail.host:}") String host,
            @Value("${uteexpress.mail.from:}") String from) {
        this.senders = senders;
        this.host = host;
        this.from = from;
    }

    @Override
    public void sendVerification(String email, String otp) {
        JavaMailSender sender = senders.getIfAvailable();
        if (sender == null || host.isBlank() || from.isBlank()) {
            throw new RegistrationException(RegistrationException.Reason.MAIL_NOT_CONFIGURED);
        }
        try {
            new InternetAddress(from, true).validate();
        } catch (AddressException exception) {
            throw new RegistrationException(RegistrationException.Reason.MAIL_NOT_CONFIGURED);
        }
        if (sender instanceof JavaMailSenderImpl configured
                && Boolean.parseBoolean(configured.getJavaMailProperties().getProperty("mail.smtp.auth", "true"))
                && (configured.getUsername() == null || configured.getUsername().isBlank()
                    || configured.getPassword() == null || configured.getPassword().isBlank())) {
            throw new RegistrationException(RegistrationException.Reason.MAIL_NOT_CONFIGURED);
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("UTEExpress — Mã xác minh email");
        message.setText("Mã xác minh email UTEExpress của bạn là: " + otp
            + "\nMã có hiệu lực trong 5 phút và chỉ dùng một lần."
            + "\nKhông chia sẻ mã này. Nếu bạn không đăng ký tài khoản, hãy bỏ qua email.");
        try {
            sender.send(message);
        } catch (MailException exception) {
            // Provider exceptions may contain sensitive details. Never log or expose them.
            throw new RegistrationException(RegistrationException.Reason.MAIL_DELIVERY_FAILED);
        }
    }
}
