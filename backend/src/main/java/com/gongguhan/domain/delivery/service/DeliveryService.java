package com.gongguhan.domain.delivery.service;

import com.gongguhan.domain.delivery.dto.DeliveryBulkStatusResponse;
import com.gongguhan.domain.delivery.dto.DeliveryInvoiceRequest;
import com.gongguhan.domain.delivery.dto.DeliveryResponse;
import com.gongguhan.domain.delivery.dto.SellerDeliveryItemResponse;
import com.gongguhan.domain.delivery.dto.SellerDeliveryListResponse;
import com.gongguhan.domain.delivery.entity.Delivery;
import com.gongguhan.domain.delivery.repository.DeliveryRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배송 API(DLV-01~05). 배송 상태와 참여 상태는 항상 한 트랜잭션에서 함께 움직인다 -
 * Delivery(READY→SHIPPING→DELIVERED)와 Participation(FULFILLED→SHIPPING→DELIVERED)이
 * 따로 놀면 구매확정·반품 판정이 서로 다른 상태를 보게 된다.
 */
@Service
@Transactional(readOnly = true)
public class DeliveryService {

    private final DeliveryRepository deliveryRepository;
    private final ParticipationRepository participationRepository;
    private final GroupBuyRepository groupBuyRepository;
    private final PolicyService policyService;
    private final Clock clock;

    public DeliveryService(DeliveryRepository deliveryRepository,
                            ParticipationRepository participationRepository,
                            GroupBuyRepository groupBuyRepository,
                            PolicyService policyService,
                            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.participationRepository = participationRepository;
        this.groupBuyRepository = groupBuyRepository;
        this.policyService = policyService;
        this.clock = clock;
    }

    /** DLV-01: 참여자별 배송 목록과 송장 등록률. */
    public SellerDeliveryListResponse getSellerDeliveries(Long sellerId, Long groupBuyId) {
        findOwnedGroupBuy(sellerId, groupBuyId);
        List<SellerDeliveryItemResponse> items = new ArrayList<>();
        for (ParticipationWithDelivery row : findRows(groupBuyId)) {
            items.add(SellerDeliveryItemResponse.of(row.participation(), row.delivery()));
        }
        return SellerDeliveryListResponse.of(items);
    }

    /** DLV-02: 송장 등록. 모집 완료 상태에서만 허용하고 동일 송장 중복을 거부한다. */
    @Transactional
    public DeliveryResponse registerInvoice(Long sellerId, Long deliveryId, DeliveryInvoiceRequest request) {
        ParticipationWithDelivery row = findLockedRow(deliveryId);
        Delivery delivery = row.delivery();
        Participation participation = row.participation();
        GroupBuy groupBuy = requireSellerOwnership(sellerId, participation.getGroupBuyId());

        if (!GroupBuy.STATUS_FULFILLED.equals(groupBuy.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "모집 완료 상태에서만 송장을 등록할 수 있습니다.");
        }
        if (deliveryRepository.existsByCourierCodeAndTrackingNumberAndDeliveryIdNot(
                request.carrierCode(), request.trackingNumber(), deliveryId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_TRACKING_NUMBER);
        }

        LocalDateTime shippedAt = request.shippedAt() == null
                ? LocalDateTime.now(clock)
                : request.shippedAt().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        delivery.registerInvoice(request.carrierCode(), request.trackingNumber(), shippedAt);
        return DeliveryResponse.from(delivery);
    }

    /** DLV-03: 참여자별 배송 상태 전환 (READY→SHIPPING→DELIVERED). */
    @Transactional
    public DeliveryResponse updateStatus(Long sellerId, Long deliveryId, String rawStatus) {
        ParticipationWithDelivery row = findLockedRow(deliveryId);
        Delivery delivery = row.delivery();
        Participation participation = row.participation();
        requireSellerOwnership(sellerId, participation.getGroupBuyId());

        transition(delivery, participation, normalizeTargetStatus(rawStatus));
        return DeliveryResponse.from(delivery);
    }

    /**
     * DLV-04: 공동구매 단위 일괄 상태 전환. 전이 조건이 안 맞는 행(송장 미등록, 이미
     * 전환됨, 반품 진행 중 등)은 실패 대신 건너뛴다 - 일괄 처리에서 한 건 때문에 전체가
     * 롤백되면 판매자가 개별 API로 다시 처리할 방법이 없다.
     */
    @Transactional
    public DeliveryBulkStatusResponse bulkUpdateStatus(Long sellerId, Long groupBuyId, String rawStatus) {
        findOwnedGroupBuy(sellerId, groupBuyId);
        String targetStatus = normalizeTargetStatus(rawStatus);

        int updated = 0;
        int total = 0;
        // ID만 먼저 읽는다. 잠금 전에 엔티티를 읽으면 1차 캐시의 오래된 confirmHold가 남는다.
        for (Long participationId : participationRepository.findIdsByGroupBuyIdOrderByParticipationIdAsc(groupBuyId)) {
            Participation participation = findLockedParticipation(participationId);
            Delivery delivery = deliveryRepository.findByParticipationId(participationId).orElse(null);
            total++;
            if (delivery != null && canTransition(delivery, participation, targetStatus)) {
                transition(delivery, participation, targetStatus);
                updated++;
            }
        }
        return new DeliveryBulkStatusResponse(total, updated, total - updated);
    }

