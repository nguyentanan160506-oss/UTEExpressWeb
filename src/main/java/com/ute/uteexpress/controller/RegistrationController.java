package com.ute.uteexpress.controller;

import com.ute.uteexpress.dto.EmailForm;
import com.ute.uteexpress.dto.RegistrationForm;
import com.ute.uteexpress.dto.VerificationForm;
import com.ute.uteexpress.service.RegistrationException;
import com.ute.uteexpress.service.RegistrationService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RegistrationController {
    private static final String PENDING_EMAIL = "PENDING_VERIFICATION_EMAIL";
    private static final String DATABASE_ERROR = "Không thể xử lý yêu cầu lúc này. Vui lòng thử lại sau.";
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
            BindingResult errors, HttpSession session, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            form.clearPasswords();
            return "auth/register";
        }
        try {
            registration.register(form);
        } catch (RegistrationException exception) {
            errors.reject("registration.failed", exception.getMessage());
            form.clearPasswords();
            return "auth/register";
        } catch (DataAccessException exception) {
            errors.reject("registration.database", DATABASE_ERROR);
            form.clearPasswords();
            return "auth/register";
        }
        form.clearPasswords();
        session.setAttribute(PENDING_EMAIL, form.getEmail());
        flash.addFlashAttribute("notice", "Mã xác minh đã được gửi. Vui lòng kiểm tra email của bạn.");
        return "redirect:/verify-email";
    }

    @GetMapping("/verify-email")
    public String verificationPage(Model model, HttpSession session) {
        VerificationForm form = new VerificationForm();
        form.setEmail((String) session.getAttribute(PENDING_EMAIL));
        model.addAttribute("verificationForm", form);
        return "auth/verify-email";
    }

    @PostMapping("/verify-email")
    public String verify(@Valid @ModelAttribute("verificationForm") VerificationForm form,
            BindingResult errors, HttpSession session, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            form.setOtp(null);
            return "auth/verify-email";
        }
        try {
            var result = registration.verify(form.getEmail(), form.getOtp());
            if (result == RegistrationService.VerificationResult.VERIFIED) {
                session.removeAttribute(PENDING_EMAIL);
                flash.addFlashAttribute("emailVerified", true);
                return "redirect:/login";
            }
            errors.reject("verification.invalid",
                "Mã không hợp lệ, đã hết hạn hoặc không còn sử dụng được. Bạn có thể yêu cầu gửi lại mã.");
        } catch (DataAccessException exception) {
            errors.reject("verification.database", DATABASE_ERROR);
        } finally {
            form.setOtp(null);
        }
        return "auth/verify-email";
    }

    @PostMapping("/verify-email/resend")
    public String resend(@Valid @ModelAttribute("resendForm") EmailForm form,
            BindingResult errors, HttpSession session, Model model, RedirectAttributes flash) {
        VerificationForm verification = new VerificationForm();
        verification.setEmail(form.getEmail());
        model.addAttribute("verificationForm", verification);
        if (errors.hasErrors()) {
            model.addAttribute("error", "Vui lòng nhập email hợp lệ trước khi yêu cầu gửi lại mã.");
            return "auth/verify-email";
        }
        try {
            var result = registration.resend(form.getEmail());
            if (result == RegistrationService.ResendResult.SENT) {
                session.setAttribute(PENDING_EMAIL, form.getEmail());
                flash.addFlashAttribute("notice", "Mã mới đã được gửi. Mã trước đó không còn hiệu lực.");
                return "redirect:/verify-email";
            }
            model.addAttribute("error", switch (result) {
                case TOO_SOON -> "Vui lòng chờ ít nhất 60 giây giữa các lần gửi mã.";
                case LIMIT_REACHED -> "Bạn đã đạt giới hạn gửi mã. Vui lòng thử lại sau.";
                default -> "Không thể gửi mã cho yêu cầu này. Kiểm tra email hoặc đăng nhập nếu đã xác minh.";
            });
        } catch (RegistrationException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (DataAccessException exception) {
            model.addAttribute("error", DATABASE_ERROR);
        }
        return "auth/verify-email";
    }
}
