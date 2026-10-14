package com.ute.uteexpress.service;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpOtpMailSenderTests {

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test void missingHostSenderOrFromReportsConfigurationFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);
        for (SmtpOtpMailSender mail : new SmtpOtpMailSender[] {
            new SmtpOtpMailSender(provider(sender), "", "sender@example.test"),
            new SmtpOtpMailSender(provider(sender), "smtp.example.test", ""),
            new SmtpOtpMailSender(provider(null), "smtp.example.test", "sender@example.test")
        }) {
            var error = assertThrows(RegistrationException.class,
                () -> mail.sendVerification("recipient@example.test", new OtpCodeGenerator().generate()));
            assertEquals(RegistrationException.Reason.MAIL_NOT_CONFIGURED, error.getReason());
        }
        verifyNoInteractions(sender);
    }

    @Test void missingAuthCredentialsOrInvalidFromNeverAttemptsDelivery() {
        JavaMailSenderImpl sender = spy(new JavaMailSenderImpl());
        var mail = new SmtpOtpMailSender(provider(sender), "smtp.example.test", "sender@example.test");
        var error = assertThrows(RegistrationException.class,
            () -> mail.sendVerification("recipient@example.test", new OtpCodeGenerator().generate()));
        assertEquals(RegistrationException.Reason.MAIL_NOT_CONFIGURED, error.getReason());
        var invalidFrom = new SmtpOtpMailSender(provider(sender), "smtp.example.test", "invalid");
        assertThrows(RegistrationException.class,
            () -> invalidFrom.sendVerification("recipient@example.test", new OtpCodeGenerator().generate()));
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test void springMailReceivesSixDigitCodeButNothingIsSentToNetwork() {
        JavaMailSender sender = mock(JavaMailSender.class);
        String code = new OtpCodeGenerator().generate();
        var mail = new SmtpOtpMailSender(provider(sender), "smtp.example.test", "sender@example.test");
        mail.sendVerification("recipient@example.test", code);
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertArrayEquals(new String[] {"recipient@example.test"}, message.getValue().getTo());
        assertEquals("sender@example.test", message.getValue().getFrom());
        assertTrue(message.getValue().getText().contains(code));
        assertTrue(message.getValue().getText().contains("5 phút"));
    }

    @Test void smtpExceptionDoesNotLeakProviderDetailsOrPretendSuccess() {
        JavaMailSender sender = mock(JavaMailSender.class);
        String privateProviderDetail = UUID.randomUUID().toString();
        doThrow(new MailSendException(privateProviderDetail)).when(sender).send(any(SimpleMailMessage.class));
        var mail = new SmtpOtpMailSender(provider(sender), "smtp.example.test", "sender@example.test");
        var error = assertThrows(RegistrationException.class,
            () -> mail.sendVerification("recipient@example.test", new OtpCodeGenerator().generate()));
        assertEquals(RegistrationException.Reason.MAIL_DELIVERY_FAILED, error.getReason());
        assertFalse(error.getMessage().contains(privateProviderDetail));
        assertNull(error.getCause());
    }
}
