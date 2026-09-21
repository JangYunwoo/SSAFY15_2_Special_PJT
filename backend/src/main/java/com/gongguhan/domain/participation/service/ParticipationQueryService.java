package com.gongguhan.domain.participation.service;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.participation.dto.ParticipationDetailResponse;
import com.gongguhan.domain.participation.dto.ParticipationHistoryEntry;
import com.gongguhan.domain.participation.dto.ParticipationSummaryResponse;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationQueryRepository;
import com.gongguhan.domain.participation.repository.ParticipationQueryRepository.MyParticipationRow;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 구매자 참여 내역 조회 (PAR-01·02). FE 거래관리 화면이 소비하는 계약. */
@Service
@Transactional(readOnly = true)
public class ParticipationQueryService {

    private static final Set<String> PARTICIPATION_STATUSES = Set.of(
            "PAID", "FULFILLED", "SHIPPING", "DELIVERED", "CONFIRMED", "SETTLED",
            "CANCELED", "RETURNING", "RETURNED", "REFUNDING", "REFUNDED");

    private final ParticipationQueryRepository participationQueryRepository;
    private final PolicyService policyService;
    private final Clock clock;

    public ParticipationQueryService(ParticipationQueryRepository participationQueryRepository,
                                      PolicyService policyService,
                                      Clock clock) {
        this.participationQueryRepository = participationQueryRepository;
        this.policyService = policyService;
        this.clock = clock;
    }

    public Page<ParticipationSummaryResponse> getMyParticipations(
            Long userId, String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page는 0 이상, size는 1 이상 100 이하여야 합니다.");
        }
        int autoConfirmDays = policyService.getInt(PolicyKey.AUTO_CONFIRM_DAYS);
        return participationQueryRepository
                .findMyParticipations(userId, normalizeStatus(status), PageRequest.of(page, size))
                .map(row -> toSummary(row, autoConfirmDays));
    }

    public ParticipationDetailResponse getMyParticipationDetail(Long userId, Long participationId) {
        MyParticipationRow row = participationQueryRepository.findByParticipationId(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참여 정보를 찾을 수 없습니다."));
        if (!row.userId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }

        int autoConfirmDays = policyService.getInt(PolicyKey.AUTO_CONFIRM_DAYS);
        ParticipationSummaryResponse summary = toSummary(row, autoConfirmDays);
        LocalDateTime now = LocalDateTime.now(clock);
        return new ParticipationDetailResponse(
                summary.id(), summary.groupBuyId(), summary.productName(), summary.sellerName(),
                summary.status(), summary.groupBuyStatus(), summary.escrowStatus(), summary.method(),
                summary.shareAmount(), summary.shippingFee(), summary.totalAmount(), summary.paidAt(),
                summary.targetCount(), summary.paidCount(), summary.recruitEndAt(), summary.deliveredAt(),
                summary.autoConfirmAt(), summary.withdrawalDeadlineAt(), summary.confirmedAt(),
                summary.autoConfirmed(),
                row.paymentId(),
                row.recipientName(), row.recipientPhone(), row.zipcode(), row.address1(), row.address2(), row.deliveryRequest(),
                buildHistory(row),
                canCancel(row),
                canConfirm(row),
                canRequestReturn(row, now));
    }

    private ParticipationSummaryResponse toSummary(MyParticipationRow row, int autoConfirmDays) {
        LocalDateTime autoConfirmAt = row.deliveredAt() == null || row.confirmedAt() != null
                ? null
                : row.deliveredAt().plusDays(autoConfirmDays);
        return new ParticipationSummaryResponse(
                row.participationId(),
                row.groupBuyId(),
                row.productName(),
                row.sellerName(),
                row.status(),
                row.groupBuyStatus(),
                row.escrowStatus(),
                row.method(),
                row.shareAmount(),
                row.shippingFee(),
                row.totalAmount(),
                toUtc(row.joinedAt()),
                row.targetParticipants(),
                row.currentParticipants(),
                toUtc(row.recruitEndAt()),
                toUtc(row.deliveredAt()),
                toUtc(autoConfirmAt),
                toUtc(row.withdrawalPeriodEndAt()),
                toUtc(row.confirmedAt()),
                Participation.CONFIRM_TYPE_AUTO.equals(row.confirmType()));
    }

    /**
     * 정상 경로(PAID→…→CONFIRMED)의 단계별 기록 시각으로 이력을 유도한다. 별도 이력
     * 테이블이 없으므로 취소·반품 등 이탈 경로는 현재 status 필드로만 드러난다.
     */
    private List<ParticipationHistoryEntry> buildHistory(MyParticipationRow row) {
        List<ParticipationHistoryEntry> history = new ArrayList<>();
        addHistory(history, Participation.STATUS_PAID, row.joinedAt());
        addHistory(history, Participation.STATUS_FULFILLED, row.fulfilledAt());
        addHistory(history, Participation.STATUS_SHIPPING, row.shippedAt());
        addHistory(history, Participation.STATUS_DELIVERED, row.deliveredAt());
        addHistory(history, Participation.STATUS_CONFIRMED, row.confirmedAt());
        return history;
    }

    private void addHistory(List<ParticipationHistoryEntry> history, String status, LocalDateTime occurredAt) {
        if (occurredAt != null) {
            history.add(new ParticipationHistoryEntry(status, toUtc(occurredAt), null));
        }
    }

    private boolean canCancel(MyParticipationRow row) {
        return Participation.STATUS_PAID.equals(row.status())
                && GroupBuy.STATUS_RECRUITING.equals(row.groupBuyStatus());
    }

    private boolean canConfirm(MyParticipationRow row) {
        return Participation.STATUS_DELIVERED.equals(row.status()) && !row.confirmHold();
    }

    private boolean canRequestReturn(MyParticipationRow row, LocalDateTime now) {
        boolean inReturnableStatus = Participation.STATUS_DELIVERED.equals(row.status())
                || Participation.STATUS_CONFIRMED.equals(row.status());
        return inReturnableStatus
                && row.withdrawalPeriodEndAt() != null
                && now.isBefore(row.withdrawalPeriodEndAt());
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!PARTICIPATION_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 참여 상태값입니다.");
        }
        return normalized;
    }

    private OffsetDateTime toUtc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
