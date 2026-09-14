package com.gongguhan.domain.settlement.service;

import com.gongguhan.domain.settlement.dto.SettlementDetailResponse;
import com.gongguhan.domain.settlement.dto.SettlementResponse;
import com.gongguhan.domain.settlement.entity.Settlement;
import com.gongguhan.domain.settlement.entity.SettlementItem;
import com.gongguhan.domain.settlement.repository.SettlementItemRepository;
import com.gongguhan.domain.settlement.repository.SettlementRepository;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository;
import com.gongguhan.domain.settlement.repository.SettlementTargetRepository.ExcludedParticipation;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 조회 (STL-01~03).
 *
 * <p>판매자 조회는 <b>쿼리에서 소유권을 끝냅니다</b> — 남의 정산은 애초에 조회되지
 * 않습니다. 조회 후 비교하는 방식보다 빠뜨릴 여지가 적습니다.
 */
@Service
public class SettlementQueryService {

    /** 유효한 상태 필터. 오타를 빈 목록으로 돌려주면 "정산이 없다" 고 오해합니다. */
    private static final Set<String> FILTERABLE_STATUSES = Set.of(
            Settlement.STATUS_PENDING, Settlement.STATUS_COMPLETED, Settlement.STATUS_HOLD);

    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final SettlementTargetRepository targetRepository;

    public SettlementQueryService(SettlementRepository settlementRepository,
                                  SettlementItemRepository settlementItemRepository,
                                  SettlementTargetRepository targetRepository) {
        this.settlementRepository = settlementRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.targetRepository = targetRepository;
    }

    /** STL-01 판매자 정산 목록 (FR-STL-06~10, 14). */
    @Transactional(readOnly = true)
    public SettlementListResult getMySettlements(Long sellerId, Pageable pageable) {
        Page<Settlement> page =
                settlementRepository.findBySellerIdOrderByCreatedAtDesc(sellerId, pageable);
        return toResult(page, settlementRepository.sumCompletedAmount(sellerId));
    }

    /**
     * STL-02 정산 상세·포함/제외 참여 건 (FR-STL-07~08).
     *
     * <p>{@code sellerId} 가 {@code null} 이면 관리자 조회로 보고 소유권을 검사하지
     * 않습니다.
     */
    @Transactional(readOnly = true)
    public SettlementDetailResponse getSettlement(Long sellerId, Long settlementId) {
        Settlement settlement = sellerId == null
                ? settlementRepository.findById(settlementId).orElseThrow(this::notFound)
                : settlementRepository.findBySettlementIdAndSellerId(settlementId, sellerId)
                        .orElseThrow(this::notFound);

        List<SettlementDetailResponse.Item> items = new ArrayList<>();
        for (SettlementItem item : settlementItemRepository.findBySettlementId(settlementId)) {
            items.add(SettlementDetailResponse.Item.included(
                    item.getParticipationId(), item.getShareAmount(), item.getShippingFee()));
        }
        for (ExcludedParticipation excluded
                : targetRepository.findExcluded(settlement.getGroupBuyId())) {
            items.add(SettlementDetailResponse.Item.excluded(
                    excluded.participationId(), excluded.excludedReason()));
        }

        return new SettlementDetailResponse(SettlementResponse.from(settlement), items);
    }

    /** STL-03 관리자 전체 목록 (FR-STL-12~14). */
    @Transactional(readOnly = true)
    public SettlementListResult getAllSettlements(String status, Pageable pageable) {
        String normalized = normalizeStatus(status);
        Page<Settlement> page = normalized == null
                ? settlementRepository.findAllByOrderByCreatedAtDesc(pageable)
                : settlementRepository.findByStatusOrderByCreatedAtDesc(normalized, pageable);
        return toResult(page, null);
    }

    private SettlementListResult toResult(Page<Settlement> page, Long completedTotal) {
        List<SettlementResponse> body = page.getContent().stream()
                .map(SettlementResponse::from)
                .toList();
        return new SettlementListResult(body, completedTotal, PageResponse.from(page));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String upper = status.trim().toUpperCase();
        if (!FILTERABLE_STATUSES.contains(upper)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "지원하지 않는 정산 상태입니다. status=" + status);
        }
        return upper;
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "정산 내역을 찾을 수 없습니다.");
    }

    /**
     * 목록 결과.
     *
     * @param completedTotal 지금까지 실제로 지급된 총액 (FR-STL-14).
     *                       관리자 목록에서는 판매자가 특정되지 않아 {@code null}
     */
    public record SettlementListResult(List<SettlementResponse> body,
                                       Long completedTotal,
                                       PageResponse meta) {
    }
}
