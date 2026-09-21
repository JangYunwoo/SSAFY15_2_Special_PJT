package com.gongguhan.domain.refund.service;

import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.entity.RefundMethod;
import com.gongguhan.domain.refund.entity.RefundReason;
import com.gongguhan.domain.refund.entity.RefundStatus;
import com.gongguhan.domain.refund.repository.RefundRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 원장의 경계 (FR-RFD-01~10).
 *
 * <p><b>환불을 일으키는 도메인이 여러 개라서 이 경계가 필요합니다.</b> 모집 실패는
 * 공동구매, 참여 취소는 참여, 반품은 반품, 미매칭 입금은 코인 결제가 일으킵니다.
 * 각자 {@code Refund} 엔티티와 저장소를 직접 다루면 상태 전이 규칙과 중복 처리
 * 규칙이 네 곳으로 흩어지고, 그중 하나가 빠뜨립니다. 호출 측은 이 클래스만 봅니다.
 *
 * <p><b>이 클래스는 돈을 보내지 않습니다.</b> "돌려주기로 했다" 는 결정을 기록하고,
 * 외부 송금·이체 결과를 받아 적기만 합니다. 실제 송금은 결제수단별 어댑터
 * ({@code RefundPort} 구현체)가 합니다. 원장과 송금을 분리하는 이유는, 송금이
 * 실패해도 결정은 남아 재처리할 수 있어야 하기 때문입니다.
 *
 * <p><b>트랜잭션은 짧게 유지합니다.</b> 외부 호출은 이 클래스 밖에서 하고, 여기서는
 * DB 쓰기만 합니다. 체인 송금은 수십 초가 걸릴 수 있어 트랜잭션 안에서 부르면
 * 커넥션과 잠금이 그만큼 묶입니다. 코인 결제 검증에서 쓴 것과 같은 구조입니다.
 */
@Service
public class RefundLedgerService {

    private static final Logger log = LoggerFactory.getLogger(RefundLedgerService.class);

    private final RefundRepository refundRepository;
    private final Clock clock;

    public RefundLedgerService(RefundRepository refundRepository, Clock clock) {
        this.refundRepository = refundRepository;
        this.clock = clock;
    }

    /**
     * 참여 건 환불을 개시합니다. 모집 실패·참여 취소·판매자 귀책이 이 경로입니다.
     *
     * <p>같은 참여 건에 이미 진행 중이거나 완료된 환불이 있으면 새로 만들지 않고
     * <b>그 환불을 그대로 돌려줍니다.</b> 배치 재실행이나 버튼 중복 클릭으로 같은
     * 요청이 두 번 와도 두 번 돈이 나가지 않아야 하고, 그렇다고 예외를 던지면
     * 호출 측이 정상 재시도와 진짜 오류를 구분할 수 없습니다.
     *
     * @throws BusinessException {@code INVALID_STATE_TRANSITION} — 동시 요청이 DB
     *         유일성 제약에 걸린 경우. 재시도하면 위의 멱등 경로를 타게 됩니다.
     */
    @Transactional
    public OpenResult openForParticipation(Long participationId, long amount,
                                           RefundReason reasonType, RefundMethod refundMethod) {
        return refundRepository.findActiveByParticipationId(participationId)
                .map(existing -> {
                    log.info("이미 처리 중인 환불이 있어 새로 만들지 않습니다. refundId={} participationId={}",
                            existing.getRefundId(), participationId);
                    return OpenResult.existing(existing);
                })
                .orElseGet(() -> OpenResult.created(insert(Refund.forParticipation(
                        participationId, amount, reasonType, refundMethod, now()))));
    }

    /** 반품 환불을 개시합니다 (FR-RTN-17). 반품 ID 를 남겨 환불액 근거를 되짚게 합니다. */
    @Transactional
    public OpenResult openForReturn(Long returnId, Long participationId, long amount,
                                    RefundMethod refundMethod) {
        return refundRepository.findActiveByParticipationId(participationId)
                .map(existing -> {
                    log.info("이미 처리 중인 환불이 있어 새로 만들지 않습니다. refundId={} returnId={}",
                            existing.getRefundId(), returnId);
                    return OpenResult.existing(existing);
                })
                .orElseGet(() -> OpenResult.created(insert(Refund.forReturn(
                        returnId, participationId, amount, refundMethod, now()))));
    }

    /**
     * 결제에 붙이지 못한 입금 환불을 개시합니다 (FR-PAY-26).
     *
     * <p>참여 건이 없으므로 입금 단위로 중복을 막습니다. 같은 입금을 두 번
     * 돌려보내면 우리 지갑에서 토큰이 두 번 나갑니다.
     */
    @Transactional
    public OpenResult openForUnmatchedDeposit(Long depositId, long amount, String toAddress) {
        return openForDeposit(depositId, () -> Refund.forUnmatchedDeposit(
                depositId, amount, toAddress, now()));
    }

