package com.gongguhan.domain.payment.common.service;

import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final String METHOD_CARD = "CARD";
    private static final String METHOD_KAKAOPAY = "KAKAOPAY";
    private static final String METHOD_CRYPTO = "CRYPTO";
    private static final long TTL_CARD_KAKAOPAY_SECONDS = 300;
    private static final long TTL_CRYPTO_SECONDS = 600;

    private final GroupBuyRepository groupBuyRepository;
    private final ParticipationRepository participationRepository;
    private final EscrowRepository escrowRepository;
    private final PaymentRepository paymentRepository;
    private final UserAddressRepository userAddressRepository;
    private final Clock clock;

    public PaymentService(GroupBuyRepository groupBuyRepository,
                           ParticipationRepository participationRepository,
                           EscrowRepository escrowRepository,
                           PaymentRepository paymentRepository,
                           UserAddressRepository userAddressRepository,
                           Clock clock) {
        this.groupBuyRepository = groupBuyRepository;
        this.participationRepository = participationRepository;
        this.escrowRepository = escrowRepository;
        this.paymentRepository = paymentRepository;
        this.userAddressRepository = userAddressRepository;
        this.clock = clock;
    }

    @Transactional
    public PaymentResponse prepare(Long userId, Long groupBuyId, PaymentPrepareRequest request,
                                    String idempotencyKey) {
        Payment existing = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            GroupBuy existingGroupBuy = findGroupBuy(existing.getGroupBuyId());
            return PaymentResponse.from(existing, existingGroupBuy, LocalDateTime.now(clock));
        }

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

    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = findOwnedPayment(userId, paymentId);
        GroupBuy groupBuy = findGroupBuy(payment.getGroupBuyId());
        return PaymentResponse.from(payment, groupBuy, LocalDateTime.now(clock));
    }

    @Transactional
    public void abort(Long userId, Long paymentId) {
        Payment payment = findOwnedPayment(userId, paymentId);
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        GroupBuy groupBuy = groupBuyRepository.findWithLockByGroupBuyId(payment.getGroupBuyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
        groupBuy.releaseSlot();
        payment.markFailed("USER_ABORTED");
    }

    public Page<PaymentResponse> listHistory(Long userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable)
                .map(payment -> PaymentResponse.from(
                        payment, findGroupBuy(payment.getGroupBuyId()), LocalDateTime.now(clock)));
    }

    /**
     * 카드/카카오페이/코인 승인 성공 후 호출되는 공통 커밋 로직.
     * 컨트롤러에 연결되지 않으며, 각 결제수단(CARD-01 등)의 승인 서비스가 승인 성공 시 직접 호출한다.
     */
    @Transactional
    public void completePayment(Long paymentId, ParticipationSnapshot snapshot) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제를 찾을 수 없습니다."));
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        if (payment.isExpired(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        }

        GroupBuy groupBuy = groupBuyRepository.findWithLockByGroupBuyId(payment.getGroupBuyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));

        Participation participation = Participation.create(
                payment.getGroupBuyId(), payment.getUserId(),
                groupBuy.getUnitShareAmount(), groupBuy.getShippingFee(), payment.getKrwAmount(),
                snapshot.recipientName(), snapshot.recipientPhone(), snapshot.zipcode(),
                snapshot.address1(), snapshot.address2());
        participationRepository.saveAndFlush(participation);

        escrowRepository.save(Escrow.hold(
                participation.getParticipationId(), payment.getGroupBuyId(), payment.getKrwAmount()));

        payment.markPaid(participation.getParticipationId());
        groupBuy.confirmParticipant();
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

    private Payment findOwnedPayment(Long userId, Long paymentId) {
        return paymentRepository.findByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
    }

    private GroupBuy findGroupBuy(Long groupBuyId) {
        return groupBuyRepository.findById(groupBuyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
    }

    public record ParticipationSnapshot(
            String recipientName,
            String recipientPhone,
            String zipcode,
            String address1,
            String address2
    ) {
    }
}
