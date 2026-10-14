package com.ute.uteexpress.service;

public interface OtpMailSender {
    void sendVerification(String email, String otp);
}
