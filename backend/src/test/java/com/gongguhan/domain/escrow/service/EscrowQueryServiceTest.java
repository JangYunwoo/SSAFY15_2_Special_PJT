package com.gongguhan.domain.escrow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.escrow.dto.EscrowResponse;
import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.entity.EscrowStatus;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 에스크로 조회 검증.
 *
 * <p>확인하는 것은 두 가지다.
 * <ul>
 *   <li><b>남의 자금 내역이 새어 나가지 않는지</b> (NFR-SC-05)</li>
 *   <li><b>합계가 페이지가 아니라 전체 기준인지</b> (FR-ESC-08)</li>
 * </ul>
 */
class EscrowQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long SELLER_ID = 10L;
    private static final Long OTHER_SELLER_ID = 11L;
    private static final Long PARTICIPATION_ID = 500L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final long AMOUNT = 54_000L;
    private static final LocalDateTime HELD_AT = LocalDateTime.parse("2026-01-01T00:00:00");

    private EscrowRepository escrowRepository;
    private ParticipationRepository participationRepository;
    private GroupBuyRepository groupBuyRepository;
    private EscrowQueryService service;

    @BeforeEach
    void setUp() {
        escrowRepository = mock(EscrowRepository.class);
        participationRepository = mock(ParticipationRepository.class);
        groupBuyRepository = mock(GroupBuyRepository.class);
        service = new EscrowQueryService(
                escrowRepository, participationRepository, groupBuyRepository);
    }

    // ------------------------------------------------------------------
    // 픽스처
    // ------------------------------------------------------------------

    private Escrow escrow(Long participationId, long amount) {
        return Escrow.hold(participationId, GROUP_BUY_ID, amount, HELD_AT);
    }

    private Participation participationOf(Long userId) {
        Participation participation = mock(Participation.class);
        when(participation.getUserId()).thenReturn(userId);
        return participation;
    }

    private GroupBuy groupBuyOf(Long sellerId) {
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getSellerId()).thenReturn(sellerId);
        return groupBuy;
    }

    private EscrowRepository.StatusAggregate aggregate(EscrowStatus status, long count, long amount) {
        EscrowRepository.StatusAggregate row = mock(EscrowRepository.StatusAggregate.class);
        when(row.getStatus()).thenReturn(status);
        when(row.getCount()).thenReturn(count);
        when(row.getAmount()).thenReturn(amount);
        return row;
    }

    // ------------------------------------------------------------------
    // ESC-01 참여자 조회
    // ------------------------------------------------------------------

    @Test
    @DisplayName("본인 참여 건의 에스크로를 조회한다")
    void returnsOwnEscrow() {
        Participation participation = participationOf(USER_ID);
        Escrow escrow = escrow(PARTICIPATION_ID, AMOUNT);
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(participation));
        when(escrowRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(escrow));

        EscrowResponse response = service.getMyEscrow(USER_ID, PARTICIPATION_ID);

        assertEquals("HELD", response.status());
        assertEquals(AMOUNT, response.amount());
        assertEquals("KRW", response.currency());
        assertEquals(String.valueOf(PARTICIPATION_ID), response.participationId());
        // 아직 해제·환불되지 않았으므로 두 시각은 비어 있어야 한다.
        assertNull(response.releasedAt());
        assertNull(response.refundedAt());
    }

    @Test
    @DisplayName("남의 참여 건은 403 이 아니라 404 로 막는다 - 존재 여부를 노출하지 않는다")
    void hidesOtherUsersEscrow() {
        Participation participation = participationOf(OTHER_USER_ID);
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(participation));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getMyEscrow(USER_ID, PARTICIPATION_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
        // 소유권 검사에서 걸렸으므로 에스크로 조회 자체를 하지 않아야 한다.
        verify(escrowRepository, never()).findByParticipationId(any());
    }

    @Test
    @DisplayName("참여 건이 없으면 404")
    void notFoundWhenParticipationMissing() {
        when(participationRepository.findById(PARTICIPATION_ID)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getMyEscrow(USER_ID, PARTICIPATION_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("참여 건은 있는데 에스크로가 아직 없으면 404")
    void notFoundWhenEscrowMissing() {
        Participation participation = participationOf(USER_ID);
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(participation));
        when(escrowRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getMyEscrow(USER_ID, PARTICIPATION_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("해제된 에스크로는 released_at 이 채워져 응답된다")
    void exposesReleasedTimestamp() {
        Participation participation = participationOf(USER_ID);
        Escrow escrow = escrow(PARTICIPATION_ID, AMOUNT);
        escrow.release(true, HELD_AT.plusDays(7));
        when(participationRepository.findById(PARTICIPATION_ID))
                .thenReturn(Optional.of(participation));
        when(escrowRepository.findByParticipationId(PARTICIPATION_ID))
                .thenReturn(Optional.of(escrow));

        EscrowResponse response = service.getMyEscrow(USER_ID, PARTICIPATION_ID);

        assertEquals("RELEASED", response.status());
        assertEquals("2026-01-08T00:00Z", response.releasedAt());
        assertNull(response.refundedAt());
    }

    // ------------------------------------------------------------------
    // ESC-02 판매자 조회
    // ------------------------------------------------------------------

    @Test
    @DisplayName("남의 공동구매는 404 로 막는다")
    void hidesOtherSellersGroupBuy() {
        GroupBuy groupBuy = groupBuyOf(OTHER_SELLER_ID);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getGroupBuyEscrows(SELLER_ID, GROUP_BUY_ID, 0, 20));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
        // 소유권 검사가 먼저라 에스크로 조회를 하지 않아야 한다.
        verify(escrowRepository, never()).findByGroupBuyId(any(), any());
        verify(escrowRepository, never()).aggregateByGroupBuy(any());
    }

    @Test
    @DisplayName("공동구매가 없으면 404")
    void notFoundWhenGroupBuyMissing() {
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.empty());

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getGroupBuyEscrows(SELLER_ID, GROUP_BUY_ID, 0, 20));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("합계는 페이지가 아니라 전체 기준이다 - FR-ESC-08 불변식 확인용")
    void summaryCoversAllRowsNotJustThePage() {
        GroupBuy groupBuy = groupBuyOf(SELLER_ID);
        // 목록은 1건만 담긴 페이지지만 전체는 20건이다.
        Page<Escrow> onePage = new PageImpl<>(
                List.of(escrow(PARTICIPATION_ID, AMOUNT)), PageRequest.of(0, 1), 20);
        List<EscrowRepository.StatusAggregate> aggregates = List.of(
                aggregate(EscrowStatus.HELD, 18, 972_000L),
                aggregate(EscrowStatus.RELEASED, 1, 54_000L),
                aggregate(EscrowStatus.REFUNDED, 1, 54_000L));

        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
        when(escrowRepository.findByGroupBuyId(eq(GROUP_BUY_ID), any(Pageable.class)))
                .thenReturn(onePage);
        when(escrowRepository.aggregateByGroupBuy(GROUP_BUY_ID)).thenReturn(aggregates);

        EscrowQueryService.GroupBuyEscrowResult result =
                service.getGroupBuyEscrows(SELLER_ID, GROUP_BUY_ID, 0, 1);

        // 목록은 1건
        assertEquals(1, result.body().escrows().size());
        // 합계는 20건 전체
        assertEquals(18, result.body().summary().heldCount());
        assertEquals(972_000L, result.body().summary().heldAmount());
        assertEquals(20, result.body().summary().totalCount());
        assertEquals(1_080_000L, result.body().summary().totalAmount());
        // 페이지 정보도 전체 건수를 알려준다
        assertEquals(20, result.meta().totalElements());
    }

    @Test
    @DisplayName("특정 상태가 하나도 없으면 그 항목은 0 이다 - 필드가 빠지면 프론트가 undefined 를 만난다")
    void fillsMissingStatusesWithZero() {
        GroupBuy groupBuy = groupBuyOf(SELLER_ID);
        Page<Escrow> page = new PageImpl<>(
                List.of(escrow(PARTICIPATION_ID, AMOUNT)), PageRequest.of(0, 20), 1);
        // HELD 만 있고 RELEASED·REFUNDED 행은 쿼리 결과에 아예 없다.
        // 스텁 객체를 when(...) 밖에서 먼저 완성해야 한다 - 인자 안에서 다른 mock 을
        // 스터빙하면 UnfinishedStubbingException 이 난다.
        List<EscrowRepository.StatusAggregate> heldOnly =
                List.of(aggregate(EscrowStatus.HELD, 1, AMOUNT));

        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
        when(escrowRepository.findByGroupBuyId(eq(GROUP_BUY_ID), any(Pageable.class)))
                .thenReturn(page);
        when(escrowRepository.aggregateByGroupBuy(GROUP_BUY_ID)).thenReturn(heldOnly);

        var summary = service.getGroupBuyEscrows(SELLER_ID, GROUP_BUY_ID, 0, 20).body().summary();

        assertEquals(1, summary.heldCount());
        assertEquals(0, summary.releasedCount());
        assertEquals(0, summary.releasedAmount());
        assertEquals(0, summary.refundedCount());
        assertEquals(0, summary.refundedAmount());
        assertEquals(1, summary.totalCount());
        assertEquals(AMOUNT, summary.totalAmount());
    }

    @Test
    @DisplayName("에스크로가 하나도 없어도 0 으로 채운 합계를 돌려준다")
    void returnsZeroSummaryWhenEmpty() {
        GroupBuy groupBuy = groupBuyOf(SELLER_ID);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
        when(escrowRepository.findByGroupBuyId(eq(GROUP_BUY_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(escrowRepository.aggregateByGroupBuy(GROUP_BUY_ID)).thenReturn(List.of());

        var result = service.getGroupBuyEscrows(SELLER_ID, GROUP_BUY_ID, 0, 20);

        assertEquals(0, result.body().escrows().size());
        assertEquals(0, result.body().summary().totalCount());
        assertEquals(0, result.body().summary().totalAmount());
        assertEquals("KRW", result.body().summary().currency());
    }

    // ------------------------------------------------------------------
    // 페이지 파라미터 검증 (명세 1.5)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("page 가 음수면 400")
    void rejectsNegativePage() {
        GroupBuy groupBuy = groupBuyOf(SELLER_ID);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getGroupBuyEscrows(SELLER_ID, GROUP_BUY_ID, -1, 20));

        assertEquals(ErrorCode.VALIDATION_ERROR, e.getErrorCode());
    }

    @Test
    @DisplayName("size 가 100 을 넘으면 400 - 명세 1.5 최대값")
    void rejectsOversizedPage() {
        GroupBuy groupBuy = groupBuyOf(SELLER_ID);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getGroupBuyEscrows(SELLER_ID, GROUP_BUY_ID, 0, 101));

        assertEquals(ErrorCode.VALIDATION_ERROR, e.getErrorCode());
    }

    @Test
    @DisplayName("size 가 0 이면 400")
    void rejectsZeroSize() {
        GroupBuy groupBuy = groupBuyOf(SELLER_ID);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.getGroupBuyEscrows(SELLER_ID, GROUP_BUY_ID, 0, 0));

        assertEquals(ErrorCode.VALIDATION_ERROR, e.getErrorCode());
    }
}
