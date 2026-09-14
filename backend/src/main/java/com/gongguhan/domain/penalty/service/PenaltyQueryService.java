package com.gongguhan.domain.penalty.service;

import com.gongguhan.domain.penalty.dto.PenaltyResponse;
import com.gongguhan.domain.penalty.dto.SellerPenaltySummaryResponse;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.repository.SellerPenaltyRepository;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위반 기록 조회 (PEN-01~03).
 *
 * <p>판매자 조회와 관리자 조회를 한 클래스에 두되 <b>응답 모양을 다르게</b> 합니다.
 * 판매자에게는 관리자 식별자를 내보내지 않습니다.
 */
@Service
public class PenaltyQueryService {

    /** 유효한 상태 필터. 오타를 빈 목록으로 돌려주면 "위반이 없다" 고 오해하게 됩니다. */
    private static final Set<String> FILTERABLE_STATUSES = Set.of(
            SellerPenalty.STATUS_CANDIDATE,
            SellerPenalty.STATUS_CONFIRMED,
            SellerPenalty.STATUS_DISMISSED,
            SellerPenalty.STATUS_REVOKED);

    private final SellerPenaltyRepository penaltyRepository;
    private final PenaltyService penaltyService;

    public PenaltyQueryService(SellerPenaltyRepository penaltyRepository,
                               PenaltyService penaltyService) {
        this.penaltyRepository = penaltyRepository;
        this.penaltyService = penaltyService;
    }

    /**
     * PEN-01 판매자 본인 위반 이력·누적 (FR-PEN-09~12).
     *
     * <p>누적 횟수와 임계값을 함께 내보냅니다. 판매자가 목록에서 확정 건만 골라 세도록
     * 두면 후보를 확정으로 오해합니다.
     */
    @Transactional(readOnly = true)
    public SellerPenaltyResult getMyPenalties(Long sellerId, Pageable pageable) {
        Page<SellerPenalty> page =
                penaltyRepository.findBySellerIdOrderByDetectedAtDesc(sellerId, pageable);

        int confirmed = penaltyService.confirmedCount(sellerId);
        int limit = penaltyService.penaltyLimit();

        SellerPenaltySummaryResponse body = new SellerPenaltySummaryResponse(
                confirmed, limit, confirmed >= limit,
                page.getContent().stream().map(PenaltyResponse::forSeller).toList());
        return new SellerPenaltyResult(body, PageResponse.from(page));
    }

    /** PEN-02 관리자 목록. {@code status} 로 검토 대기 건만 추리는 것이 주된 쓰임입니다. */
    @Transactional(readOnly = true)
    public AdminPenaltyResult getPenalties(String status, Pageable pageable) {
        String normalized = normalizeStatus(status);
        Page<SellerPenalty> page = normalized == null
                ? penaltyRepository.findAllByOrderByDetectedAtDesc(pageable)
                : penaltyRepository.findByStatusOrderByDetectedAtDesc(normalized, pageable);

        List<PenaltyResponse> body = page.getContent().stream()
                .map(PenaltyResponse::forAdmin)
                .toList();
        return new AdminPenaltyResult(body, PageResponse.from(page));
    }

    /**
     * PEN-03 위반 후보 근거 조회 (FR-PEN-06).
     *
     * <p>관리자가 확정·기각을 판단하는 화면입니다. 같은 판매자의 확정 이력을 함께
     * 보여줍니다 — 이번 건이 세 번째인지 아닌지에 따라 판단의 무게가 달라집니다.
     */
    @Transactional(readOnly = true)
    public PenaltyDetailResult getPenalty(Long penaltyId) {
        SellerPenalty penalty = penaltyRepository.findById(penaltyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "위반 기록을 찾을 수 없습니다. penaltyId=" + penaltyId));

        List<PenaltyResponse> history = penaltyRepository
                .findBySellerIdAndStatusOrderByDetectedAtDesc(
                        penalty.getSellerId(), SellerPenalty.STATUS_CONFIRMED)
                .stream()
                .map(PenaltyResponse::forAdmin)
                .toList();

        return new PenaltyDetailResult(
                PenaltyResponse.forAdmin(penalty),
                history,
                penaltyService.penaltyLimit());
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String upper = status.trim().toUpperCase();
        if (!FILTERABLE_STATUSES.contains(upper)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "지원하지 않는 위반 상태입니다. status=" + status);
        }
        return upper;
    }

    /** PEN-01 결과. 누적 요약과 페이지 메타. */
    public record SellerPenaltyResult(SellerPenaltySummaryResponse body, PageResponse meta) {
    }

    /** PEN-02 결과. */
    public record AdminPenaltyResult(List<PenaltyResponse> body, PageResponse meta) {
    }

    /**
     * PEN-03 결과.
     *
     * @param confirmedHistory 같은 판매자의 확정 이력. 이번 건이 몇 번째인지 판단하는 근거
     */
    public record PenaltyDetailResult(PenaltyResponse penalty,
                                      List<PenaltyResponse> confirmedHistory,
                                      int penaltyLimit) {
    }
}
