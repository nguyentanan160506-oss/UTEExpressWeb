package com.ute.uteexpress.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmailForm {
    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không hợp lệ.")
    @Size(max = 150, message = "Email không được dài quá 150 ký tự.")
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.strip(); }
}
