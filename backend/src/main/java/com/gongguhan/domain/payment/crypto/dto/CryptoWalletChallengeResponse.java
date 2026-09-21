package com.gongguhan.domain.payment.crypto.dto;

public record CryptoWalletChallengeResponse(String challengeId, String message, String expiresAt, long chainId) {}
