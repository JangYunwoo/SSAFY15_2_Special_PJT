package com.gongguhan.domain.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 50) String nickname,
        String phone,
        @Valid @NotNull AddressRequest defaultAddress
) {

    public record AddressRequest(
            @NotBlank String recipientName,
            @NotBlank String recipientPhone,
            @NotBlank @Size(min = 5, max = 5) String zipcode,
            @NotBlank String address1,
            String address2
    ) {
    }
}
