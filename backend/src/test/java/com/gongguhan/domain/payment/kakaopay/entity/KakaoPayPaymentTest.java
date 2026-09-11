package com.gongguhan.domain.payment.kakaopay.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class KakaoPayPaymentTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 9, 0);

    @Test
    void readyStateCanBeUsedOnlyUntilApproval() {
        KakaoPayPayment payment = readyPayment();
        payment.verifyReady("nonce");

        payment.markApproved("A123", 4_909, "MONEY", NOW.plusSeconds(10));

        BusinessException error = assertThrows(BusinessException.class,
                () -> payment.verifyReady("nonce"));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void approvalMustMatchReadyIdentifiersAndAmount() {
        KakaoPayPayment payment = readyPayment();

        BusinessException error = assertThrows(BusinessException.class,
                () -> payment.verifyApproval("T123", "PAYMENT-9001", "100", 53_999));

        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_PAYMENT_ERROR);
    }

    private KakaoPayPayment readyPayment() {
        KakaoPayPayment payment = KakaoPayPayment.forPayment(9001L);
        payment.recordReady(
                "T123", "PAYMENT-9001", "100", 54_000,
                "https://pay.example/bridge", "nonce", NOW.plusMinutes(5));
        return payment;
    }
}
