package com.gongguhan.domain.participation.service;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.dto.DisputeRequest;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.repository.SellerPenaltyRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 구매자 이의 제기 (CFM-02, S15P21B205-77).
 *
 * <p>접수 즉시 {@code confirm_hold=true}로 자동 구매확정을 보류하고(FR-CFM-10),
 * 관리자 검토 큐인 {@code seller_penalties(status=CANDIDATE)}에 위반 후보를 쌓는다.
 * 이의는 참여당 1회이므로(중복은 confirm_hold가 거른다) 후보도 접수마다 1건씩 만든다 -
 * 여러 구매자가 같은 공동구매에 이의를 내면 각각 별도 후보가 되는 것이 맞다.
 */
@Service
@Transactional(readOnly = true)
public class ParticipationDisputeService {

    public static final String TYPE_NOT_DELIVERED = "NOT_DELIVERED";
    public static final String TYPE_ITEM_MISMATCH = "ITEM_MISMATCH";

    private final ParticipationRepository participationRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final SellerPenaltyRepository sellerPenaltyRepository;
    private final Clock clock;

    public ParticipationDisputeService(ParticipationRepository participationRepository,
                                        GroupBuyRepository groupBuyRepository,
                                        SellerPenaltyRepository sellerPenaltyRepository,
                                        Clock clock) {
        this.participationRepository = participationRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.sellerPenaltyRepository = sellerPenaltyRepository;
        this.clock = clock;
    }

    @Transactional
    public void submitDispute(Long userId, Long participationId, DisputeRequest request) {
        String reasonType = mapReasonType(request.type());

        Participation participation = participationRepository.findById(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참여 정보를 찾을 수 없습니다."));
        if (!participation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }

        participation.holdConfirmForDispute();

        GroupBuy groupBuy = groupBuyRepository.findById(participation.getGroupBuyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
        sellerPenaltyRepository.save(SellerPenalty.candidate(
                groupBuy.getSellerId(), groupBuy.getGroupBuyId(), reasonType,
                "이의 제기(participationId=%d): %s".formatted(participationId, request.detailReason()),
                LocalDateTime.now(clock)));
    }

    /**
     * 이의 유형 → 위반 사유 매핑. NOT_DELIVERED는 송장·배송완료 기록과 실제 수령이
     * 다르다는 주장이므로 FAKE_INVOICE 후보로 분류한다.
     */
    private String mapReasonType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case TYPE_NOT_DELIVERED -> SellerPenalty.REASON_FAKE_INVOICE;
            case TYPE_ITEM_MISMATCH -> SellerPenalty.REASON_ITEM_MISMATCH;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "type은 NOT_DELIVERED 또는 ITEM_MISMATCH만 허용합니다.");
        };
    }
}
