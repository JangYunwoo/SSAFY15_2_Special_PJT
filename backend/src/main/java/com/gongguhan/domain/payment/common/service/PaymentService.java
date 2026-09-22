package com.gongguhan.domain.payment.common.service;

import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.groupbuy.service.GroupBuySlotGate;
import com.gongguhan.domain.payment.common.dto.PaymentPrepareRequest;
import com.gongguhan.domain.payment.common.dto.PaymentResponse;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.entity.PaymentShippingSnapshot;
import com.gongguhan.domain.payment.common.port.EscrowWriterPort;
import com.gongguhan.domain.payment.common.port.GroupBuySlotPort;
import com.gongguhan.domain.payment.common.port.ParticipationWriterPort;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
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
    private final UserAddressRepository userAddressRepository;
    private final GroupBuySlotGate groupBuySlotGate;
    private final GroupBuySlotPort groupBuySlotPort;
    private final ParticipationWriterPort participationWriterPort;
    private final EscrowWriterPort escrowWriterPort;
    private final PaymentSlotReservationTransaction slotReservationTransaction;
    private final Clock clock;

    public PaymentService(GroupBuyRepository groupBuyRepository,
                           PaymentRepository paymentRepository,
                           UserAddressRepository userAddressRepository,
                           GroupBuySlotGate groupBuySlotGate,
                           GroupBuySlotPort groupBuySlotPort,
                           ParticipationWriterPort participationWriterPort,
                           EscrowWriterPort escrowWriterPort,
                           PaymentSlotReservationTransaction slotReservationTransaction,
                           Clock clock) {
        this.groupBuyRepository = groupBuyRepository;
        this.paymentRepository = paymentRepository;
        this.userAddressRepository = userAddressRepository;
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
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "요청 키는 비어 있지 않은 64자 이하여야 합니다.");
        }
        validatePrepareRequest(request);
        Payment existing = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return reusePreparedPayment(existing, userId, groupBuyId, request);
        }

        // 멱등키가 달라도 자리는 하나다.
        //
        // 멱등키는 클라이언트가 만드는데, 새로고침하면 그 값이 사라져 같은 화면에서도
        // 매번 새 키로 오게 된다. 그때마다 슬롯을 새로 잡으면 사용자가 자기 자신 때문에
        // 정원을 채우고 GROUP_BUY_FULL로 막힌다. uk_payments_slot(group_buy_id, user_id,
        // slot_marker)이 활성 결제를 하나로 보장하므로 새로 만들어도 어차피 제약에 걸린다.
        // 유효한 자리가 이미 있으면 그것을 그대로 돌려준다.
        //
        // 결제수단이 다르면 재사용하지 않는다 - 금액 검증 대상과 후속 승인 경로가 달라서
        // 카드 슬롯을 코인 결제로 이어 쓸 수 없다. 이전 자리 반납은 호출 측(PAY-03)이 한다.
        Payment activeSlot = paymentRepository
                .findByGroupBuyIdAndUserIdAndStatus(groupBuyId, userId, Payment.STATUS_PROCESSING)
                .orElse(null);
        if (activeSlot != null
                && activeSlot.getMethod().equals(request.method())
                && !activeSlot.isExpired(LocalDateTime.now(clock))) {
            return reusePreparedPayment(activeSlot, userId, groupBuyId, request);
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
            // A concurrent identical request may have committed after our initial lookup.
            // This lookup is outside the failed transaction: never query a rollback-only session.
            if (e instanceof BusinessException business
                    && business.getErrorCode() == ErrorCode.DUPLICATE_PARTICIPATION) {
                Payment winner = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
                if (winner != null) {
                    return reusePreparedPayment(winner, userId, groupBuyId, request);
                }
            }
            throw e;
        }
    }

    private PaymentResponse reusePreparedPayment(Payment payment, Long userId, Long groupBuyId,
                                                 PaymentPrepareRequest request) {
        if (!Objects.equals(payment.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED);
        }
        if (!Objects.equals(payment.getGroupBuyId(), groupBuyId)
                || !Objects.equals(payment.getMethod(), request.method())
                || !Objects.equals(payment.getAddressId(), request.addressId())
                || !Objects.equals(payment.getPrepareCryptoAssetId(), request.cryptoAssetId())
                || !Objects.equals(payment.getPrepareDeliveryRequest(), request.deliveryRequest())) {
            throw new BusinessException(ErrorCode.PAYMENT_REQUEST_CONFLICT);
        }
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (payment.isExpired(now) || !now.isBefore(payment.getSlotExpiresAt())) {
            throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        }
        return PaymentResponse.from(payment, groupBuySlotPort.get(groupBuyId), now);
    }

    private static void validatePrepareRequest(PaymentPrepareRequest request) {
        if (request != null && request.deliveryRequest() != null && request.deliveryRequest().length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "배송 요청사항은 100자 이하여야 합니다.");
        }
        if (request == null || request.addressId() == null || request.addressId() <= 0
                || !Set.of("CARD", "KAKAOPAY", "CRYPTO").contains(Objects.toString(request.method(), ""))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (Payment.METHOD_CRYPTO.equals(request.method())) {
            if (request.cryptoAssetId() == null || request.cryptoAssetId() <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "디지털 자산 결제에는 유효한 cryptoAssetId가 필요합니다.");
            }
        } else if (request.cryptoAssetId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "카드·카카오페이 결제에는 cryptoAssetId를 지정할 수 없습니다.");
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
        Payment payment = paymentRepository.findForUpdateByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        rejectUnresolvedCardApproval(payment);

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
        Payment payment = paymentRepository.findForUpdateByPaymentId(paymentId).orElse(null);
        if (payment == null || !payment.isProcessing() || !payment.isExpired(LocalDateTime.now(clock))) {
            return;
        }
        if (payment.hasUnresolvedCardApproval()) return;

        groupBuySlotPort.release(payment.getGroupBuyId());
        groupBuySlotGate.release(payment.getGroupBuyId());
        payment.markExpired();
    }

    /**
     * 외부 승인(카드/카카오페이/코인) 실패 후 슬롯을 되돌린다. {@link #abort}와 같은
     * 시퀀스(슬롯 포트 해제 → 게이트 해제 → markFailed)를 쓰지만, 사용자가 직접
     * 요청하는 abort와 달리 외부 콜백 경로에서 호출되므로 이미 처리된 결제를 만나도
     * 예외 대신 조용히 무시한다 - {@link #expireSlotIfDue}와 같은 이유로, 배치의 만료
     * 처리와 경합해도 안전해야 한다.
     */
    @Transactional
    public void failPayment(Long paymentId, String reason) {
        Payment payment = paymentRepository.findForUpdateByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제를 찾을 수 없습니다."));
        if (!payment.isProcessing()) {
            return;
        }
        if (payment.hasUnresolvedCardApproval()) return;

        groupBuySlotPort.release(payment.getGroupBuyId());
        groupBuySlotGate.release(payment.getGroupBuyId());
        payment.markFailed(reason);
    }

    public Page<PaymentResponse> listHistory(Long userId, Pageable pageable) {
        Page<Payment> payments = paymentRepository.findByUserId(userId, pageable);
        Set<Long> groupBuyIds = payments.stream()
                .map(Payment::getGroupBuyId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, GroupBuySlotPort.GroupBuySlot> slots = groupBuySlotPort.getAll(groupBuyIds);
        LocalDateTime now = LocalDateTime.now(clock);
        return payments.map(payment -> PaymentResponse.from(
                payment, slots.get(payment.getGroupBuyId()), now));
    }

    /**
     * 카드/카카오페이/코인 승인 성공 후 호출되는 공통 커밋 로직.
     * 컨트롤러에 연결되지 않으며, 각 결제수단(CARD-01 등)의 승인 서비스가 승인 성공 시 직접 호출한다.
     */
    @Transactional
    public PaymentResponse completePayment(Long paymentId) {
        // 코인/레거시 경로: 승인 후 공구가 종료됐으면 기존대로 예외를 던진다(동작 불변).
        return complete(paymentId, false);
    }

    /**
     * 카드/카카오페이 승인 성공 후 호출한다. {@link #completePayment}와 달리, 확정 직전에
     * 공구가 종료됐으면(외부 청구는 이미 성공) 예외로 롤백하지 않고 결제를 환불 대상으로
     * durable하게 기록한 뒤 그 상태를 담은 응답을 돌려준다. 호출 측이 응답의 reconcileState로
     * 환불 예약을 판별한다.
     */
    @Transactional
    public PaymentResponse completeOrScheduleRefund(Long paymentId) {
        return complete(paymentId, true);
    }

    private PaymentResponse complete(Long paymentId, boolean scheduleRefundOnTerminated) {
        Payment payment = paymentRepository.findForUpdateByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "결제를 찾을 수 없습니다."));
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        if ("UNKNOWN".equals(payment.getCardApprovalStatus())) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "카드 승인 결과 대조가 필요한 결제입니다.");
        }
        if (payment.isExpired(LocalDateTime.now(clock)) && !payment.isCardApprovalInFlight()) {
            throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        }

        // 레거시도 승인/견적 이전에 확정되어야 한다. 사후 주소록 조회는 하지 않는다.
        PaymentShippingSnapshot snapshot = storedSnapshot(payment);

        // confirmForCompletion은 findWithLockByGroupBuyId(비관적 락)로 재조회 후, 모집 중이면
        // confirmParticipant()까지 한 번에 수행한다 - complete 전체가 하나의 트랜잭션이라 이
        // 시점에 먼저 호출해도(참여·에스크로 생성 실패 시 롤백되어) 원자적이다. 모집 중이 아니면
        // (외부 승인 성공 후 판매철회/모집종료로 공구가 종료된 경합) 예외로 트랜잭션을 오염시키지
        // 않고 notRecruiting 결과를 돌려준다.
        GroupBuySlotPort.ConfirmOutcome outcome = groupBuySlotPort.confirmForCompletion(payment.getGroupBuyId());
        if (outcome.notRecruiting()) {
            if (!scheduleRefundOnTerminated) {
                // 코인/레거시 경로는 기존 동작(예외 롤백)을 유지한다.
                throw new BusinessException(ErrorCode.GROUP_BUY_NOT_RECRUITING);
            }
            // 외부 청구는 성공했으나 확정 불가 - 결과 불명이 아니라 환불이 필요한 건으로 기록한다.
            // 참여·에스크로는 만들지 않고, 종료된 공구가 이미 슬롯을 회수했으므로 슬롯 해제도 하지 않는다.
            payment.markRefundRequired("GROUP_BUY_TERMINATED_AFTER_CHARGE");
            return PaymentResponse.from(payment, outcome.slot(), LocalDateTime.now(clock));
        }
        GroupBuySlotPort.GroupBuySlot groupBuySlot = outcome.slot();

        ParticipationWriterPort.ParticipationRecord participation = participationWriterPort.create(
                new ParticipationWriterPort.ParticipationCreateCommand(
                        payment.getGroupBuyId(), payment.getUserId(),
                        groupBuySlot.unitShareAmount(), groupBuySlot.shippingFee(), payment.getKrwAmount(),
                        snapshot.getRecipientName(), snapshot.getRecipientPhone(), snapshot.getZipcode(),
                        snapshot.getAddress1(), snapshot.getAddress2(), snapshot.getDeliveryRequest()));

        // 보관 시각도 Clock 에서 가져온다. 엔티티가 LocalDateTime.now() 를 직접 부르면
        // 테스트에서 시각을 고정할 수 없고, DB 에 UTC/KST 가 섞인다.
        escrowWriterPort.hold(participation.participationId(), payment.getGroupBuyId(),
                payment.getKrwAmount(), LocalDateTime.now(clock));

        payment.markPaid(participation.participationId(), LocalDateTime.now(clock));
        return PaymentResponse.from(payment, groupBuySlot, LocalDateTime.now(clock));
    }

    /**
     * 결제수단 어댑터가 공통 결제 내부 엔티티나 주소 저장소를 직접 참조하지 않고
     * 외부 승인에 필요한 불변 값만 읽는 경계다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ApprovalContext getApprovalContext(Long userId, Long paymentId, String expectedMethod) {
        Payment payment = paymentRepository.findForUpdateByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        validateApprovalCandidate(payment, expectedMethod);
        // 결제 TTL이 남아 있어도 이미 종료된 공구에 새 외부 승인/견적을 시작하지 않는다.
        // 모집 종료 시각만 지난 RECRUITING 공구의 기존 유효 슬롯은 현재 정책대로 유지한다.
        if (!"RECRUITING".equals(groupBuySlotPort.get(payment.getGroupBuyId()).status())) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_RECRUITING);
        }
        ensureShippingSnapshot(payment);
        return new ApprovalContext(
                payment.getPaymentId(), payment.getGroupBuyId(), payment.getUserId(),
                payment.getKrwAmount(), payment.getSlotExpiresAt());
    }

    /** 승인 응답 유실 후 같은 요청을 재시도할 때 외부 결제를 다시 호출하지 않고 결과를 돌려준다. */
    public PaymentResponse getCompletedPayment(Long userId, Long paymentId, String expectedMethod) {
        Payment payment = findOwnedPayment(userId, paymentId);
        if (!expectedMethod.equals(payment.getMethod())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "요청한 결제수단과 결제 건이 일치하지 않습니다.");
        }
        if (!Payment.STATUS_PAID.equals(payment.getStatus())) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        return PaymentResponse.from(payment, groupBuySlotPort.get(payment.getGroupBuyId()), LocalDateTime.now(clock));
    }

    /**
     * 외부 승인 검증이 끝난 뒤 참여·에스크로·결제를 하나의 짧은 트랜잭션으로 확정한다.
     * 호출한 결제수단 서비스의 트랜잭션이 있으면 그 트랜잭션에 참여한다.
     */
    @Transactional
    public PaymentResponse completeOwnedPayment(Long userId, Long paymentId, String expectedMethod) {
        Payment payment = paymentRepository.findForUpdateByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        validateApprovalCandidate(payment, expectedMethod);
        return completePayment(paymentId);
    }

    /**
     * 소유권을 검증한 뒤 {@link #completeOrScheduleRefund}로 확정한다. 카카오페이처럼 승인
     * 상세 기록과 내부 확정을 한 트랜잭션에서 처리하는 경로가, 공구 종료 경합에서도 예외로
     * 상세 기록까지 롤백되지 않도록 환불 예약 결과를 돌려받는다.
     */
    @Transactional
    public PaymentResponse completeOwnedPaymentOrScheduleRefund(Long userId, Long paymentId, String expectedMethod) {
        Payment payment = paymentRepository.findForUpdateByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        validateApprovalCandidate(payment, expectedMethod);
        return completeOrScheduleRefund(paymentId);
    }

    /** 외부 호출 전의 짧은 트랜잭션에서만 레거시 결제 주소를 보정한다. */
    private void ensureShippingSnapshot(Payment payment) {
        if (payment.getShippingSnapshot() != null) return;
        UserAddress address = payment.getAddressId() == null ? null : userAddressRepository
                .findByAddressIdAndUserId(payment.getAddressId(), payment.getUserId()).orElse(null);
        if (address == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "기존 결제의 배송지를 확인할 수 없습니다. 결제를 종료하고 배송지를 다시 선택해 주세요.");
        payment.captureShipping(new PaymentShippingSnapshot(address.getRecipientName(), address.getRecipientPhone(),
                address.getZipcode(), address.getAddress1(), address.getAddress2(), address.getDeliveryRequest()), null);
    }

    private PaymentShippingSnapshot storedSnapshot(Payment payment) {
        PaymentShippingSnapshot snapshot = payment.getShippingSnapshot();
        if (snapshot == null) throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                "외부 결제 전 배송지 확정이 누락되었습니다. 거래 결과 대조가 필요합니다.");
        return snapshot;
    }

    private void validateApprovalCandidate(Payment payment, String expectedMethod) {
        if (!expectedMethod.equals(payment.getMethod())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "요청한 결제수단과 결제 건이 일치하지 않습니다.");
        }
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        if (payment.isExpired(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        }
    }

    private Payment findOwnedPayment(Long userId, Long paymentId) {
        return paymentRepository.findByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
    }

    @Transactional
    public void abortPayment(Long userId, Long paymentId, String expectedMethod, String reason) {
        Payment payment = paymentRepository.findForUpdateByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        if (!expectedMethod.equals(payment.getMethod())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "요청한 결제수단과 결제 건이 일치하지 않습니다.");
        }
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        rejectUnresolvedCardApproval(payment);
        groupBuySlotPort.release(payment.getGroupBuyId());
        groupBuySlotGate.release(payment.getGroupBuyId());
        payment.markFailed(reason);
    }

    private void rejectUnresolvedCardApproval(Payment payment) {
        if (payment.hasUnresolvedCardApproval()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "카드 승인 중이거나 결과 확인이 필요한 결제는 슬롯을 해제할 수 없습니다.");
        }
    }

    public record ApprovalContext(
            Long paymentId,
            Long groupBuyId,
            Long userId,
            long amount,
            LocalDateTime expiresAt
    ) {
    }
}
