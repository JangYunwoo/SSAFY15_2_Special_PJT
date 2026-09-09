package com.gongguhan.domain.escrow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.entity.EscrowStatus;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EscrowServiceTest {

    private static final Long PARTICIPATION_ID = 500L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final long AMOUNT = 54_000L;

    private static final Instant NOW = Instant.parse("2026-01-08T00:00:00Z");
    private static final LocalDateTime NOW_LDT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final LocalDateTime HELD_AT = LocalDateTime.parse("2026-01-01T00:00:00");

    private EscrowRepository escrowRepository;
    private ParticipationRepository participationRepository;
    private EscrowService service;

    @BeforeEach
    void setUp() {
        escrowRepository = mock(EscrowRepository.class);
        participationRepository = mock(ParticipationRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new EscrowService(escrowRepository, participationRepository, clock);
    }

    private Escrow heldEscrow() {
        return Escrow.hold(PARTICIPATION_ID, GROUP_BUY_ID, AMOUNT, HELD_AT);
    }

    private Participation participationWithStatus(String status) {
        Participation participation = mock(Participation.class);
        when(participation.getStatus()).thenReturn(status);
        return participation;
    }

    /** 스텁 객체를 먼저 완성한 뒤 when(...) 에 넣는다. 중첩 스터빙은 예외가 난다. */
    private Escrow givenEscrowAndParticipation(String participationStatus) {
        Escrow escrow = heldEscrow();
        Participation participation = participationWithStatus(participationStatus);

        when(escrowRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(escrow));
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(participation));
        return escrow;
    }

    // ------------------------------------------------------------------
    // 해제
    // ------------------------------------------------------------------

    @Test
    @DisplayName("참여 건이 CONFIRMED 면 해제한다")
    void releasesWhenParticipationConfirmed() {
        Escrow escrow = givenEscrowAndParticipation("CONFIRMED");

        Escrow result = service.release(PARTICIPATION_ID);

        assertEquals(EscrowStatus.RELEASED, result.getStatus());
        // Clock 에서 가져온 시각이 기록돼야 한다.
        assertEquals(NOW_LDT, result.getReleasedAt());
        assertEquals(escrow, result);
    }

    @Test
    @DisplayName("참여 건이 SETTLED 여도 확정으로 인정한다 - 정산까지 끝난 건은 확정을 거쳤다")
    void treatsSettledAsConfirmed() {
        givenEscrowAndParticipation("SETTLED");

        Escrow result = service.release(PARTICIPATION_ID);

        assertEquals(EscrowStatus.RELEASED, result.getStatus());
    }

    @Test
    @DisplayName("배송완료 상태에서는 해제하지 않는다 - 구매확정이 선행 조건 (FR-ESC-03)")
    void rejectsReleaseWhenOnlyDelivered() {
        Escrow escrow = givenEscrowAndParticipation("DELIVERED");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.release(PARTICIPATION_ID));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.getErrorCode());
        assertEquals(EscrowStatus.HELD, escrow.getStatus());
    }

    @Test
    @DisplayName("결제 직후(PAID) 상태에서는 해제하지 않는다")
    void rejectsReleaseWhenOnlyPaid() {
        givenEscrowAndParticipation("PAID");

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.release(PARTICIPATION_ID));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.getErrorCode());
    }

    @Test
    @DisplayName("에스크로가 없으면 404")
    void rejectsReleaseWhenEscrowMissing() {
        when(escrowRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.release(PARTICIPATION_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("참여 건이 없으면 404")
    void rejectsReleaseWhenParticipationMissing() {
        Escrow escrow = heldEscrow();
        when(escrowRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(escrow));
        when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.release(PARTICIPATION_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    // ------------------------------------------------------------------
    // 환불
    // ------------------------------------------------------------------

    @Test
    @DisplayName("환불은 참여 건 상태를 조회하지 않는다 - 확정 전 반품이 정상 경로")
    void refundDoesNotReadParticipation() {
        Escrow escrow = heldEscrow();
        when(escrowRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(escrow));
        // participationRepository 를 스터빙하지 않았다. 조회하면 NPE 로 드러난다.

        Escrow result = service.refund(PARTICIPATION_ID, "RECRUIT_FAILED");

        assertEquals(EscrowStatus.REFUNDED, result.getStatus());
        assertEquals(NOW_LDT, result.getRefundedAt());
    }

    @Test
    @DisplayName("이미 해제된 건은 환불할 수 없다")
    void rejectsRefundAfterRelease() {
        Escrow escrow = givenEscrowAndParticipation("CONFIRMED");
        service.release(PARTICIPATION_ID);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.refund(PARTICIPATION_ID, "RETURN"));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, e.getErrorCode());
        assertEquals(EscrowStatus.RELEASED, escrow.getStatus());
    }

    // ------------------------------------------------------------------
    // 집계 · 불변식
    // ------------------------------------------------------------------

    @Test
    @DisplayName("공동구매별 보관 총액을 집계한다")
    void sumsHeldAmountByGroupBuy() {
        when(escrowRepository.sumHeldAmountByGroupBuy(GROUP_BUY_ID)).thenReturn(1_080_000L);

        assertEquals(1_080_000L, service.getHeldAmountByGroupBuy(GROUP_BUY_ID));
    }

    @Test
    @DisplayName("보관 건이 없으면 0을 돌려준다 - NULL 이 아니어야 한다")
    void returnsZeroWhenNothingHeld() {
        when(escrowRepository.sumHeldAmountByGroupBuy(GROUP_BUY_ID)).thenReturn(0L);

        assertEquals(0L, service.getHeldAmountByGroupBuy(GROUP_BUY_ID));
    }

    @Test
    @DisplayName("불변식이 정상이면 빈 목록이다")
    void reportsNoViolationsWhenHealthy() {
        when(escrowRepository.findAmountMismatches()).thenReturn(List.of());
        when(escrowRepository.findOrphans()).thenReturn(List.of());

        assertTrue(service.findInvariantViolations().isEmpty());
    }

    @Test
    @DisplayName("금액 불일치와 고아 행을 함께 보고한다")
    void reportsBothViolationKinds() {
        Escrow mismatch = heldEscrow();
        Escrow orphan = Escrow.hold(999L, GROUP_BUY_ID, 1_000L, HELD_AT);
        when(escrowRepository.findAmountMismatches()).thenReturn(List.of(mismatch));
        when(escrowRepository.findOrphans()).thenReturn(List.of(orphan));

        List<Escrow> violations = service.findInvariantViolations();

        assertEquals(2, violations.size());
        assertTrue(violations.contains(mismatch));
        assertTrue(violations.contains(orphan));
    }
}
