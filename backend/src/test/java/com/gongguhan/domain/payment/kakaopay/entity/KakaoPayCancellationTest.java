package com.gongguhan.domain.payment.kakaopay.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class KakaoPayCancellationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 11, 5, 0);

    @Test
    void sameIdempotencyKeyCannotBeReusedForDifferentAmount() {
        KakaoPayCancellation cancellation = KakaoPayCancellation.start(
                "RETURN-40", 50L, "T123", 44_000, NOW);

        assertThatThrownBy(() -> cancellation.verifySameRequest(50L, 43_000))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void successStoresProviderAmountsForCachedDuplicateResult() {
        KakaoPayCancellation cancellation = KakaoPayCancellation.start(
                "RETURN-40", 50L, "T123", 44_000, NOW);

        cancellation.markSucceeded("A-CANCEL", 44_000, 44_000, 10_000,
                NOW.plusSeconds(1));

        assertThat(cancellation.isSucceeded()).isTrue();
        assertThat(cancellation.getCancelAid()).isEqualTo("A-CANCEL");
        assertThat(cancellation.getApprovedCancelAmount()).isEqualTo(44_000);
        assertThat(cancellation.getCancelAvailableAmount()).isEqualTo(10_000);
    }

    @Test
    void timeoutBecomesUnknownToPreventBlindSecondCancellation() {
        KakaoPayCancellation cancellation = KakaoPayCancellation.start(
                "RETURN-40", 50L, "T123", 44_000, NOW);

        cancellation.markUnknown("timeout", NOW.plusSeconds(1));

        assertThat(cancellation.isInFlightOrUnknown()).isTrue();
        assertThat(cancellation.getStatus()).isEqualTo(KakaoPayCancellation.STATUS_UNKNOWN);
    }
}
