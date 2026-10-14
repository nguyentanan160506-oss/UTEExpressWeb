package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.RegistrationForm;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.repository.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.ute.uteexpress.service.RegistrationService.VerificationResult.*;
import static com.ute.uteexpress.service.RegistrationService.ResendResult.*;

class RegistrationServiceTests {
    private static final String EMAIL = "otp-fixture@example.test";
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-15T01:00:00Z"), ZoneOffset.UTC);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository users;
    private RoleRepository roles;
    private UserRoleRepository memberships;
    private EmailOtpRepository otps;
    private OtpMailSender mail;
    private RegistrationService service;
    private User user;
    private EmailOtp otp;
    private String code;
    private final AtomicReference<String> sentCode = new AtomicReference<>();
    private final AtomicReference<EmailOtp> issued = new AtomicReference<>();

    @BeforeEach
    void setup() {
        users = mock(UserRepository.class);
        roles = mock(RoleRepository.class);
        memberships = mock(UserRoleRepository.class);
        otps = mock(EmailOtpRepository.class);
        mail = mock(OtpMailSender.class);
        service = new RegistrationService(users, roles, memberships, otps, encoder,
            new OtpCodeGenerator(), mail, clock);
        user = new User();
        user.setId(71L);
        user.setEmail(EMAIL);
        user.setStatus(UserStatus.UNVERIFIED);
        code = new OtpCodeGenerator().generate();
        otp = new EmailOtp();
        otp.setEmail(EMAIL);
        otp.setOtpType(OtpType.REGISTER_VERIFY);
        otp.setOtpHash(encoder.encode(code));
        otp.setExpiresAt(now().plusMinutes(5));
        otp.setResendAvailableAt(now().plusSeconds(60));
        otp.setCreatedAt(now());
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
        when(otps.findFirstByEmailAndOtpTypeOrderByCreatedAtDescIdDesc(EMAIL, OtpType.REGISTER_VERIFY))
            .thenReturn(Optional.of(otp));
        Role role = new Role();
        role.setId(2L);
        role.setName("ROLE_USER");
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(71L);
            return saved;
        });
        when(otps.saveAndFlush(any(EmailOtp.class))).thenAnswer(invocation -> {
            issued.set(invocation.getArgument(0));
            return issued.get();
        });
        doAnswer(invocation -> { sentCode.set(invocation.getArgument(1)); return null; })
            .when(mail).sendVerification(eq(EMAIL), anyString());
    }

    @Test
    void registrationStoresBcryptHashesAndOnlyExistingUserRole() {
        RegistrationForm form = form();
        service.register(form);
        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(savedUser.capture());
        assertEquals(UserStatus.UNVERIFIED, savedUser.getValue().getStatus());
        assertTrue(encoder.matches(form.getPassword(), savedUser.getValue().getPasswordHash()));
        assertNotEquals(form.getPassword(), savedUser.getValue().getPasswordHash());
        var membership = org.mockito.ArgumentCaptor.forClass(UserRole.class);
        verify(memberships).save(membership.capture());
        assertEquals("ROLE_USER", membership.getValue().getRole().getName());
        assertEquals(71L, membership.getValue().getId().getUserId());
        assertEquals(2L, membership.getValue().getId().getRoleId());
        verify(roles, never()).save(any());
        verifyOtpMetadata(issued.get());
        assertTrue(sentCode.get().matches("[0-9]{6}"));
        assertTrue(encoder.matches(sentCode.get(), issued.get().getOtpHash()));
        assertNotEquals(sentCode.get(), issued.get().getOtpHash());
    }

    @Test
    void duplicateEmailStopsBeforeSavingOrSending() {
        when(users.existsByEmail(EMAIL)).thenReturn(true);
        var error = assertThrows(RegistrationException.class, () -> service.register(form()));
        assertEquals(RegistrationException.Reason.DUPLICATE_ACCOUNT, error.getReason());
        verifyNoInteractions(roles, memberships, mail);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void duplicateUsernameAndPhoneAreRejected() {
        when(users.existsByUsername("fixture")).thenReturn(true);
        assertThrows(RegistrationException.class, () -> service.register(form()));
        when(users.existsByUsername("fixture")).thenReturn(false);
        when(users.existsByPhoneNumber("0901234567")).thenReturn(true);
        assertThrows(RegistrationException.class, () -> service.register(form()));
        verifyNoInteractions(mail);
    }

    @Test
    void missingUserRoleDoesNotCreateRolesOrAccounts() {
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.empty());
        var error = assertThrows(RegistrationException.class, () -> service.register(form()));
        assertEquals(RegistrationException.Reason.ROLE_NOT_CONFIGURED, error.getReason());
        verify(users, never()).saveAndFlush(any());
        verify(roles, never()).save(any());
        verifyNoInteractions(mail);
    }

    @Test
    void validOtpActivatesAccountAndIsSingleUse() {
        assertEquals(VERIFIED, service.verify(EMAIL, code));
        assertTrue(otp.isUsed());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(USED, service.verify(EMAIL, code));
        verify(users, times(1)).save(user);
    }

    @Test
    void incorrectOtpIncrementsCounterWithoutActivatingAccount() {
        assertEquals(INVALID, service.verify(EMAIL, wrongCode()));
        assertEquals(1, otp.getFailedAttempts());
        assertEquals(UserStatus.UNVERIFIED, user.getStatus());
        assertFalse(otp.isUsed());
        verify(otps).save(otp);
        verify(users, never()).save(any());
    }

    @Test
    void fifthFailureExhaustsOtpAndEvenCorrectCodeCannotBeUsed() {
        otp.setFailedAttempts(4);
        assertEquals(ATTEMPTS_EXCEEDED, service.verify(EMAIL, wrongCode()));
        assertEquals(5, otp.getFailedAttempts());
        assertEquals(ATTEMPTS_EXCEEDED, service.verify(EMAIL, code));
        assertEquals(UserStatus.UNVERIFIED, user.getStatus());
    }

    @Test
    void expirationBoundaryAndUsedOtpAreRejected() {
        otp.setExpiresAt(now());
        assertEquals(EXPIRED, service.verify(EMAIL, code));
        otp.setExpiresAt(now().minusSeconds(1));
        assertEquals(EXPIRED, service.verify(EMAIL, code));
        otp.setUsed(true);
        assertEquals(USED, service.verify(EMAIL, code));
        assertEquals(UserStatus.UNVERIFIED, user.getStatus());
    }

    @Test
    void lockedUnknownOrMissingOtpCannotActivateAccount() {
        user.setStatus(UserStatus.LOCKED);
        assertEquals(INVALID, service.verify(EMAIL, code));
        user.setStatus(UserStatus.UNVERIFIED);
        when(otps.findFirstByEmailAndOtpTypeOrderByCreatedAtDescIdDesc(EMAIL, OtpType.REGISTER_VERIFY))
            .thenReturn(Optional.empty());
        assertEquals(INVALID, service.verify(EMAIL, code));
        assertEquals(INVALID, service.verify("unknown@example.test", code));
        verify(users, never()).save(any());
    }

    @Test
    void resendRequiresCooldown() {
        assertEquals(TOO_SOON, service.resend(EMAIL));
        verifyNoInteractions(mail);
        otp.setResendAvailableAt(now());
        assertEquals(SENT, service.resend(EMAIL));
        assertTrue(otp.isUsed());
        verifyOtpMetadata(issued.get());
    }

    @Test
    void hourlyAndDailySendLimitsIncludeInitialCodes() {
        otp.setResendAvailableAt(now());
        when(otps.countByEmailAndOtpTypeAndCreatedAtGreaterThanEqual(
            EMAIL, OtpType.REGISTER_VERIFY, now().minusHours(1))).thenReturn(3L);
        assertEquals(LIMIT_REACHED, service.resend(EMAIL));
        when(otps.countByEmailAndOtpTypeAndCreatedAtGreaterThanEqual(
            EMAIL, OtpType.REGISTER_VERIFY, now().minusHours(1))).thenReturn(2L);
        when(otps.countByEmailAndOtpTypeAndCreatedAtGreaterThanEqual(
            EMAIL, OtpType.REGISTER_VERIFY, now().minusDays(1))).thenReturn(10L);
        assertEquals(LIMIT_REACHED, service.resend(EMAIL));
        verifyNoInteractions(mail);
    }

    @Test
    void activeOrLockedAccountsCannotRequestVerificationMessages() {
        user.setStatus(UserStatus.ACTIVE);
        assertEquals(NOT_AVAILABLE, service.resend(EMAIL));
        user.setStatus(UserStatus.LOCKED);
        assertEquals(NOT_AVAILABLE, service.resend(EMAIL));
        assertEquals(NOT_AVAILABLE, service.resend("unknown@example.test"));
        verifyNoInteractions(mail);
    }

    @Test
    void resendReplacesPreviousCodeAndRejectsOlderCode() {
        otp.setResendAvailableAt(now());
        assertEquals(SENT, service.resend(EMAIL));
        EmailOtp newest = issued.get();
        when(otps.findFirstByEmailAndOtpTypeOrderByCreatedAtDescIdDesc(EMAIL, OtpType.REGISTER_VERIFY))
            .thenReturn(Optional.of(newest));
        assertNotEquals(code, sentCode.get());
        assertEquals(INVALID, service.verify(EMAIL, code));
        assertEquals(VERIFIED, service.verify(EMAIL, sentCode.get()));
        assertTrue(newest.isUsed());
    }

    @Test
    void smtpFailureRollsBackRegistrationTransaction() {
        doThrow(new RegistrationException(RegistrationException.Reason.MAIL_DELIVERY_FAILED))
            .when(mail).sendVerification(eq(EMAIL), anyString());
        RecordingTransactions transactions = new RecordingTransactions();
        RegistrationService proxy = transactional(transactions);
        assertThrows(RegistrationException.class, () -> proxy.register(form()));
        assertEquals(1, transactions.rollbacks);
        assertEquals(0, transactions.commits);
    }

    @Test
    void smtpFailureDuringResendKeepsPreviousOtpUsableAndRollsBack() {
        otp.setResendAvailableAt(now());
        doThrow(new RegistrationException(RegistrationException.Reason.MAIL_DELIVERY_FAILED))
            .when(mail).sendVerification(eq(EMAIL), anyString());
        RecordingTransactions transactions = new RecordingTransactions();
        assertThrows(RegistrationException.class, () -> transactional(transactions).resend(EMAIL));
        assertFalse(otp.isUsed());
        assertEquals(1, transactions.rollbacks);
        assertEquals(VERIFIED, service.verify(EMAIL, code));
    }

    @Test
    void wrongAttemptCommitsAndActivationUsesOneTransaction() {
        RecordingTransactions transactions = new RecordingTransactions();
        RegistrationService proxy = transactional(transactions);
        assertEquals(INVALID, proxy.verify(EMAIL, wrongCode()));
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
        assertEquals(1, otp.getFailedAttempts());
        assertEquals(VERIFIED, proxy.verify(EMAIL, code));
        assertEquals(2, transactions.commits);
        assertEquals(0, transactions.rollbacks);
    }

    @Test
    void databaseFailureIsNotReportedAsSuccessfulVerification() {
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("test-only"))
            .when(users).save(user);
        RecordingTransactions transactions = new RecordingTransactions();
        assertThrows(org.springframework.dao.DataAccessException.class,
            () -> transactional(transactions).verify(EMAIL, code));
        assertEquals(1, transactions.rollbacks);
        assertEquals(0, transactions.commits);
    }

    private RegistrationService transactional(RecordingTransactions transactions) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        interceptor.afterPropertiesSet();
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (RegistrationService) factory.getProxy();
    }

    private void verifyOtpMetadata(EmailOtp issued) {
        assertEquals(OtpType.REGISTER_VERIFY, issued.getOtpType());
        assertEquals(now().plusMinutes(5), issued.getExpiresAt());
        assertEquals(now().plusSeconds(60), issued.getResendAvailableAt());
        assertEquals(5, issued.getMaxAttempts());
        assertEquals(0, issued.getFailedAttempts());
        assertFalse(issued.isUsed());
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }

    private String wrongCode() {
        return String.format(Locale.ROOT, "%06d", (Integer.parseInt(code) + 1) % 1_000_000);
    }

    static RegistrationForm form() {
        RegistrationForm form = new RegistrationForm();
        form.setEmail(EMAIL);
        form.setUsername("fixture");
        form.setFullName("Người dùng thử nghiệm");
        form.setPhoneNumber("0901234567");
        form.setPassword(UUID.randomUUID().toString());
        form.setConfirmPassword(form.getPassword());
        return form;
    }

    static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
