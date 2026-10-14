package com.ute.uteexpress.dto.admin;

import com.ute.uteexpress.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;

/** Detached view data: deliberately excludes credentials and OTP data. */
public record AdminUserView(Long id, String username, String email, String fullName,
        String phoneNumber, UserStatus status, LocalDateTime createdAt,
        LocalDateTime updatedAt, List<AdminRoleView> roles, boolean self) {
    public AdminUserView {
        roles = List.copyOf(roles);
    }
}
