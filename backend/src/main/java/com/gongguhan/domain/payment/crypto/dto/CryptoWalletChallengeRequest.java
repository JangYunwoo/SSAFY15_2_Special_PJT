package com.gongguhan.domain.payment.crypto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record CryptoWalletChallengeRequest(
        @NotNull @Positive Long quoteId,
        @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{40}$") String fromAddress,
        @NotBlank @Pattern(regexp = "^0x[0-9a-fA-F]{64}$") String txHash) {}
