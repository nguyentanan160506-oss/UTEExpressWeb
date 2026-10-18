package com.ute.uteexpress.security;

import com.ute.uteexpress.dto.RegistrationForm;
import com.ute.uteexpress.repository.*;
import com.ute.uteexpress.service.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SecurityConfigTests.TestApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    "spring.flyway.enabled=false", "spring.sql.init.mode=never",
    "spring.thymeleaf.check-template-location=false", "debug=false", "logging.level.root=INFO"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RegistrationControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private UserRepository users;
    @MockitoBean private UserRoleRepository userRoles;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private EmailOtpRepository otps;
    @MockitoBean private OtpMailSender otpMailSender;
    @MockitoBean private RegistrationService registration;

    @Test void guestPagesRenderFormsAndCsrfWithoutPasswordOrOtpValues() throws Exception {
        mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(view().name("auth/register"))
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("name=\"_csrf\""));
                assertTrue(html.contains("name=\"phoneNumber\""));
                assertTrue(html.contains("name=\"username\""));
                assertFalse(html.contains("type=\"password\" value="));
            });
        mvc.perform(get("/verify-email")).andExpect(status().isOk()).andExpect(view().name("auth/verify-email"))
            .andExpect(result -> {
                String html = result.getResponse().getContentAsString();
                assertTrue(html.contains("name=\"_csrf\""));
                assertTrue(html.contains("formaction=\"/verify-email/resend\""));
            });
    }

    @ParameterizedTest @ValueSource(strings = {"ROLE_ADMIN", "ROLE_SHIPPER"})
    void validRegistrationIgnoresRoleTamperingAndRedirectsWithoutAuthenticating(String attemptedRole) throws Exception {
        String password = UUID.randomUUID().toString();
        var result = mvc.perform(validRegistration(password).with(csrf())
                .param("role", attemptedRole).param("status", "ACTIVE"))
            .andExpect(redirectedUrl("/verify-email")).andReturn();
        var form = org.mockito.ArgumentCaptor.forClass(RegistrationForm.class);
        verify(registration).register(form.capture());
        assertEquals("fixture@example.test", form.getValue().getEmail());
        assertNull(form.getValue().getPassword());
        assertTrue(java.util.Arrays.stream(RegistrationForm.class.getDeclaredFields())
            .noneMatch(field -> field.getName().toLowerCase(java.util.Locale.ROOT).contains("role")));
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertEquals("fixture@example.test", session.getAttribute("PENDING_VERIFICATION_EMAIL"));
        assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
    }

    @Test void invalidPasswordAndConfirmationDoNotReachServiceOrReappearInHtml() throws Exception {
        String password = UUID.randomUUID().toString();
        mvc.perform(validRegistration(password).with(csrf()).param("confirmPassword", "mismatch"))
            .andExpect(status().isOk()).andExpect(view().name("auth/register"))
            .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains(password)));
        mvc.perform(validRegistration("short").with(csrf()))
            .andExpect(status().isOk()).andExpect(view().name("auth/register"));
        verifyNoInteractions(registration);
    }

    @Test void duplicateEmailAndMailConfigurationFailuresAreDisplayedWithoutSuccess() throws Exception {
        for (var reason : new RegistrationException.Reason[] {
                RegistrationException.Reason.DUPLICATE_ACCOUNT,
                RegistrationException.Reason.MAIL_NOT_CONFIGURED,
                RegistrationException.Reason.MAIL_DELIVERY_FAILED}) {
            doThrow(new RegistrationException(reason)).when(registration).register(any());
            mvc.perform(validRegistration(UUID.randomUUID().toString()).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("auth/register"))
                .andExpect(result -> {
                    assertTrue(result.getResponse().getContentAsString()
                        .contains(new RegistrationException(reason).getMessage()));
                    assertNull(result.getRequest().getSession(false).getAttribute("PENDING_VERIFICATION_EMAIL"));
                });
        }
    }

    @Test void successfulVerificationRedirectsToLoginWithoutAutoLogin() throws Exception {
        String code = new OtpCodeGenerator().generate();
        when(registration.verify("fixture@example.test", code))
            .thenReturn(RegistrationService.VerificationResult.VERIFIED);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("PENDING_VERIFICATION_EMAIL", "fixture@example.test");
        var result = mvc.perform(post("/verify-email").session(session).with(csrf())
                .param("email", "fixture@example.test").param("otp", code))
            .andExpect(redirectedUrl("/login")).andReturn();
        assertNull(session.getAttribute("PENDING_VERIFICATION_EMAIL"));
        assertNull(session.getAttribute("SPRING_SECURITY_CONTEXT"));
        assertEquals(true, result.getFlashMap().get("emailVerified"));
    }

    @Test void incorrectExpiredUsedAndExhaustedOtpUseSafeErrorWithoutEchoingCode() throws Exception {
        String code = new OtpCodeGenerator().generate();
        for (var outcome : new RegistrationService.VerificationResult[] {
            RegistrationService.VerificationResult.INVALID, RegistrationService.VerificationResult.EXPIRED,
            RegistrationService.VerificationResult.USED, RegistrationService.VerificationResult.ATTEMPTS_EXCEEDED}) {
            when(registration.verify("fixture@example.test", code)).thenReturn(outcome);
            mvc.perform(post("/verify-email").with(csrf()).param("email", "fixture@example.test").param("otp", code))
                .andExpect(status().isOk()).andExpect(view().name("auth/verify-email"))
                .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains(code)));
        }
    }

    @Test void invalidOtpFormatDoesNotReachService() throws Exception {
        mvc.perform(post("/verify-email").with(csrf()).param("email", "fixture@example.test").param("otp", "invalid"))
            .andExpect(status().isOk()).andExpect(view().name("auth/verify-email"));
        verifyNoInteractions(registration);
    }

    @Test void resendSuccessCooldownAndLimitsAreHandled() throws Exception {
        for (var outcome : RegistrationService.ResendResult.values()) {
            when(registration.resend("fixture@example.test")).thenReturn(outcome);
            var result = mvc.perform(post("/verify-email/resend").with(csrf()).param("email", "fixture@example.test"));
            if (outcome == RegistrationService.ResendResult.SENT) {
                result.andExpect(redirectedUrl("/verify-email"));
            } else {
                result.andExpect(status().isOk()).andExpect(view().name("auth/verify-email"))
                    .andExpect(model().attributeExists("error"));
            }
        }
    }

    @Test void resendMailFailureCannotClaimCodeWasSent() throws Exception {
        doThrow(new RegistrationException(RegistrationException.Reason.MAIL_DELIVERY_FAILED))
            .when(registration).resend("fixture@example.test");
        mvc.perform(post("/verify-email/resend").with(csrf()).param("email", "fixture@example.test"))
            .andExpect(status().isOk()).andExpect(view().name("auth/verify-email"))
            .andExpect(model().attributeExists("error")).andExpect(model().attributeDoesNotExist("notice"));
    }

    @Test void databaseErrorsDoNotRenderProviderDetailsOrSuccess() throws Exception {
        String internal = UUID.randomUUID().toString();
        doThrow(new DataAccessResourceFailureException(internal)).when(registration).register(any());
        mvc.perform(validRegistration(UUID.randomUUID().toString()).with(csrf()))
            .andExpect(view().name("auth/register"))
            .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains(internal)));
        String code = new OtpCodeGenerator().generate();
        doThrow(new DataAccessResourceFailureException(internal))
            .when(registration).verify("fixture@example.test", code);
        mvc.perform(post("/verify-email").with(csrf()).param("email", "fixture@example.test").param("otp", code))
            .andExpect(view().name("auth/verify-email"))
            .andExpect(result -> assertFalse(result.getResponse().getContentAsString().contains(internal)));
    }

    @Test void allPublicPostsStillRequireCsrfAndNeverCallServiceWhenMissing() throws Exception {
        for (String path : new String[] {"/register", "/verify-email", "/verify-email/resend"}) {
            mvc.perform(post(path).param("email", "fixture@example.test"))
                .andExpect(status().isForbidden());
        }
        verifyNoInteractions(registration, otpMailSender);
    }

    @Test void verificationPageSupportsResumingWithSessionEmail() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("PENDING_VERIFICATION_EMAIL", "fixture@example.test");
        mvc.perform(get("/verify-email").session(session)).andExpect(status().isOk())
            .andExpect(result -> assertTrue(result.getResponse().getContentAsString()
                .contains("value=\"fixture@example.test\"")));
    }

    private MockHttpServletRequestBuilder validRegistration(String password) {
        return post("/register").param("fullName", "Người dùng thử nghiệm")
            .param("username", "fixture").param("email", "fixture@example.test")
            .param("phoneNumber", "0901234567").param("password", password)
            .param("confirmPassword", password);
    }
}
