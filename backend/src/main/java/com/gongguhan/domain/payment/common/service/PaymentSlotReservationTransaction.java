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
 * 슬롯 선점의 실제 DB 트랜잭션만 담당하는 별도 빈.
 *
 * <p>{@link PaymentService#prepare}가 이 메서드를 직접 담고 있으면 안 되는 이유:
 * 같은 클래스 안에서 {@code @Transactional} 메서드를 호출하면(self-invocation) 프록시를
 * 거치지 않아 트랜잭션이 실제로 걸리지 않는다. 별도 빈으로 분리해야 {@code prepare()}가
 * Redis 게이트 체크(및 그 왕복 시간) 동안 DB 커넥션을 붙잡지 않게 되고, 재시도할 때마다
 * 이 프록시를 다시 거쳐 매번 새 트랜잭션이 열리게 된다(실패한 트랜잭션 안에서 재시도하면
 * 안 된다).
 *
 * <p><b>낙관적 락({@code GroupBuy.version}) 사용, 비관적 락 아님.</b> 정원 초과로 거부될
 * 요청은 이미 Redis 게이트에서 걸러지므로, 이 트랜잭션에 도달하는 요청끼리의 실제 충돌은
 * 드물 것으로 예상된다 - 드문 경쟁에 매번 행 잠금을 거는 것보다, 어쩌다 충돌하면 예외를
 * 받고 {@link PaymentService#prepare}가 재시도하는 편이 동시 처리량에 유리하다. 실제로
 * 슬롯을 얻는 요청끼리는 이제 서로 순서를 기다리지 않고 각자 트랜잭션을 진행하다가, 정말
 * 같은 순간 같은 행을 건드렸을 때만 버전 충돌로 재시도한다. 정원 초과에 대한 최종 안전망은
 * 여전히 {@code ck_group_buys_slots} CHECK 제약이다.
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
        GroupBuy groupBuy = groupBuyRepository.findById(groupBuyId)
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

        // 고른 배송지를 결제 건에 남긴다. 승인 완료 시 참여 건 배송지의 출처가 된다.
        // 코인 결제는 스케줄러가 완료 처리하므로 그 시점에 요청 컨텍스트가 없다.
        Payment payment = Payment.prepare(
                groupBuyId, userId, address.getAddressId(), request.method(), totalAmount,
                slotExpiresAt, idempotencyKey);
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
