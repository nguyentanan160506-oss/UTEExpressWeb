package com.ute.uteexpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class VerificationForm extends EmailForm {
    @NotBlank(message = "Vui lòng nhập mã xác minh.")
    @Pattern(regexp = "[0-9]{6}", message = "Mã xác minh phải gồm đúng 6 chữ số.")
    private String otp;

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
