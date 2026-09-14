package com.gongguhan.domain.refund.service;

import com.gongguhan.domain.refund.dto.RefundResponse;
import com.gongguhan.domain.refund.entity.RefundStatus;
import com.gongguhan.domain.refund.repository.RefundQueryRepository;
import com.gongguhan.domain.refund.repository.RefundQueryRepository.RefundRow;
import com.gongguhan.global.common.PageResponse;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 조회 (RFD-01~03).
 *
 * <p><b>남의 환불을 조회하면 403 이 아니라 404 를 돌려줍니다.</b> 403 은 "그 ID 는
 * 존재한다" 를 알려주는 셈이라, 식별자를 바꿔가며 자원을 열거할 수 있게 됩니다.
 * 에스크로 조회(ESC-01)와 같은 규칙입니다.
 */
@Service
public class RefundQueryService {

    private final RefundQueryRepository refundQueryRepository;

    public RefundQueryService(RefundQueryRepository refundQueryRepository) {
        this.refundQueryRepository = refundQueryRepository;
    }

    /** RFD-01 내 환불 목록 (FR-RFD-07~10). */
    @Transactional(readOnly = true)
    public RefundListResult getMyRefunds(Long userId, String status, Pageable pageable) {
        Page<RefundRow> page = refundQueryRepository.findMyRefunds(
                userId, normalizeStatus(status), pageable);
        return toResult(page);
    }

    /**
     * RFD-02 환불 상세.
     *
     * <p>소유자가 {@code null} 인 행(참여 건도 입금도 연결되지 않은 비정상 데이터)은
     * 아무에게도 보이지 않습니다. 조용히 남의 환불을 노출하는 것보다 낫습니다.
     */
    @Transactional(readOnly = true)
    public RefundResponse getMyRefund(Long userId, Long refundId) {
        RefundRow row = refundQueryRepository.findById(refundId)
                .filter(candidate -> userId.equals(candidate.ownerUserId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "환불 건을 찾을 수 없습니다. refundId=" + refundId));
        return RefundResponse.from(row);
    }

    /**
     * RFD-03 관리자 환불 목록 (FR-RFD-09, FR-ADM-08).
     *
     * <p>실패 건을 포함해 전부 봅니다. {@code status=FAILED} 로 재처리 대상을 추립니다.
     */
    @Transactional(readOnly = true)
    public RefundListResult getAllRefunds(String status, Pageable pageable) {
        Page<RefundRow> page = refundQueryRepository.findAll(normalizeStatus(status), pageable);
        return toResult(page);
    }

    /** 관리자 화면에서 한 건을 여는 경로. 소유권 검증 없이 그대로 돌려줍니다. */
    @Transactional(readOnly = true)
    public RefundResponse getRefund(Long refundId) {
        RefundRow row = refundQueryRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "환불 건을 찾을 수 없습니다. refundId=" + refundId));
        return RefundResponse.from(row);
    }

    private RefundListResult toResult(Page<RefundRow> page) {
        List<RefundResponse> body = page.getContent().stream()
                .map(RefundResponse::from)
                .toList();
        return new RefundListResult(body, PageResponse.from(page));
    }

    /**
     * 상태 필터를 검증합니다.
     *
     * <p>정의되지 않은 값을 그대로 SQL 에 넘기면 조용히 빈 목록이 나와, 사용자는
     * "환불이 없다" 고 오해합니다. 오타를 400 으로 되돌립니다.
     */
    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String upper = status.trim().toUpperCase();
        try {
            return RefundStatus.valueOf(upper).name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "지원하지 않는 환불 상태입니다. status=" + status);
        }
    }

    /** 목록 응답과 페이지 메타. */
    public record RefundListResult(List<RefundResponse> body, PageResponse meta) {
    }
}
