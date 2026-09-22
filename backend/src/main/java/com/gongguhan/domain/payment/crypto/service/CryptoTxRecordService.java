package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.common.service.PaymentService;
import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoDeposit;
import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import com.gongguhan.domain.payment.crypto.entity.CryptoQuote;
import com.gongguhan.domain.payment.crypto.entity.CryptoWallet;
import com.gongguhan.domain.payment.crypto.repository.CryptoDepositRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoPaymentRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoQuoteRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코인 결제의 DB 쓰기 전담.
 *
 * <p>{@link CryptoTxVerificationService}가 체인 호출(느린 I/O)을 트랜잭션 밖에서
 * 끝낸 뒤, 그 결과를 짧은 트랜잭션으로 반영하기 위해 분리했다.
 * (API 명세서 5장 원자성 규칙 5·6: 외부 호출은 트랜잭션 밖, 확정은 짧은 트랜잭션)
 *
 * <p>같은 클래스 안에서 나눠 두면 자기 호출이 프록시를 우회해 트랜잭션 경계가
 * 생기지 않으므로 별도 빈으로 둔다.
 */
@Service
public class CryptoTxRecordService {

    private static final Logger log = LoggerFactory.getLogger(CryptoTxRecordService.class);

    private final CryptoPaymentRepository cryptoPaymentRepository;
    private final CryptoDepositRepository cryptoDepositRepository;
    private final CryptoDepositRecorder depositRecorder;
    private final CryptoQuoteRepository cryptoQuoteRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    public CryptoTxRecordService(CryptoPaymentRepository cryptoPaymentRepository,
                                  CryptoDepositRepository cryptoDepositRepository,
                                  CryptoDepositRecorder depositRecorder,
                                  CryptoQuoteRepository cryptoQuoteRepository,
                                  PaymentRepository paymentRepository,
                                  PaymentService paymentService,
                                  Clock clock) {
        this.cryptoPaymentRepository = cryptoPaymentRepository;
        this.cryptoDepositRepository = cryptoDepositRepository;
        this.depositRecorder = depositRecorder;
        this.cryptoQuoteRepository = cryptoQuoteRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    /**
     * 검증을 통과한 트랜잭션을 기록한다 (CRYP-03).
     *
     * <p>순서가 중요하다. <b>Quote claim 을 가장 먼저</b> 한다. 뒤로 미루면 두 요청이
     * 같은 견적으로 각각 {@code crypto_payments} 를 만든 뒤 하나만 claim 에 성공해,
     * 실패한 쪽의 행을 되돌려야 한다.
     *
     * @param paymentId 결제 건 ID. 엔티티가 아니라 ID 를 받는 이유는, 호출 측이
     *                  트랜잭션 밖에서 읽은 detached 엔티티를 변경해도 반영되지
     *                  않기 때문이다. 이 트랜잭션 안에서 직접 다시 읽는다.
     * @param receipt 체인에서 읽어온 영수증. null 이면 노드가 아직 트랜잭션을 모르는
     *                상태이므로 {@code receipt_status} 를 비워 두고 폴링에 넘긴다.
     */
    @Transactional
    public CryptoPayment record(Long paymentId, CryptoQuote quote, CryptoAsset asset,
                                 CryptoWallet wallet, String fromAddress, String txHash,
                                 ChainClient.ReceiptView receipt, long latestBlock) {

        LocalDateTime now = LocalDateTime.now(clock);

        Payment payment = paymentRepository.findForUpdateByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "결제를 찾을 수 없습니다."));
        // 검증과 기록 사이에 슬롯 만료 배치나 사용자 이탈이 끼어들 수 있다.
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }

        // 배정된 동시성 과제: Quote 중복 사용 방지.
        // used=false AND expires_at>now 를 UPDATE 의 WHERE 에 넣어 DB 가 한 번만
        // 성공시킨다. 조회 후 검사 방식은 두 요청이 함께 통과한다.
        if (cryptoQuoteRepository.claim(quote.getQuoteId(), now) != 1) {
            throw new BusinessException(ErrorCode.QUOTE_EXPIRED,
                    "이미 사용되었거나 만료된 견적입니다. 견적을 다시 발급받아 주세요.");
        }

        CryptoPayment submitted = CryptoPayment.submit(
                paymentId, asset, quote, wallet, fromAddress, txHash);

        // save() 의 반환값을 반드시 받아서 써야 한다.
        //
        // crypto_payments 는 payment_id 를 직접 지정하는 엔티티다(@GeneratedValue 없음).
        // 그래서 Spring Data 의 isNew() 가 false 로 판단해 persist() 가 아니라 merge() 를
        // 호출하고, merge() 는 "새로운 관리 객체"를 반환하며 넘긴 인스턴스는 detached 로
        // 남긴다. 원본을 계속 쓰면 이후 변경(컨펌 수·확정 시각)이 DB 에 반영되지 않는다.
        //
        // 실제로 이 버그로 confirmations 가 0, confirmed_at 이 NULL 로 남았다.
        // API 응답은 메모리 객체를 읽어 정상으로 보였기 때문에 단위 테스트로는 잡히지 않았다.
        CryptoPayment cryptoPayment = cryptoPaymentRepository.save(submitted);

        if (receipt == null) {
            // 노드가 아직 모른다. 실패가 아니므로 폴링이 이어받는다.
            log.info("영수증 미확인 상태로 등록 payment={} txHash={}", paymentId, txHash);
            return cryptoPayment;
        }

        cryptoPayment.applyReceipt(receipt.success(), receipt.blockNumber());

        if (!receipt.success()) {
            // 트랜잭션이 revert 됐다. 자금이 이동하지 않았으므로 입금 기록도 없다.
            // 여기서 save() 를 다시 부르지 않는다. cryptoPayment 는 이미 관리 객체이므로
            // 변경이 커밋 시점에 자동으로 반영된다.
            paymentService.failPayment(paymentId, "TX_REVERTED");
            log.info("트랜잭션 revert payment={} txHash={}", paymentId, txHash);
            return cryptoPayment;
        }

        ChainClient.TransferLog transfer = requireMatchingTransfer(cryptoPayment, receipt);
        recordDeposit(cryptoPayment, transfer, receipt, latestBlock, now);

        cryptoPayment.updateConfirmations(
                ChainClient.calculateConfirmations(latestBlock, receipt.blockNumber()));

        if (payment.isExpired(now)) {
            paymentService.failPayment(paymentId, "SLOT_EXPIRED_BEFORE_CONFIRMATION");
            classifyTerminalDeposits(paymentId, now, false);
        } else if (cryptoPayment.isConfirmed()) {
            completePayment(payment, cryptoPayment, now);
        }
        return cryptoPayment;
    }

    /**
     * 컨펌 수를 갱신하고, 충족되면 결제를 확정한다 (CRYP-04 폴링).
     *
     * @return 이 호출로 결제가 확정된 경우 true
     */
    /**
     * 입금을 환불 완료로 표시합니다 (FR-PAY-26).
     *
     * <p>온체인 송금은 호출 측이 트랜잭션 밖에서 끝낸 뒤 이 짧은 트랜잭션으로 기록만
     * 합니다 - 이 클래스의 다른 메서드와 같은 이유입니다.
     */
    @Transactional
    public void markDepositRefunded(Long depositId) {
        CryptoDeposit deposit = cryptoDepositRepository.findForUpdate(depositId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "입금 건을 찾을 수 없습니다. depositId=" + depositId));
        deposit.markRefunded();
        cryptoDepositRepository.save(deposit);
    }

    @Transactional
    public boolean refreshConfirmations(Long paymentId, long latestBlock) {
        Payment payment = paymentRepository.findForUpdateByPaymentId(paymentId).orElse(null);
        CryptoPayment cryptoPayment = cryptoPaymentRepository.findById(paymentId).orElse(null);
        if (payment == null || cryptoPayment == null || cryptoPayment.getBlockNumber() == null
                || !CryptoPayment.RECEIPT_SUCCESS.equals(cryptoPayment.getReceiptStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        cryptoPayment.updateConfirmations(
                ChainClient.calculateConfirmations(latestBlock, cryptoPayment.getBlockNumber()));

        if (!payment.isProcessing()) {
            if (!Payment.STATUS_PAID.equals(payment.getStatus())) {
                classifyTerminalDeposits(paymentId, now, true);
            }
            return false;
        }
        if (payment.isExpired(now)) {
            paymentService.failPayment(paymentId, "SLOT_EXPIRED_BEFORE_CONFIRMATION");
            classifyTerminalDeposits(paymentId, now, true);
            return false;
        }
        if (!cryptoPayment.isConfirmed()) { return false; }
        completePayment(payment, cryptoPayment, now);
        return true;
    }

    /** 종료 결제의 이전 MATCHED 입금은 반환 후보로 옮기되 슬롯·참여를 되살리지 않는다. */
    private void classifyTerminalDeposits(Long paymentId, LocalDateTime now, boolean needsFreshReceipt) {
        for (CryptoDeposit candidate : cryptoDepositRepository.findByPaymentId(paymentId)) {
            CryptoDeposit deposit = cryptoDepositRepository.findForUpdate(candidate.getDepositId()).orElseThrow();
            if (CryptoDeposit.STATUS_MATCHED.equals(deposit.getStatus())) {
                deposit.markUnmatched("결제 종료 후 확인된 입금: 관리자 원금 반환 대상", now);
                // 과거 영수증+현재 블록 높이만으로 환불 확정을 추정하지 않는다.
                if (needsFreshReceipt) { deposit.updateConfirmations(0); }
            }
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<CryptoDeposit> findUnconfirmedDeposits(long chainId, org.springframework.data.domain.Pageable page) {
        return cryptoDepositRepository.findUnconfirmedRefundCandidates(chainId, page);
    }

    /** 외부에서 새로 읽은 영수증을 원본 로그와 대조한 뒤 반환용 컨펌을 기록한다. */
    @Transactional
    public void refreshDepositConfirmations(Long depositId, ChainClient.ReceiptView receipt, long latestBlock) {
        CryptoDeposit deposit = cryptoDepositRepository.findForUpdate(depositId).orElse(null);
        if (deposit == null || !java.util.List.of(CryptoDeposit.STATUS_UNMATCHED,
                CryptoDeposit.STATUS_UNDERPAID, CryptoDeposit.STATUS_OVERPAID).contains(deposit.getStatus())) {
            return;
        }
        boolean valid = receipt != null && receipt.success() && receipt.transfers().stream().anyMatch(t ->
                t.logIndex() == deposit.getLogIndex()
                        && t.tokenContract().equalsIgnoreCase(deposit.getTokenContract())
                        && t.from().equalsIgnoreCase(deposit.getFromAddress())
                        && t.to().equalsIgnoreCase(deposit.getToAddress())
                        && t.rawAmount().equals(deposit.rawAmountValue()));
        if (!valid) { deposit.updateConfirmations(0); return; }
        deposit.observeConfirmedReceipt(receipt.blockNumber(),
                ChainClient.calculateConfirmations(latestBlock, receipt.blockNumber()));
    }

    /** 영수증을 뒤늦게 확인한 건에 결과를 반영한다 (폴링). */
    @Transactional
    public void applyLateReceipt(Long paymentId, ChainClient.ReceiptView receipt, long latestBlock) {
        Payment payment = paymentRepository.findForUpdateByPaymentId(paymentId).orElse(null);
        CryptoPayment cryptoPayment = cryptoPaymentRepository.findById(paymentId).orElse(null);
        if (payment == null || cryptoPayment == null || cryptoPayment.getReceiptStatus() != null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        cryptoPayment.applyReceipt(receipt.success(), receipt.blockNumber());

        if (!receipt.success()) {
            if (payment.isProcessing()) { paymentService.failPayment(paymentId, "TX_REVERTED"); }
            return;
        }

        try {
            ChainClient.TransferLog transfer = requireMatchingTransfer(cryptoPayment, receipt);
            recordDeposit(cryptoPayment, transfer, receipt, latestBlock, now);
        } catch (BusinessException e) {
            // 폴링 경로에서는 예외를 던져도 받을 사용자가 없다. 결제를 실패로
            // 확정하고 사유를 남겨 관리자가 확인하게 한다.
            if (payment.isProcessing()) {
                paymentService.failPayment(paymentId, "TX_VERIFICATION_FAILED: " + e.getMessage());
            }
            log.warn("폴링 중 트랜잭션 검증 실패 payment={}", paymentId, e);
            return;
        }

        cryptoPayment.updateConfirmations(
                ChainClient.calculateConfirmations(latestBlock, receipt.blockNumber()));
        if (!payment.isProcessing() || payment.isExpired(now)) {
            if (payment.isProcessing()) { paymentService.failPayment(paymentId, "SLOT_EXPIRED_BEFORE_CONFIRMATION"); }
            if (!Payment.STATUS_PAID.equals(payment.getStatus())) { classifyTerminalDeposits(paymentId, now, false); }
        } else if (cryptoPayment.isConfirmed()) {
            completePayment(payment, cryptoPayment, now);
        }
    }

    // ------------------------------------------------------------------
    // 내부
    // ------------------------------------------------------------------

    /**
     * 영수증의 Transfer 중 우리 결제에 해당하는 것을 찾는다.
     *
     * <p>검증 5가지 중 4가지가 여기서 걸러진다 — 수취 주소, 토큰 컨트랙트, 금액,
     * 송신 주소. (남은 하나는 revert 여부)
     *
     * <p><b>금액은 uint256 원본값 문자열로 비교한다.</b> {@code BigDecimal} 로 바꿔
     * 비교하면 소수 자릿수 처리에서 오차가 끼어들 여지가 생기고, 그 오차가 곧
     * 미달 입금을 정상으로 통과시킨다.
     */
    private ChainClient.TransferLog requireMatchingTransfer(CryptoPayment cryptoPayment,
                                                             ChainClient.ReceiptView receipt) {
        BigInteger expected = cryptoPayment.expectedRawAmount();

        // 1차: 수취 주소 + 토큰이 우리 것인 Transfer 만 남긴다.
        var toUs = receipt.transfers().stream()
                .filter(t -> t.to().equalsIgnoreCase(cryptoPayment.getToAddress()))
                .filter(t -> t.tokenContract().equalsIgnoreCase(cryptoPayment.getTokenContract()))
                .toList();

        if (toUs.isEmpty()) {
            throw new BusinessException(ErrorCode.CRYPTO_AMOUNT_MISMATCH,
                    "이 트랜잭션에는 지정된 수취 주소로의 입금이 없습니다.");
        }

        // 2차: 송신 주소가 신고한 주소와 같아야 한다. 다르면 남의 트랜잭션을
        // 가져다 붙인 것일 수 있다.
        var fromBuyer = toUs.stream()
                .filter(t -> t.from().equalsIgnoreCase(cryptoPayment.getFromAddress()))
                .toList();

        if (fromBuyer.isEmpty()) {
            throw new BusinessException(ErrorCode.CRYPTO_AMOUNT_MISMATCH,
                    "신고한 지갑 주소에서 보낸 입금이 아닙니다.");
        }

        // 3차: 금액이 정확히 일치하는 건을 우선 찾는다.
        Optional<ChainClient.TransferLog> exact = fromBuyer.stream()
                .filter(t -> t.rawAmount().compareTo(expected) == 0)
                .findFirst();
        if (exact.isPresent()) {
            return exact.get();
        }

        // 정확히 맞는 게 없으면 미달·초과다. 어느 쪽인지 사유에 담아 되돌린다.
        //
        // 거부하기 전에 입금을 남긴다. 이 메서드는 @Transactional 안이라 예외를 던지면
        // 롤백되므로, 같은 트랜잭션에 기록하면 사라진다. 그러면 돈은 우리 지갑에 있는데
        // DB 에 흔적이 없어 누구에게 돌려줄지 알 수 없다(FR-PAY-26). 별도 빈의
        // REQUIRES_NEW 트랜잭션이라 이 롤백에 휩쓸리지 않는다.
        ChainClient.TransferLog closest = fromBuyer.get(0);
        boolean underpaid = closest.rawAmount().compareTo(expected) < 0;
        depositRecorder.recordMismatched(cryptoPayment, closest, receipt.blockNumber(),
                underpaid ? CryptoDeposit.STATUS_UNDERPAID : CryptoDeposit.STATUS_OVERPAID);

        throw new BusinessException(ErrorCode.CRYPTO_AMOUNT_MISMATCH,
                underpaid
                        ? "입금액이 견적보다 적습니다. 견적 금액 그대로 송금해야 합니다."
                        : "입금액이 견적과 다릅니다. 견적 금액 그대로 송금해야 합니다.");
    }

    /**
     * 입금을 기록한다.
     *
     * <p>{@code UNIQUE(tx_hash, log_index)} 가 최종 방어선이다. 여기서 미리 조회하는
     * 것은 재요청·폴링 중복 같은 흔한 경우를 예외 없이 넘기기 위함이다.
     */
    private void recordDeposit(CryptoPayment cryptoPayment, ChainClient.TransferLog transfer,
                                ChainClient.ReceiptView receipt, long latestBlock,
                                LocalDateTime now) {
        CryptoDeposit existing = cryptoDepositRepository.findForUpdateByTxHashAndLogIndex(
                cryptoPayment.getTxHash(), transfer.logIndex()).orElse(null);
        if (existing != null) {
            // UNIQUE는 중복 행 삽입만 막는다. 이미 반환 대상인 입금을 읽고 그냥 통과하면
            // 새 결제의 참여/에스크로가 만들어지므로 상태와 원래 매칭 소유권도 검증한다.
            boolean sameMatch = CryptoDeposit.STATUS_MATCHED.equals(existing.getStatus())
                    && cryptoPayment.getPaymentId().equals(existing.getPaymentId())
                    && existing.getChainId() == cryptoPayment.getChainId()
                    && existing.getTokenContract().equalsIgnoreCase(transfer.tokenContract())
                    && existing.getFromAddress().equalsIgnoreCase(transfer.from())
                    && existing.getToAddress().equalsIgnoreCase(transfer.to())
                    && existing.rawAmountValue().equals(transfer.rawAmount());
            if (!sameMatch) {
                throw new BusinessException(ErrorCode.CRYPTO_AMOUNT_MISMATCH,
                        "이미 기록된 입금은 다른 결제에 재사용할 수 없습니다. 기존 입금 상태를 확인해 주세요.");
            }
            return;
        }

        CryptoDeposit deposit = CryptoDeposit.detected(
                cryptoPayment.getChainId(),
                cryptoPayment.getTxHash(),
                transfer.logIndex(),
                receipt.blockNumber(),
                transfer.tokenContract(),
                transfer.from(),
                transfer.to(),
                transfer.rawAmount(),
                cryptoPayment.getAssetAmount(),
                ChainClient.calculateConfirmations(latestBlock, receipt.blockNumber()),
                now);

        deposit.matchTo(cryptoPayment.getPaymentId(), CryptoDeposit.STATUS_MATCHED,
                "영수증 검증 통과", now);
        cryptoDepositRepository.save(deposit);
    }

    /**
     * 결제를 확정한다.
     *
     * <p>참여 건 생성·에스크로 HELD·인원 확정은 결제 공통의
     * {@link PaymentService#completeIfRecruiting}가 한 트랜잭션에서 처리한다.
     * 배송지는 공통 결제에 저장된 준비 시점 스냅샷을 사용한다.
     *
     * <p>확정 직전 공구가 종료(판매철회·모집종료)됐으면 참여를 만들 수 없다. 이때
     * {@code completeIfRecruiting}는 예외를 던지지 않으므로(예외를 던지면 이 트랜잭션이
     * 롤백돼 방금 기록한 MATCHED 입금까지 사라진다) 온체인 입금 관측을 보존한 채
     * {@link #classifyTerminalDeposits}로 반환 후보(UNMATCHED)로 재분류하고 결제를 종결한다.
     */
    private void completePayment(Payment payment, CryptoPayment cryptoPayment,
                                  LocalDateTime now) {
        // payment.getPaymentId() 대신 crypto_payments 의 PK 를 쓴다. 이 값은 호출 측이
        // 명시적으로 넘긴 ID 에서 온 것이라 항상 채워져 있다.
        Long paymentId = cryptoPayment.getPaymentId();
        PaymentService.CompletionOutcome outcome = paymentService.completeIfRecruiting(paymentId);
        if (outcome == PaymentService.CompletionOutcome.GROUP_TERMINATED) {
            // 입금 확정 직전 공구가 종료됨. 참여/슬롯/PAID를 되살리지 않고, 온체인 입금은
            // 반환 후보로 분류만 한다. 실제 원금 반환은 관리자 주도(CryptoDepositRefundService).
            paymentService.failPayment(paymentId, "GROUP_BUY_TERMINATED_BEFORE_CONFIRMATION");
            classifyTerminalDeposits(paymentId, now, false);
            log.info("공구 종료로 코인 확정 불가 - 입금 반환 후보로 분류 payment={} txHash={}",
                    paymentId, cryptoPayment.getTxHash());
            return;
        }
        cryptoPayment.markConfirmed(now);
        log.info("코인 결제 확정 payment={} txHash={} confirmations={}/{}",
                paymentId, cryptoPayment.getTxHash(),
                cryptoPayment.getConfirmations(), cryptoPayment.getRequiredConfirmations());
    }

}