    /** 슬롯이 만료된 뒤 도착한 입금 환불을 개시합니다 (FR-SLT-10). */
    @Transactional
    public OpenResult openForExpiredSlot(Long depositId, long amount, String toAddress) {
        return openForDeposit(depositId, () -> Refund.forExpiredSlot(
                depositId, amount, toAddress, now()));
    }

    /**
     * 계좌이체 환불의 수취 계좌를 확정합니다.
     *
     * <p><b>이체를 보내기 전에</b> 기록합니다. 송금 직후 장애가 나면 "어디로 보냈는지"
     * 가 원장에 남아 있어야 금융망 거래내역과 대조할 수 있습니다.
     */
    @Transactional
    public Refund assignAccount(Long refundId, String accountNo) {
        Refund refund = load(refundId);
        refund.assignAccount(accountNo);
        return refund;
    }

    /**
     * 코인 환불 수취 주소를 확정합니다.
     *
     * <p><b>송금 전에</b> 기록합니다. 송금 직후 장애가 나면 "어디로 보냈는지" 가 원장에
     * 남아 있어야 체인에서 대조할 수 있습니다.
     */
    @Transactional
    public Refund assignWalletAddress(Long refundId, String address) {
        Refund refund = load(refundId);
        refund.assignWalletAddress(address);
        return refund;
    }

    /**
     * 카드 취소 불가로 계좌이체 환불로 전환합니다.
     *
     * <p>{@code CardRefundPort} 가 부분 환불·청구서 발행으로 {@code REQUIRES_TRANSFER_FALLBACK}
     * 를 돌려줬을 때만 부릅니다. 수단만 바꾸고, 실제 이체는 오케스트레이션이 이어서 합니다.
     */
    @Transactional
    public Refund fallbackToAccountTransfer(Long refundId) {
        Refund refund = load(refundId);
        refund.fallbackToAccountTransfer();
        log.info("카드 취소 불가로 계좌이체 환불로 전환합니다. refundId={}", refundId);
        return refund;
    }

    /** 카드 승인 취소 결과를 기록합니다. */
    @Transactional
    public Refund completeByCardCancel(Long refundId, Long ssafyTransactionNo) {
        Refund refund = load(refundId);
        refund.completeByCardCancel(ssafyTransactionNo, now());
        return refund;
    }

    /** 계좌 이체 결과를 기록합니다. */
    @Transactional
    public Refund completeByAccountTransfer(Long refundId, String institutionTxNo,
                                            Long ssafyTransactionNo) {
        Refund refund = load(refundId);
        refund.completeByAccountTransfer(institutionTxNo, ssafyTransactionNo, now());
        return refund;
    }

    /** 카카오페이 취소 결과를 기록합니다. */
    @Transactional
    public Refund completeByKakaoPay(Long refundId, String cancelAid) {
        Refund refund = load(refundId);
        refund.completeByKakaoPay(cancelAid, now());
        return refund;
    }

