package com.gongguhan.domain.penalty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.account.entity.SellerProfile;
import com.gongguhan.domain.account.repository.SellerProfileRepository;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.event.SanctionLevel;
import com.gongguhan.domain.penalty.event.SellerSanctionedEvent;
import com.gongguhan.domain.penalty.repository.SellerPenaltyRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 제재 판정 검증 (PEN-04~06).
 *
 * <p><b>지켜야 하는 것은 "사람 판단 없이 정지되지 않는다" 와 "같은 위반으로 두 번
 * 정지되지 않는다" 입니다.</b> 제재는 판매자의 영업을 막는 처분이라, 놓쳐서 정지를
 * 못 시키는 것보다 <b>부당하게 정지시키는 쪽</b>이 더 큰 사고입니다.
 */
class PenaltyServiceTest {

    private static final Long ADMIN_ID = 9L;
    private static final Long SELLER_ID = 201L;
    private static final Long PENALTY_ID = 7001L;
    private static final int LIMIT = 3;
    private static final String MEMO = "송장 미등록 확인";

    private static final Instant NOW = Instant.parse("2026-09-14T05:00:00Z");
    private static final LocalDateTime NOW_LDT = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private SellerPenaltyRepository penaltyRepository;
    private SellerProfileRepository sellerProfileRepository;
    private PolicyService policyService;
    private ApplicationEventPublisher events;

    private PenaltyService service;

