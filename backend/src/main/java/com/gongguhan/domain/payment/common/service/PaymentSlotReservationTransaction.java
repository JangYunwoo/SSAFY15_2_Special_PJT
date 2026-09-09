package com.gongguhan.domain.payment.common.service;

import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 슬롯 선점의 실제 DB 트랜잭션(비관적 락 포함)만 담당하는 별도 빈.
 *
 * <p>{@link PaymentService#prepare}가 이 메서드를 직접 담고 있으면 안 되는 이유:
 * 같은 클래스 안에서 {@code @Transactional} 메서드를 호출하면(self-invocation) 프록시를
 * 거치지 않아 트랜잭션이 실제로 걸리지 않는다. 별도 빈으로 분리해야 {@code prepare()}가
 * Redis 게이트 체크(및 그 왕복 시간) 동안 DB 커넥션을 붙잡지 않게 되고 — 커넥션을
 * 붙잡은 채 게이트를 통과시키면 Redis fast-fail 게이트를 추가한 의미가 없어진다
 * (거부될 요청도 결국 커넥션 풀에서 대기하게 되므로).
 */
@Component
class PaymentSlotReservationTransaction {

    private static final String METHOD_CARD = "CARD";
    private static final String METHOD_KAKAOPAY = "KAKAOPAY";
    private static final String METHOD_CRYPTO = "CRYPTO";
    private static final long TTL_CARD_KAKAOPAY_SECONDS = 300;
    private static final long TTL_CRYPTO_SECONDS = 600;

    private final GroupBuyRepository groupBuyRepository;
    private final ParticipationRepository participationRepository;
    private final PaymentRepository paymentRepository;
    private final UserAddressRepository userAddressRepository;
    private final Clock clock;

    PaymentSlotReservationTransaction(GroupBuyRepository groupBuyRepository,
                                       ParticipationRepository participationRepository,
                                       PaymentRepository paymentRepository,
                                       UserAddressRepository userAddressRepository,
                                       Clock clock) {
        this.groupBuyRepository = groupBuyRepository;
        this.participationRepository = participationRepository;
        this.paymentRepository = paymentRepository;
        this.userAddressRepository = userAddressRepository;
        this.clock = clock;
    }

    @Transactional
    PaymentResponse execute(Long userId, Long groupBuyId, PaymentPrepareRequest request, String idempotencyKey) {
        GroupBuy groupBuy = groupBuyRepository.findWithLockByGroupBuyId(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));

        boolean hasActiveParticipation = participationRepository
                .existsByGroupBuyIdAndUserIdAndActiveMarker(groupBuyId, userId, Participation.ACTIVE_MARKER);
        boolean hasActiveSlot = paymentRepository
                .existsByGroupBuyIdAndUserIdAndStatus(groupBuyId, userId, Payment.STATUS_PROCESSING);
        if (hasActiveParticipation || hasActiveSlot) {
            throw new BusinessException(ErrorCode.DUPLICATE_PARTICIPATION);
        }

        UserAddress address = userAddressRepository.findByAddressIdAndUserId(request.addressId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "본인 소유의 배송지가 아닙니다."));
        validateMethod(request);

        groupBuy.reserveSlot();

        long totalAmount = groupBuy.getUnitShareAmount() + groupBuy.getShippingFee();
        long ttlSeconds = METHOD_CRYPTO.equals(request.method()) ? TTL_CRYPTO_SECONDS : TTL_CARD_KAKAOPAY_SECONDS;
        LocalDateTime slotExpiresAt = LocalDateTime.now(clock).plusSeconds(ttlSeconds);

        Payment payment = Payment.prepare(
                groupBuyId, userId, request.method(), totalAmount, slotExpiresAt, idempotencyKey);
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_PARTICIPATION);
        }

        return PaymentResponse.from(payment, groupBuy, LocalDateTime.now(clock));
    }

    private void validateMethod(PaymentPrepareRequest request) {
        boolean knownMethod = METHOD_CARD.equals(request.method())
                || METHOD_KAKAOPAY.equals(request.method())
                || METHOD_CRYPTO.equals(request.method());
        if (!knownMethod) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "지원하지 않는 결제수단입니다.");
        }
        if (METHOD_CRYPTO.equals(request.method()) && request.cryptoAssetId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "디지털 자산 결제에는 cryptoAssetId가 필요합니다.");
        }
    }
}
