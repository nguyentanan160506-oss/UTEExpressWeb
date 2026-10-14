package com.ute.uteexpress.service;

public class RegistrationException extends RuntimeException {
    public enum Reason { DUPLICATE_ACCOUNT, ROLE_NOT_CONFIGURED, MAIL_NOT_CONFIGURED, MAIL_DELIVERY_FAILED }
    private final Reason reason;

    public RegistrationException(Reason reason) {
        super(switch (reason) {
            case DUPLICATE_ACCOUNT -> "Email, tên đăng nhập hoặc số điện thoại đã được sử dụng.";
            case ROLE_NOT_CONFIGURED -> "Chức năng đăng ký chưa sẵn sàng. Vui lòng liên hệ quản trị viên.";
            case MAIL_NOT_CONFIGURED -> "Chức năng gửi email chưa được cấu hình. Vui lòng liên hệ quản trị viên.";
            case MAIL_DELIVERY_FAILED -> "Không thể gửi email xác minh. Vui lòng thử lại sau.";
        });
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