    @BeforeEach
    void setUp() {
        penaltyRepository = mock(SellerPenaltyRepository.class);
        sellerProfileRepository = mock(SellerProfileRepository.class);
        policyService = mock(PolicyService.class);
        events = mock(ApplicationEventPublisher.class);

        when(policyService.getInt(PolicyKey.PENALTY_LIMIT)).thenReturn(LIMIT);

        service = new PenaltyService(penaltyRepository, sellerProfileRepository,
                policyService, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------
    // 고정 객체
    // ------------------------------------------------------------------

    private SellerPenalty candidate() {
        return SellerPenalty.candidate(SELLER_ID, 100L,
                SellerPenalty.REASON_NO_INVOICE, "기한 초과", NOW_LDT);
    }

    /**
     * 협력 객체를 세웁니다.
     *
     * <p>스텁 객체를 {@code when(...)} 호출 <b>밖에서</b> 먼저 완성합니다. 인자 안에서
     * 다른 mock 을 스터빙하면 {@code UnfinishedStubbingException} 이 납니다.
     */
    private SellerPenalty given(SellerPenalty penalty, SellerProfile seller, long confirmedCount) {
        when(penaltyRepository.findById(PENALTY_ID)).thenReturn(Optional.of(penalty));
        when(sellerProfileRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
        when(penaltyRepository.countBySellerIdAndStatus(SELLER_ID, SellerPenalty.STATUS_CONFIRMED))
                .thenReturn(confirmedCount);
        return penalty;
    }

    private SellerSanctionedEvent capturedEvent() {
        ArgumentCaptor<SellerSanctionedEvent> captor =
                ArgumentCaptor.forClass(SellerSanctionedEvent.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------
    // 확정
    // ------------------------------------------------------------------

    @Test
    @DisplayName("첫 위반 확정은 경고 단계로 알린다")
    void firstConfirmationIsWarning() {
        SellerProfile seller = mock(SellerProfile.class);
        SellerPenalty penalty = given(candidate(), seller, 1L);

        service.confirm(ADMIN_ID, PENALTY_ID, MEMO);

        assertEquals(SellerPenalty.STATUS_CONFIRMED, penalty.getStatus());
        assertEquals(ADMIN_ID, penalty.getReviewedBy());
        assertEquals(MEMO, penalty.getMemo());
        assertEquals(SanctionLevel.WARNING, capturedEvent().level());
    }

    @Test
    @DisplayName("임계 도달 시 쓰리아웃으로 알리고 판매자를 정지시킨다")
    void reachingLimitSuspendsSeller() {
        SellerProfile seller = mock(SellerProfile.class);
        given(candidate(), seller, 3L);

        service.confirm(ADMIN_ID, PENALTY_ID, MEMO);

        verify(seller).recordPenalty(LIMIT);
        SellerSanctionedEvent event = capturedEvent();
        assertEquals(SanctionLevel.THREE_STRIKE, event.level());
        assertEquals(3, event.violationCount());
    }

    /**
     * 알림 문구가 "3회 누적" 인데 실제로는 2회면 판매자가 혼란스럽습니다. 비정규화된
     * {@code seller_profiles.penalty_count} 가 아니라 원장을 세어 알립니다.
     */
    @Test
    @DisplayName("알리는 누적 횟수는 원장에서 센 값이다")
    void reportsCountFromLedger() {
        SellerProfile seller = mock(SellerProfile.class);
        given(candidate(), seller, 2L);

        service.confirm(ADMIN_ID, PENALTY_ID, MEMO);

        assertEquals(2, capturedEvent().violationCount());
    }

    /**
     * 관리자가 목록에서 같은 건을 두 번 누르는 것이 가장 흔한 경로입니다. 막지 않으면
     * 누적이 두 번 올라가 판매자가 부당하게 정지됩니다.
     */
    @Test
    @DisplayName("이미 확정된 건을 또 확정할 수 없다 - 누적 이중 증가 차단")
    void rejectsDoubleConfirmation() {
        SellerProfile seller = mock(SellerProfile.class);
        SellerPenalty penalty = candidate();
        penalty.confirm(ADMIN_ID, MEMO, NOW_LDT);
        given(penalty, seller, 1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm(ADMIN_ID, PENALTY_ID, MEMO));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
        verify(seller, never()).recordPenalty(anyInt());
        verify(events, never()).publishEvent(any(SellerSanctionedEvent.class));
    }

    @Test
    @DisplayName("기각된 건은 확정할 수 없다")
    void rejectsConfirmationOfDismissed() {
        SellerProfile seller = mock(SellerProfile.class);
        SellerPenalty penalty = candidate();
        penalty.dismiss(ADMIN_ID, "오탐", NOW_LDT);
        given(penalty, seller, 0L);

        assertThrows(BusinessException.class, () -> service.confirm(ADMIN_ID, PENALTY_ID, MEMO));
    }

    // ------------------------------------------------------------------
    // 기각
    // ------------------------------------------------------------------

    /**
     * 후보는 배치와 이의 제기가 자동으로 쌓는 것이라, 기각은 "원래 아무 일도 없었다"
     * 입니다. 누적을 건드리거나 알림을 보낼 이유가 없습니다.
     */
    @Test
    @DisplayName("기각은 누적을 건드리지 않고 알림도 보내지 않는다")
    void dismissDoesNotTouchSellerOrNotify() {
        SellerPenalty penalty = candidate();
        when(penaltyRepository.findById(PENALTY_ID)).thenReturn(Optional.of(penalty));

        service.dismiss(ADMIN_ID, PENALTY_ID, "택배사 지연으로 확인");

        assertEquals(SellerPenalty.STATUS_DISMISSED, penalty.getStatus());
        verify(sellerProfileRepository, never()).findById(anyLong());
        verify(events, never()).publishEvent(any(SellerSanctionedEvent.class));
    }

    // ------------------------------------------------------------------
    // 취소 (FR-PEN-13)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("확정 취소는 누적을 되돌린다")
    void revokeRollsBackCount() {
        SellerProfile seller = mock(SellerProfile.class);
        SellerPenalty penalty = candidate();
        penalty.confirm(ADMIN_ID, MEMO, NOW_LDT);
        given(penalty, seller, 2L);

        service.revoke(ADMIN_ID, PENALTY_ID, "오확정 정정");

        assertEquals(SellerPenalty.STATUS_REVOKED, penalty.getStatus());
        verify(seller).revokePenalty(LIMIT);
    }

    @Test
    @DisplayName("확정되지 않은 건은 취소할 수 없다")
    void rejectsRevokeOfCandidate() {
        SellerProfile seller = mock(SellerProfile.class);
        given(candidate(), seller, 0L);

        assertThrows(BusinessException.class, () -> service.revoke(ADMIN_ID, PENALTY_ID, "정정"));
        verify(seller, never()).revokePenalty(anyInt());
    }

    @Test
    @DisplayName("취소는 알림을 보내지 않는다 - 제재가 생긴 게 아니라 사라진 것이다")
    void revokeDoesNotNotify() {
        SellerProfile seller = mock(SellerProfile.class);
        SellerPenalty penalty = candidate();
        penalty.confirm(ADMIN_ID, MEMO, NOW_LDT);
        given(penalty, seller, 2L);

        service.revoke(ADMIN_ID, PENALTY_ID, "오확정 정정");

        verify(events, never()).publishEvent(any(SellerSanctionedEvent.class));
    }

    // ------------------------------------------------------------------
    // 조회 실패
    // ------------------------------------------------------------------

    @Test
    @DisplayName("없는 위반 기록은 404")
    void missingPenaltyIsNotFound() {
        when(penaltyRepository.findById(PENALTY_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm(ADMIN_ID, PENALTY_ID, MEMO));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("판매자 프로필이 없으면 확정하지 않는다")
    void missingSellerIsNotFound() {
        when(penaltyRepository.findById(PENALTY_ID)).thenReturn(Optional.of(candidate()));
        when(sellerProfileRepository.findById(SELLER_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> service.confirm(ADMIN_ID, PENALTY_ID, MEMO));
        verify(events, never()).publishEvent(any(SellerSanctionedEvent.class));
    }
}
