package com.gongguhan.domain.escrow.service;

import com.gongguhan.domain.escrow.dto.EscrowResponse;
import com.gongguhan.domain.escrow.dto.EscrowSummaryResponse;
import com.gongguhan.domain.escrow.dto.GroupBuyEscrowResponse;
import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 에스크로 조회 (ESC-01, ESC-02).
 *
 * <p>상태를 바꾸지 않습니다. 전이는 {@link EscrowService} 가 담당하고, 이 서비스는
 * 읽기만 합니다. 그래서 클래스 전체가 {@code readOnly} 입니다.
 *
 * <p><b>소유권 검증에서 404 를 돌려주는 이유.</b> 403 을 주면 "그 자원은 존재한다"는
 * 사실이 새어 나갑니다. 남의 참여 건 ID 를 훑어 어디까지 존재하는지 알아낼 수 있습니다.
 * 팀원 코드({@code GroupBuyQueryService.getSellerParticipants})도 같은 방식이라
 * 패턴을 맞췄습니다 (NFR-SC-05).
 */
@Service
@Transactional(readOnly = true)
public class EscrowQueryService {

    /** 명세 1.5: 목록 기본값 page=0, size=20, 최대 size=100. */
    private static final int MAX_PAGE_SIZE = 100;

    private final EscrowRepository escrowRepository;
    private final ParticipationRepository participationRepository;
    private final GroupBuyRepository groupBuyRepository;

    public EscrowQueryService(EscrowRepository escrowRepository,
                               ParticipationRepository participationRepository,
                               GroupBuyRepository groupBuyRepository) {
        this.escrowRepository = escrowRepository;
        this.participationRepository = participationRepository;
        this.groupBuyRepository = groupBuyRepository;
    }

    /**
     * ESC-01 참여자 본인의 에스크로 상태 (FR-ESC-04, FR-ESC-07).
     *
     * <p>참여 건이 본인 것인지 먼저 확인합니다. 에스크로에는 구매자 ID 가 없어
     * ({@code participation_id} 만 있음) 참여 건을 거쳐야 소유권을 알 수 있습니다.
     *
     * @throws BusinessException {@code RESOURCE_NOT_FOUND} — 참여 건이 없거나 남의 것이거나
     *         에스크로가 아직 없는 경우
     */
    public EscrowResponse getMyEscrow(Long userId, Long participationId) {
        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(this::notFound);

        if (!participation.getUserId().equals(userId)) {
            // 존재 여부를 노출하지 않기 위해 403 이 아니라 404 를 던진다.
            throw notFound();
        }

        Escrow escrow = escrowRepository.findByParticipationId(participationId)
                .orElseThrow(this::notFound);

        return EscrowResponse.from(escrow);
    }

    /**
     * ESC-02 판매자 공동구매별 보관 내역·합계 (FR-ESC-04, FR-ESC-07·08).
     *
     * <p>목록은 페이지로 끊고 합계는 전체를 집계합니다. FR-ESC-08 불변식 확인에
     * 쓰려면 합계가 전체여야 합니다.
     *
     * @throws BusinessException {@code RESOURCE_NOT_FOUND} — 공동구매가 없거나 남의 것
     * @throws BusinessException {@code VALIDATION_ERROR} — 페이지 파라미터가 범위를 벗어난 경우
     */
    public GroupBuyEscrowResult getGroupBuyEscrows(Long sellerId, Long groupBuyId,
                                                    int page, int size) {
        requireOwnedGroupBuy(sellerId, groupBuyId);

        Page<Escrow> escrows = escrowRepository.findByGroupBuyId(groupBuyId, pageRequest(page, size));
        EscrowSummaryResponse summary = EscrowSummaryResponse.from(
                escrowRepository.aggregateByGroupBuy(groupBuyId));

        GroupBuyEscrowResponse body = GroupBuyEscrowResponse.of(groupBuyId, summary,
                escrows.getContent().stream().map(EscrowResponse::from).toList());

        return new GroupBuyEscrowResult(body, PageResponse.from(escrows));
    }

    /**
     * ESC-02 결과. 응답 본문과 페이지 정보를 함께 돌려줍니다.
     *
     * <p>본문과 페이지 정보를 각각 조회하게 만들면 같은 쿼리를 두 번 돌고 소유권 검증도
     * 두 번 하게 됩니다. 한 번에 묶어서 넘깁니다.
     */
    public record GroupBuyEscrowResult(GroupBuyEscrowResponse body, PageResponse meta) {
    }

    // ------------------------------------------------------------------
    // 내부
    // ------------------------------------------------------------------

    /**
     * 공동구매가 이 판매자 것인지 확인합니다.
     *
     * <p>{@code group_buys} 는 다른 담당자 소유 패키지라 읽기만 합니다.
     */
    private void requireOwnedGroupBuy(Long sellerId, Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(this::groupBuyNotFound);

        if (!groupBuy.getSellerId().equals(sellerId)) {
            throw groupBuyNotFound();
        }
    }

    private Pageable pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "page는 0 이상, size는 1 이상 %d 이하여야 합니다.".formatted(MAX_PAGE_SIZE));
        }
        return PageRequest.of(page, size);
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                "에스크로 내역을 찾을 수 없습니다.");
    }

    private BusinessException groupBuyNotFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다.");
    }
}
