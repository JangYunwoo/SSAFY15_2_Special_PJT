package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.account.entity.UserAddress;
import com.gongguhan.domain.account.repository.UserAddressRepository;
import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.port.GroupBuySlotPort;
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
    private final CryptoQuoteRepository cryptoQuoteRepository;
    private final PaymentRepository paymentRepository;
    private final UserAddressRepository userAddressRepository;
    private final PaymentService paymentService;
    private final GroupBuySlotPort groupBuySlotPort;
    private final Clock clock;

    public CryptoTxRecordService(CryptoPaymentRepository cryptoPaymentRepository,
                                  CryptoDepositRepository cryptoDepositRepository,
                                  CryptoQuoteRepository cryptoQuoteRepository,
                                  PaymentRepository paymentRepository,
                                  UserAddressRepository userAddressRepository,
                                  PaymentService paymentService,
                                  GroupBuySlotPort groupBuySlotPort,
                                  Clock clock) {
        this.cryptoPaymentRepository = cryptoPaymentRepository;
        this.cryptoDepositRepository = cryptoDepositRepository;
        this.cryptoQuoteRepository = cryptoQuoteRepository;
        this.paymentRepository = paymentRepository;
        this.userAddressRepository = userAddressRepository;
        this.paymentService = paymentService;
        this.groupBuySlotPort = groupBuySlotPort;
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

        Payment payment = paymentRepository.findById(paymentId)
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

        CryptoPayment cryptoPayment = CryptoPayment.submit(
                paymentId, asset, quote, wallet, fromAddress, txHash);

        if (receipt == null) {
            // 노드가 아직 모른다. 실패가 아니므로 폴링이 이어받는다.
            cryptoPaymentRepository.save(cryptoPayment);
            log.info("영수증 미확인 상태로 등록 payment={} txHash={}",
                    payment.getPaymentId(), txHash);
            return cryptoPayment;
        }

        cryptoPayment.applyReceipt(receipt.success(), receipt.blockNumber());

        if (!receipt.success()) {
            // 트랜잭션이 revert 됐다. 자금이 이동하지 않았으므로 입금 기록도 없다.
            cryptoPaymentRepository.save(cryptoPayment);
            failPayment(payment, "TX_REVERTED");
            log.info("트랜잭션 revert payment={} txHash={}", payment.getPaymentId(), txHash);
            return cryptoPayment;
        }

        ChainClient.TransferLog transfer = requireMatchingTransfer(cryptoPayment, receipt);
        cryptoPaymentRepository.save(cryptoPayment);
        recordDeposit(cryptoPayment, transfer, receipt, latestBlock, now);

        cryptoPayment.updateConfirmations(
                ChainClient.calculateConfirmations(latestBlock, receipt.blockNumber()));

        if (cryptoPayment.isConfirmed()) {
            completePayment(payment, cryptoPayment, now);
        }
        return cryptoPayment;
    }

    /**
     * 컨펌 수를 갱신하고, 충족되면 결제를 확정한다 (CRYP-04 폴링).
     *
     * @return 이 호출로 결제가 확정된 경우 true
     */
    @Transactional
    public boolean refreshConfirmations(Long paymentId, long latestBlock) {
        CryptoPayment cryptoPayment = cryptoPaymentRepository.findById(paymentId).orElse(null);
        if (cryptoPayment == null || cryptoPayment.getBlockNumber() == null) {
            return false;
        }

        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !payment.isProcessing()) {
            return false;   // 이미 완료·실패·만료됨. 폴링이 배치와 겹치는 정상 상황이다.
        }

        LocalDateTime now = LocalDateTime.now(clock);

        // 슬롯이 만료된 뒤 컨펌이 도달하는 경우가 있다. 이때 결제를 확정하면
        // 자리는 이미 남에게 넘어갔는데 참여 건을 만들게 되어 인원이 초과된다.
        // FR-SLT-10 대로 결제를 폐기하고 환불 대상으로 남긴다.
        if (payment.isExpired(now)) {
            failPayment(payment, "SLOT_EXPIRED_BEFORE_CONFIRMATION");
            log.warn("슬롯 만료 후 컨펌 도달 - 환불 대상 payment={}", paymentId);
            return false;
        }

        cryptoPayment.updateConfirmations(
                ChainClient.calculateConfirmations(latestBlock, cryptoPayment.getBlockNumber()));

        if (!cryptoPayment.isConfirmed()) {
            return false;
        }
        completePayment(payment, cryptoPayment, now);
        return true;
    }

    /** 영수증을 뒤늦게 확인한 건에 결과를 반영한다 (폴링). */
    @Transactional
    public void applyLateReceipt(Long paymentId, ChainClient.ReceiptView receipt, long latestBlock) {
        CryptoPayment cryptoPayment = cryptoPaymentRepository.findById(paymentId).orElse(null);
        if (cryptoPayment == null || cryptoPayment.getReceiptStatus() != null) {
            return;
        }
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || !payment.isProcessing()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        cryptoPayment.applyReceipt(receipt.success(), receipt.blockNumber());

        if (!receipt.success()) {
            failPayment(payment, "TX_REVERTED");
            return;
        }

        try {
            ChainClient.TransferLog transfer = requireMatchingTransfer(cryptoPayment, receipt);
            recordDeposit(cryptoPayment, transfer, receipt, latestBlock, now);
        } catch (BusinessException e) {
            // 폴링 경로에서는 예외를 던져도 받을 사용자가 없다. 결제를 실패로
            // 확정하고 사유를 남겨 관리자가 확인하게 한다.
            failPayment(payment, "TX_VERIFICATION_FAILED: " + e.getMessage());
            log.warn("폴링 중 트랜잭션 검증 실패 payment={}", paymentId, e);
            return;
        }

        cryptoPayment.updateConfirmations(
                ChainClient.calculateConfirmations(latestBlock, receipt.blockNumber()));
        if (cryptoPayment.isConfirmed()) {
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
        ChainClient.TransferLog closest = fromBuyer.get(0);
        boolean underpaid = closest.rawAmount().compareTo(expected) < 0;
        throw new BusinessException(ErrorCode.CRYPTO_AMOUNT_MISMATCH,
                underpaid
                        ? "입금액이 견적보다 적습니다. 견적 금액 그대로 ì¡ê¸해야 합니다."
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
        if (cryptoDepositRepository.existsByTxHashAndLogIndex(
                cryptoPayment.getTxHash(), transfer.logIndex())) {
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
     * {@link PaymentService#completePayment}가 한 트랜잭션에서 처리한다.
     * 여기서는 배송지 스냅샷만 만들어 넘긴다.
     */
    private void completePayment(Payment payment, CryptoPayment cryptoPayment,
                                  LocalDateTime now) {
        // payment.getPaymentId() 대신 crypto_payments 의 PK 를 쓴다. 이 값은 호출 측이
        // 명시적으로 넘긴 ID 에서 온 것이라 항상 채워져 있다.
        Long paymentId = cryptoPayment.getPaymentId();
        paymentService.completePayment(paymentId, buildSnapshot(payment));
        cryptoPayment.markConfirmed(now);
        log.info("코인 결제 확정 payment={} txHash={} confirmations={}/{}",
                paymentId, cryptoPayment.getTxHash(),
                cryptoPayment.getConfirmations(), cryptoPayment.getRequiredConfirmations());
    }

    /**
     * 배송지 스냅샷을 만든다.
     *
     * <p>{@code payments.address_id}로 다시 읽는다. {@code user_addresses}는 수정
     * 메서드가 없는 불변 엔티티라 결제 시작 시점과 같은 값이 나온다.
     */
    private PaymentService.ParticipationSnapshot buildSnapshot(Payment payment) {
        if (payment.getAddressId() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "결제 건에 배송지가 없습니다. paymentId=" + payment.getPaymentId());
        }
        UserAddress address = userAddressRepository
                .findByAddressIdAndUserId(payment.getAddressId(), payment.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "결제 시작 시 선택한 배송지를 찾을 수 없습니다. addressId="
                                + payment.getAddressId()));

        return new PaymentService.ParticipationSnapshot(
                address.getRecipientName(),
                address.getRecipientPhone(),
                address.getZipcode(),
                address.getAddress1(),
                address.getAddress2());
    }

    /**
     * 결제를 실패로 확정하고 선점 슬롯을 놓아준다.
     *
     * <p>슬롯 해제를 빠뜨리면 그 자리가 영구히 막힌다. 슬롯 만료 배치(FR-SLT-07)가
     * 아직 없어서 자동으로 회수되지도 않는다. 공동구매 엔티티를 직접 만지지 않고
     * {@link GroupBuySlotPort}를 쓰는 이유는, 결제 계층이 공동구매 내부에 의존하지
     * 않게 하려는 팀의 경계 설계를 따르기 위함이다.
     */
    private void failPayment(Payment payment, String reason) {
        payment.markFailed(reason);
        groupBuySlotPort.release(payment.getGroupBuyId());
    }
}
