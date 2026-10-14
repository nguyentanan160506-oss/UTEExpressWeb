package com.ute.uteexpress.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;

public class RegistrationForm extends EmailForm {
    @NotBlank(message = "Vui lòng nhập họ tên.")
    @Size(max = 100, message = "Họ tên không được dài quá 100 ký tự.")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập tên đăng nhập.")
    @Pattern(regexp = "[A-Za-z0-9._-]{3,50}",
        message = "Tên đăng nhập gồm 3–50 chữ cái, chữ số, dấu chấm, gạch dưới hoặc gạch ngang.")
    private String username;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Pattern(regexp = "\\+?[0-9]{9,15}", message = "Số điện thoại cần có 9–15 chữ số.")
    private String phoneNumber;

    @NotBlank(message = "Vui lòng nhập mật khẩu.")
    @Size(min = 8, max = 72, message = "Mật khẩu cần có ít nhất 8 ký tự và không quá 72 ký tự.")
    private String password;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu.")
    @Size(max = 72, message = "Mật khẩu xác nhận không được vượt quá 72 ký tự.")
    private String confirmPassword;

    @AssertTrue(message = "Mật khẩu xác nhận không khớp.")
    public boolean isPasswordConfirmed() { return password == null || password.equals(confirmPassword); }

    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8.")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName == null ? null : fullName.strip(); }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username == null ? null : username.strip(); }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber == null ? null : phoneNumber.strip(); }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public void clearPasswords() { password = null; confirmPassword = null; }
}
