package com.gongguhan.domain.payment.common.service;

import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.port.EscrowWriterPort;
import com.gongguhan.domain.payment.common.port.GroupBuySlotPort;
import com.gongguhan.domain.payment.common.port.ParticipationWriterPort;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    /**
     * Redis 게이트를 통과한 요청끼리만 경쟁하므로(정원 초과는 이미 걸러짐) 충돌은
     * 드물 것으로 예상 - 3회면 충분하다. 그 이상도 계속 충돌한다면 재시도로 풀릴
     * 문제가 아니라고 보고 포기한다.
     */
    private static final int MAX_RESERVATION_ATTEMPTS = 3;

    /**
     * {@code GroupBuyRepository}만 남기는 이유(S15P21B205-103 포트 마이그레이션 예외).
     *
     * <p>{@link #prepare}의 Redis 게이트 사전조회(findSlotCounts)는 고QPS 경로라
     * 프로젝션 쿼리로 남긴다. {@link GroupBuySlotPort#get}은 엔티티 전체를 읽어와
     * 이 자리에 쓰면 불필요하게 무겁다. 이 필드 외의 모든 {@code GroupBuy} 엔티티
     * 직접 접근은 {@link GroupBuySlotPort}로 옮겼다.
     */
    private final GroupBuyRepository groupBuyRepository;
    private final PaymentRepository paymentRepository;
    private final GroupBuySlotGate groupBuySlotGate;
    private final GroupBuySlotPort groupBuySlotPort;
    private final ParticipationWriterPort participationWriterPort;
    private final EscrowWriterPort escrowWriterPort;
    private final PaymentSlotReservationTransaction slotReservationTransaction;
    private final Clock clock;

    public PaymentService(GroupBuyRepository groupBuyRepository,
                           PaymentRepository paymentRepository,
                           GroupBuySlotGate groupBuySlotGate,
                           GroupBuySlotPort groupBuySlotPort,
                           ParticipationWriterPort participationWriterPort,
                           EscrowWriterPort escrowWriterPort,
                           PaymentSlotReservationTransaction slotReservationTransaction,
                           Clock clock) {
        this.groupBuyRepository = groupBuyRepository;
        this.paymentRepository = paymentRepository;
        this.groupBuySlotGate = groupBuySlotGate;
        this.groupBuySlotPort = groupBuySlotPort;
        this.participationWriterPort = participationWriterPort;
        this.escrowWriterPort = escrowWriterPort;
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
            GroupBuySlotPort.GroupBuySlot existingGroupBuySlot = groupBuySlotPort.get(existing.getGroupBuyId());
            return PaymentResponse.from(existing, existingGroupBuySlot, LocalDateTime.now(clock));
        }

        // Redis fast-fail 게이트: 프로젝션 조회(짧은 자체 트랜잭션, 즉시 커넥션 반납)라
        // PaymentSlotReservationTransaction의 슬롯 예약과 버전 충돌이 나지 않고, 락도
        // 잡지 않는다. 정원 초과면 그 슬롯 예약까지 가지 않고 여기서 즉시 거부한다 -
        // 고경합 상황에서 거부될 요청이 MySQL 락/커넥션풀 대기열에 쌓이지 않게 하는 것이
        // 이 게이트의 유일한 목적이다.
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
            return reserveWithRetry(userId, groupBuyId, request, idempotencyKey);
        } catch (RuntimeException e) {
            // DB 쪽에서 어떤 이유로든 실패하면(중복 참여, 주소 소유권, DB 자체의
            // GROUP_BUY_FULL, 제약 위반, 재시도 소진 등) Redis에 잠정 승인해둔
            // 슬롯을 되돌린다.
            if (gateApplies) {
                groupBuySlotGate.release(groupBuyId);
            }
            throw e;
        }
    }

    /**
     * {@link PaymentSlotReservationTransaction#execute}는 낙관적 락({@code GroupBuy.version})을
     * 쓴다 - {@link ConcurrencyFailureException}(과 그 하위 타입)만 재시도 대상이고, 그 외
     * 실패(중복 참여, 주소 소유권 등 업무 규칙 위반)는 재시도해도 결과가 같으므로 즉시
     * 전파한다. 재시도마다 {@code slotReservationTransaction}의 프록시를 다시 거쳐야 매번
     * 새 트랜잭션이 열린다 - 그래서 이 루프는 여기(호출하는 쪽)에 있어야 한다.
     *
     * <p>버전 충돌({@code ObjectOptimisticLockingFailureException}, {@code
     * OptimisticLockingFailureException}의 하위 타입)뿐 아니라, 비관적 락을 뗀 뒤 실제
     * 부하테스트에서 InnoDB 데드락({@code CannotAcquireLockException},
     * {@code PessimisticLockingFailureException}의 하위 타입)도 재현됐다 - 여러 트랜잭션이
     * {@code payments.group_buy_id} FK 체크로 group_buys 행에 공유 락을 동시에 잡은 뒤 각자
     * UPDATE로 배타 락 승격을 시도하면서 발생. 두 예외 모두
     * {@code org.springframework.dao.ConcurrencyFailureException}의 하위 타입이라 하나의
     * catch로 함께 처리한다.
     */
    private PaymentResponse reserveWithRetry(Long userId, Long groupBuyId, PaymentPrepareRequest request,
                                              String idempotencyKey) {
        for (int attempt = 1; attempt <= MAX_RESERVATION_ATTEMPTS; attempt++) {
            try {
                return slotReservationTransaction.execute(userId, groupBuyId, request, idempotencyKey);
            } catch (ConcurrencyFailureException e) {
                if (attempt == MAX_RESERVATION_ATTEMPTS) {
                    throw new BusinessException(ErrorCode.SLOT_RESERVATION_CONFLICT);
                }
                sleepBeforeRetry();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 15));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = findOwnedPayment(userId, paymentId);
        GroupBuySlotPort.GroupBuySlot groupBuySlot = groupBuySlotPort.get(payment.getGroupBuyId());
        return PaymentResponse.from(payment, groupBuySlot, LocalDateTime.now(clock));
    }

    @Transactional
    public void abort(Long userId, Long paymentId) {
        Payment payment = findOwnedPayment(userId, paymentId);
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        groupBuySlotPort.release(payment.getGroupBuyId());
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

        groupBuySlotPort.release(payment.getGroupBuyId());
        groupBuySlotGate.release(payment.getGroupBuyId());
        payment.markExpired();
    }

    public Page<PaymentResponse> listHistory(Long userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable)
                .map(payment -> PaymentResponse.from(
                        payment, groupBuySlotPort.get(payment.getGroupBuyId()), LocalDateTime.now(clock)));
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

        // GroupBuySlotPort.confirm은 findWithLockByGroupBuyId(비관적 락)로 재조회 후
        // confirmParticipant()까지 한 번에 수행한다 - completePayment 전체가 하나의
        // 트랜잭션이라 이 시점에 먼저 호출해도(참여·에스크로 생성 실패 시 롤백되어) 예전과
        // 동일하게 원자적이다. 마지막 슬롯이면 즉시 FULFILLED 판정도 함께 일어난다(예전엔
        // 배치가 다음 주기에 판정했음 - 부수 개선).
        GroupBuySlotPort.GroupBuySlot groupBuySlot = groupBuySlotPort.confirm(payment.getGroupBuyId());

        ParticipationWriterPort.ParticipationRecord participation = participationWriterPort.create(
                new ParticipationWriterPort.ParticipationCreateCommand(
                        payment.getGroupBuyId(), payment.getUserId(),
                        groupBuySlot.unitShareAmount(), groupBuySlot.shippingFee(), payment.getKrwAmount(),
                        snapshot.recipientName(), snapshot.recipientPhone(), snapshot.zipcode(),
                        snapshot.address1(), snapshot.address2()));

        // 보관 시각도 Clock 에서 가져온다. 엔티티가 LocalDateTime.now() 를 직접 부르면
        // 테스트에서 시각을 고정할 수 없고, DB 에 UTC/KST 가 섞인다.
        escrowWriterPort.hold(participation.participationId(), payment.getGroupBuyId(),
                payment.getKrwAmount(), LocalDateTime.now(clock));

        payment.markPaid(participation.participationId());
    }

    private Payment findOwnedPayment(Long userId, Long paymentId) {
        return paymentRepository.findByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
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
