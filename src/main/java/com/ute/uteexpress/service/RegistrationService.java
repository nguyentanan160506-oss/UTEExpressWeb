package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.RegistrationForm;
import com.ute.uteexpress.entity.EmailOtp;
import com.ute.uteexpress.entity.User;
import com.ute.uteexpress.entity.UserRole;
import com.ute.uteexpress.entity.id.UserRoleId;
import com.ute.uteexpress.enums.OtpType;
import com.ute.uteexpress.enums.UserStatus;
import com.ute.uteexpress.repository.EmailOtpRepository;
import com.ute.uteexpress.repository.RoleRepository;
import com.ute.uteexpress.repository.UserRepository;
import com.ute.uteexpress.repository.UserRoleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class RegistrationService {
    public enum VerificationResult { VERIFIED, INVALID, EXPIRED, USED, ATTEMPTS_EXCEEDED }
    public enum ResendResult { SENT, NOT_AVAILABLE, TOO_SOON, LIMIT_REACHED }
    private static final OtpType TYPE = OtpType.REGISTER_VERIFY;
    private final UserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository memberships;
    private final EmailOtpRepository otps;
    private final PasswordEncoder passwordEncoder;
    private final OtpCodeGenerator generator;
    private final OtpMailSender mail;
    private final Clock clock;

    public RegistrationService(UserRepository users, RoleRepository roles,
            UserRoleRepository memberships, EmailOtpRepository otps, PasswordEncoder passwordEncoder,
            OtpCodeGenerator generator, OtpMailSender mail, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.memberships = memberships;
        this.otps = otps;
        this.passwordEncoder = passwordEncoder;
        this.generator = generator;
        this.mail = mail;
        this.clock = clock;
    }

    @Transactional
    public void register(@Valid RegistrationForm form) {
        if (users.existsByEmail(form.getEmail()) || users.existsByUsername(form.getUsername())
                || users.existsByPhoneNumber(form.getPhoneNumber())) {
            throw new RegistrationException(RegistrationException.Reason.DUPLICATE_ACCOUNT);
        }
        var role = roles.findByName("ROLE_USER")
            .orElseThrow(() -> new RegistrationException(RegistrationException.Reason.ROLE_NOT_CONFIGURED));
        User user = new User();
        user.setUsername(form.getUsername());
        user.setFullName(form.getFullName());
        user.setEmail(form.getEmail());
        user.setPhoneNumber(form.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.setStatus(UserStatus.UNVERIFIED);
        user = users.saveAndFlush(user);
        UserRole membership = new UserRole();
        membership.setId(new UserRoleId(user.getId(), role.getId()));
        membership.setUser(user);
        membership.setRole(role);
        memberships.save(membership);
        issue(user, now(), null);
        // SMTP errors escape as unchecked exceptions and roll back the account, role and OTP.
    }

    @Transactional
    public VerificationResult verify(@NotBlank @Email @Size(max = 150) String email, String code) {
        User user = users.findByEmailForUpdate(email.strip()).orElse(null);
        if (user == null || user.getStatus() == UserStatus.LOCKED) { return VerificationResult.INVALID; }
        EmailOtp otp = latest(user);
        if (otp == null) { return VerificationResult.INVALID; }
        if (otp.isUsed() || user.getStatus() == UserStatus.ACTIVE) { return VerificationResult.USED; }
        if (user.getStatus() != UserStatus.UNVERIFIED) { return VerificationResult.INVALID; }
        if (otp.getFailedAttempts() >= otp.getMaxAttempts()) { return VerificationResult.ATTEMPTS_EXCEEDED; }
        if (!otp.getExpiresAt().isAfter(now())) { return VerificationResult.EXPIRED; }
        if (code == null || !code.matches("[0-9]{6}") || !passwordEncoder.matches(code, otp.getOtpHash())) {
            otp.setFailedAttempts(otp.getFailedAttempts() + 1);
            otps.save(otp);
            // Return normally so incorrect-attempt increments commit rather than roll back.
            return otp.getFailedAttempts() >= otp.getMaxAttempts()
                ? VerificationResult.ATTEMPTS_EXCEEDED : VerificationResult.INVALID;
        }
        otp.setUsed(true);
        user.setStatus(UserStatus.ACTIVE);
        otps.save(otp);
        users.save(user);
        return VerificationResult.VERIFIED;
    }

    @Transactional
    public ResendResult resend(@NotBlank @Email @Size(max = 150) String email) {
        // Verify/resend lock the same user first, serializing operations across application instances.
        User user = users.findByEmailForUpdate(email.strip()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.UNVERIFIED) { return ResendResult.NOT_AVAILABLE; }
        LocalDateTime now = now();
        EmailOtp previous = latest(user);
        if (previous != null && previous.getResendAvailableAt().isAfter(now)) { return ResendResult.TOO_SOON; }
        if (otps.countByEmailAndOtpTypeAndCreatedAtGreaterThanEqual(user.getEmail(), TYPE, now.minusHours(1)) >= 3
                || otps.countByEmailAndOtpTypeAndCreatedAtGreaterThanEqual(user.getEmail(), TYPE, now.minusDays(1)) >= 10) {
            return ResendResult.LIMIT_REACHED;
        }
        issue(user, now, previous);
        if (previous != null && !previous.isUsed()) {
            previous.setUsed(true);
            otps.save(previous);
        }
        return ResendResult.SENT;
    }

    private EmailOtp latest(User user) {
        return otps.findFirstByEmailAndOtpTypeOrderByCreatedAtDescIdDesc(user.getEmail(), TYPE).orElse(null);
    }

    private void issue(User user, LocalDateTime now, EmailOtp previous) {
        String code;
        do {
            code = generator.generate();
        } while (previous != null && passwordEncoder.matches(code, previous.getOtpHash()));
        EmailOtp otp = new EmailOtp();
        otp.setEmail(user.getEmail());
        otp.setOtpType(TYPE);
        otp.setOtpHash(passwordEncoder.encode(code));
        otp.setExpiresAt(now.plusMinutes(5));
        otp.setFailedAttempts(0);
        otp.setMaxAttempts(5);
        otp.setUsed(false);
        otp.setResendAvailableAt(now.plusSeconds(60));
        otp.setCreatedAt(now);
        // Flush FK/uniqueness problems before SMTP; database changes remain uncommitted.
        otps.saveAndFlush(otp);
        mail.sendVerification(user.getEmail(), code);
    }

    private LocalDateTime now() {
        LocalDateTime value = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return value.withNano(value.getNano() / 100 * 100);
    }
}