    /** DLV-05: 구매자 본인 참여의 배송 상태·송장 조회. */
    public DeliveryResponse getUserDelivery(Long userId, Long participationId) {
        Participation participation = findParticipation(participationId);
        if (!participation.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }
        Delivery delivery = deliveryRepository.findByParticipationId(participationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND, "배송 정보가 아직 생성되지 않았습니다. 모집 완료 후 조회할 수 있습니다."));
        return DeliveryResponse.from(delivery);
    }

    private void transition(Delivery delivery, Participation participation, String targetStatus) {
        if (Delivery.STATUS_SHIPPING.equals(targetStatus)) {
            delivery.markShipping();
            participation.markShipping();
            return;
        }
        LocalDateTime deliveredAt = LocalDateTime.now(clock);
        delivery.markDelivered(deliveredAt);
        participation.markDelivered(
                deliveredAt,
                deliveredAt.plusDays(policyService.getInt(PolicyKey.WITHDRAWAL_PERIOD_DAYS)),
                deliveredAt.plusDays(policyService.getInt(PolicyKey.SETTLEMENT_ELIGIBLE_DAYS)));
    }

    private boolean canTransition(Delivery delivery, Participation participation, String targetStatus) {
        if (Delivery.STATUS_SHIPPING.equals(targetStatus)) {
            return delivery.isReady() && delivery.hasInvoice()
                    && Participation.STATUS_FULFILLED.equals(participation.getStatus());
        }
        return delivery.isShipping()
                && Participation.STATUS_SHIPPING.equals(participation.getStatus());
    }

    private String normalizeTargetStatus(String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        if (!Delivery.STATUS_SHIPPING.equals(status) && !Delivery.STATUS_DELIVERED.equals(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status는 SHIPPING 또는 DELIVERED만 허용합니다.");
        }
        return status;
    }

    /** groupBuyId 기준 참여·배송을 참여 생성순으로 묶는다. 배송 행이 없는 참여도 포함된다. */
    private List<ParticipationWithDelivery> findRows(Long groupBuyId) {
        List<Participation> participations =
                participationRepository.findByGroupBuyIdOrderByParticipationIdAsc(groupBuyId);
        List<Long> participationIds = participations.stream()
                .map(Participation::getParticipationId)
                .toList();
        Map<Long, Delivery> deliveriesByParticipationId = participationIds.isEmpty()
                ? Map.of()
                : deliveryRepository.findByParticipationIdIn(participationIds).stream()
                        .collect(Collectors.toMap(Delivery::getParticipationId, Function.identity()));
        return participations.stream()
                .map(participation -> new ParticipationWithDelivery(
                        participation, deliveriesByParticipationId.get(participation.getParticipationId())))
                .toList();
    }

    /** 판매자 소유가 아니면 존재 자체를 숨긴다(GroupBuyQueryService와 같은 패턴). */
    private GroupBuy findOwnedGroupBuy(Long sellerId, Long groupBuyId) {
        return groupBuyRepository.findById(groupBuyId)
                .filter(found -> found.getSellerId().equals(sellerId))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
    }

    /** 배송 단건 경로 - 남의 배송임이 이미 특정되므로 403으로 알린다(명세 16장). */
    private GroupBuy requireSellerOwnership(Long sellerId, Long groupBuyId) {
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
        if (!groupBuy.getSellerId().equals(sellerId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }
        return groupBuy;
    }

    private Delivery findDelivery(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "배송 정보를 찾을 수 없습니다."));
    }

    private ParticipationWithDelivery findLockedRow(Long deliveryId) {
        Long participationId = deliveryRepository.findParticipationIdByDeliveryId(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "배송 정보를 찾을 수 없습니다."));
        // 분쟁·반품·확정과 동일한 참여 행을 먼저 잠근 뒤 배송을 읽는다.
        Participation participation = findLockedParticipation(participationId);
        return new ParticipationWithDelivery(participation, findDelivery(deliveryId));
    }

    private Participation findLockedParticipation(Long participationId) {
        return participationRepository.findWithLockByParticipationId(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참여 정보를 찾을 수 없습니다."));
    }

    private Participation findParticipation(Long participationId) {
        return participationRepository.findById(participationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참여 정보를 찾을 수 없습니다."));
    }

    private record ParticipationWithDelivery(Participation participation, Delivery delivery) {
    }
}
