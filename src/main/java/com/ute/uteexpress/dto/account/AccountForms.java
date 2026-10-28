package com.ute.uteexpress.dto.account;

import jakarta.validation.constraints.*;

public final class AccountForms {
    private AccountForms() { }
    public record Profile(@NotBlank @Size(max=100) String fullName,
            @NotBlank @Pattern(regexp="[0-9+() .-]{8,20}") String phoneNumber) { }
    public record Address(@NotBlank @Size(max=100) String contactName,
            @NotBlank @Pattern(regexp="[0-9+() .-]{8,20}") String contactPhone,
            @NotBlank @Size(max=100) String province, @NotBlank @Size(max=100) String district,
            @NotBlank @Size(max=100) String ward, @NotBlank @Size(max=255) String detailAddress,
            Boolean defaultAddress) { }
}