    /**
     * 온체인 송금 결과를 기록합니다.
     *
     * <p>{@code txHash} 가 이미 다른 환불에 기록돼 있으면 거부합니다. 같은 송금을 두
     * 건의 환불로 세면 장부상 두 번 돌려준 것이 되고, 실제로는 한 번만 나갔으므로
     * 차액이 영구히 어긋납니다.
     */
    @Transactional
    public Refund completeByCrypto(Long refundId, String txHash, BigDecimal assetAmount) {
        refundRepository.findByTxHash(txHash)
                .filter(other -> !other.getRefundId().equals(refundId))
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                            "이미 다른 환불에 기록된 트랜잭션입니다. txHash=" + txHash
                                    + " refundId=" + other.getRefundId());
                });

        Refund refund = load(refundId);
        refund.completeByCrypto(txHash, assetAmount, now());
        return refund;
    }

    /**
     * 환불 실패를 기록합니다 (FR-RFD-09).
     *
     * <p>이미 완료된 건은 실패로 되돌리지 않습니다. 돈이 나간 뒤에 실패로 표시하면
     * 재처리 대상이 되어 이중 지급으로 이어집니다.
     */
    @Transactional
    public Refund fail(Long refundId, String reason) {
        Refund refund = load(refundId);
        refund.markFailed(reason, now());
        log.warn("환불 실패 refundId={} reason={}", refundId, reason);
        return refund;
    }

    /**
     * 실패한 환불을 재처리 대상으로 되돌립니다 (RFD-04, FR-ADM-08).
     *
     * <p>상태만 바꿉니다. 실제 재송금은 호출 측이 결제수단 어댑터로 다시 시도합니다.
     */
    @Transactional
    public Refund retry(Long refundId) {
        Refund refund = locked(refundId);
        try {
            refund.retry();
            refundRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            // 실패한 사이에 같은 참여 건으로 다른 환불이 만들어진 경우다.
            // 두 건을 동시에 진행시키는 대신 막고 사람이 보게 한다.
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "같은 대상에 다른 환불이 진행 중이라 재처리할 수 없습니다. refundId=" + refundId);
        }
        return refund;
    }

    @Transactional
    public void claimExecution(Long refundId, String correlationId) {
        locked(refundId).claimExecution(correlationId);
    }

    /** 복구 스캔과 최초 요청이 경쟁해도 패자는 진행 중인 원장을 FAILED로 덮지 않는다. */
    @Transactional
    public boolean tryClaimExecution(Long refundId, String correlationId) {
        Refund refund = locked(refundId);
        if (!refund.isProcessing() || refund.isExecutionClaimed()) { return false; }
        refund.claimExecution(correlationId);
        return true;
    }

    @Transactional(readOnly = true)
    public Refund get(Long refundId) { return load(refundId); }

    @Transactional
    public void recordInstitutionTransaction(Long refundId, String correlationId) {
        locked(refundId).recordInstitutionTransaction(correlationId);
    }

    @Transactional
    public void releaseUnsentOrRejectedExecution(Long refundId) {
        locked(refundId).releaseUnsentOrRejectedExecution();
    }

    /** 확실한 거절만 실행권 해제와 실패 기록을 한 번에 커밋한다. */
    @Transactional
    public Refund failRejected(Long refundId, String reason) {
        Refund refund = locked(refundId);
        refund.releaseUnsentOrRejectedExecution();
        refund.markFailed(reason, now());
        return refund;
    }

    private Refund locked(Long refundId) {
        return refundRepository.findForUpdate(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 재처리 배치가 집어갈 실패 건을 오래된 순서로 가져옵니다. */
    @Transactional(readOnly = true)
    public List<Refund> findFailed(int limit) {
        return refundRepository.findByStatusOrderByCreatedAtAsc(
                RefundStatus.FAILED, PageRequest.of(0, limit));
    }

    private OpenResult openForDeposit(Long depositId, Supplier<Refund> factory) {
        return refundRepository.findActiveByDepositId(depositId)
                .map(existing -> {
                    log.info("이미 처리 중인 입금 환불이 있어 새로 만들지 않습니다. refundId={} depositId={}",
                            existing.getRefundId(), depositId);
                    return OpenResult.existing(existing);
                })
                .orElseGet(() -> OpenResult.created(insert(factory.get())));
    }

    /**
     * 환불 개시 결과.
     *
     * <p><b>{@code created} 를 돌려주는 이유는 이중 송금을 막기 위해서입니다.</b>
     * 개시가 멱등이라 같은 요청이 두 번 오면 기존 행이 그대로 돌아오는데, 호출 측이
     * 그걸 구분하지 못하면 <b>같은 환불에 대해 외부 송금을 두 번</b> 실행합니다.
     * 원장은 한 건인데 돈은 두 번 나가는, 가장 알아채기 어려운 형태의 사고입니다.
     *
     * <p>동시에 들어온 두 요청 중 하나만 INSERT 에 성공하므로
     * ({@code UNIQUE(participation_id, refund_marker)}), {@code created} 가 true 인
     * 호출자도 정확히 하나입니다. 실제 송금은 그 하나만 수행하면 됩니다.
     *
     * @param refund  개시됐거나 이미 존재하던 환불
     * @param created 이 호출이 행을 새로 만들었으면 true
     */
    public record OpenResult(Refund refund, boolean created) {

        static OpenResult created(Refund refund) {
            return new OpenResult(refund, true);
        }

        static OpenResult existing(Refund refund) {
            return new OpenResult(refund, false);
        }
    }

    /**
     * 새 환불 행을 넣습니다.
     *
     * <p>{@code refund_id} 는 자동 생성이라 {@code save()} 가 {@code persist()} 를
     * 호출하고, IDENTITY 키를 받아오기 위해 INSERT 가 즉시 나갑니다. 그래서 유일성
     * 위반이 이 자리에서 드러납니다. 조회로 먼저 걸러도 두 요청이 동시에 통과할 수
     * 있으므로, 경쟁의 최종 방어선은 DB 제약입니다.
     */
    private Refund insert(Refund refund) {
        try {
            return refundRepository.save(refund);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "같은 대상에 이미 진행 중인 환불이 있습니다.");
        }
    }

    private Refund load(Long refundId) {
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "환불 건을 찾을 수 없습니다. refundId=" + refundId));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
