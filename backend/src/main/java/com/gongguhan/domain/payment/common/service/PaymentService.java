package com.gongguhan.domain.payment.common.service;

import com.gongguhan.domain.escrow.entity.Escrow;
import com.gongguhan.domain.escrow.repository.EscrowRepository;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
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
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final GroupBuyRepository groupBuyRepository;
    private final ParticipationRepository participationRepository;
    private final EscrowRepository escrowRepository;
    private final PaymentRepository paymentRepository;
    private final GroupBuySlotGate groupBuySlotGate;
    private final PaymentSlotReservationTransaction slotReservationTransaction;
    private final Clock clock;

    public PaymentService(GroupBuyRepository groupBuyRepository,
                           ParticipationRepository participationRepository,
                           EscrowRepository escrowRepository,
                           PaymentRepository paymentRepository,
                           GroupBuySlotGate groupBuySlotGate,
                           PaymentSlotReservationTransaction slotReservationTransaction,
                           Clock clock) {
        this.groupBuyRepository = groupBuyRepository;
        this.participationRepository = participationRepository;
        this.escrowRepository = escrowRepository;
        this.paymentRepository = paymentRepository;
        this.groupBuySlotGate = groupBuySlotGate;
        this.slotReservationTransaction = slotReservationTransaction;
        this.clock = clock;
    }

    /**
     * 의도적으로 트랜잭션 밖에서 실행된다({@code NOT_SUPPORTED}로 클래스 레벨의
     * readOnly 트랜잭션도 무시). Redis 게이트 왕복 시간 동안 DB 커넥션을 붙잡지
     * 않아야, 정원 초과로 거부되는 요청이 커넥션 풀 대기열에도 쌓이지 않는다.
     * 실제 DB 작업(비관적 락 포함)은 게이트를 통과한 요청만
     * {@link PaymentSlotReservationTransaction#execute}의 자체 트랜잭션에서 수행한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResponse prepare(Long userId, Long groupBuyId, PaymentPrepareRequest request,
                                    String idempotencyKey) {
        Payment existing = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            GroupBuy existingGroupBuy = findGroupBuy(existing.getGroupBuyId());
            return PaymentResponse.from(existing, existingGroupBuy, LocalDateTime.now(clock));
        }

        // Redis fast-fail 게이트: 프로젝션 조회(짧은 자체 트랜잭션, 즉시 커넥션 반납)라
        // 아래 findWithLockByGroupBuyId와 버전 충돌이 나지 않고, 락도 잡지 않는다.
        // 정원 초과면 findWithLockByGroupBuyId(비관적 락)까지 가지 않고 여기서 즉시
        // 거부한다 - 고경합 상황에서 거부될 요청이 MySQL 락/커넥션풀 대기열에 쌓이지
        // 않게 하는 것이 이 게이트의 유일한 목적이다.
        GroupBuyRepository.SlotCounts gatePreCheck = groupBuyRepository.findSlotCounts(groupBuyId).orElse(null);
        boolean gateApplies = gatePreCheck != null;
        if (gateApplies) {
            int dbTruth = gatePreCheck.getReservedSlots() + gatePreCheck.getCurrentParticipants();
            boolean granted = groupBuySlotGate.tryReserve(groupBuyId, gatePreCheck.getTargetParticipants(), dbTruth);
            if (!granted) {
                throw new BusinessException(ErrorCode.GROUP_BUY_FULL);
            }
        }

        try {
            return slotReservationTransaction.execute(userId, groupBuyId, request, idempotencyKey);
        } catch (RuntimeException e) {
            // DB 쪽에서 어떤 이유로든 실패하면(중복 참여, 주소 소유권, DB 자체의
            // GROUP_BUY_FULL, 제약 위반 등) Redis에 잠정 승인해둔 슬롯을 되돌린다.
            if (gateApplies) {
                groupBuySlotGate.release(groupBuyId);
            }
            throw e;
        }
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
        groupBuySlotGate.release(payment.getGroupBuyId());
        payment.markFailed("USER_ABORTED");
    }

    /**
     * 슬롯 만료 배치(1분 주기)가 호출한다. idx_payments_slot_expire(status, slot_expires_at)로
     * 대상 후보를 뽑고, 실제 해제는 건별로 별도 트랜잭션(expireSlotIfDue)에서 재검증 후 처리한다.
     */
    public List<Long> findExpiredSlotPaymentIds() {
        return paymentRepository
                .findByStatusAndSlotExpiresAtBefore(Payment.STATUS_PROCESSING, LocalDateTime.now(clock))
                .stream()
                .map(Payment::getPaymentId)
                .toList();
    }

    /**
     * 조회 시점과 처리 시점 사이에 abort/completePayment로 이미 상태가 바뀌었을 수 있으므로
     * 재조회 후 PROCESSING·만료 조건을 다시 확인한다 (중복 슬롯 해제 방지).
     */
    @Transactional
    public void expireSlotIfDue(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !payment.isProcessing() || !payment.isExpired(LocalDateTime.now(clock))) {
            return;
        }

        GroupBuy groupBuy = groupBuyRepository.findWithLockByGroupBuyId(payment.getGroupBuyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "공동구매를 찾을 수 없습니다."));
        groupBuy.releaseSlot();
        groupBuySlotGate.release(payment.getGroupBuyId());
        payment.markExpired();
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

        // 보관 시각도 Clock 에서 가져온다. 엔티티가 LocalDateTime.now() 를 직접 부르면
        // 테스트에서 시각을 고정할 수 없고, DB 에 UTC/KST 가 섞인다.
        escrowRepository.save(Escrow.hold(
                participation.getParticipationId(), payment.getGroupBuyId(),
                payment.getKrwAmount(), LocalDateTime.now(clock)));

        payment.markPaid(participation.getParticipationId());
        groupBuy.confirmParticipant();
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
