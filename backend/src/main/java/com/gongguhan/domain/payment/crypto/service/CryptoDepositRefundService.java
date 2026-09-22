package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoDeposit;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoDepositRepository;
import com.gongguhan.domain.refund.entity.Refund;
import com.gongguhan.domain.refund.service.RefundLedgerService;
import com.gongguhan.domain.refund.service.RefundLedgerService.OpenResult;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 결제에 붙지 못한 입금의 환불 (S15P21B205-80, FR-PAY-26 / FR-SLT-10).
 *
 * <h2>어떤 입금이 대상인가</h2>
 *
 * <p>체인에서 우리 수취 지갑으로 들어왔지만 어느 결제와도 맞지 않는 입금입니다.
 *
 * <ul>
 *   <li>{@code UNMATCHED} — 어느 결제에도 붙지 않음(슬롯 만료 뒤 도착 등)</li>
 *   <li>{@code UNDERPAID} — 견적보다 적게 보냄</li>
 *   <li>{@code OVERPAID} — 견적보다 많이 보냄</li>
 * </ul>
 *
 * <p>돈은 우리 지갑에 있는데 대응하는 참여 건이 없습니다. 그대로 두면 남의 돈을
 * 보관하게 되므로 돌려보내야 합니다.
 *
 * <h2>왜 관리자가 실행하는가</h2>
 *
 * <p>배치가 자동으로 돌려보내면, 잘못 분류된 입금까지 사람 확인 없이 나갑니다. 특히
 * {@code UNDERPAID} 는 구매자가 곧 부족분을 더 보낼 수도 있어 성급히 되돌리면 결제를
 * 방해합니다. 정산 집계와 실행을 나눈 것과 같은 판단입니다(24장).
 *
 * <h2>얼마를 돌려주는가 — 받은 그대로</h2>
 *
 * <p>참여 취소 환불은 Quote 로 <b>원화 가치가 확정된</b> 돈이라 환불 시점 환율로
 * 환산합니다. 그러나 매칭되지 않은 입금은 <b>우리가 원화로 받아들인 적이 없습니다.</b>
 * 환산하면 동의한 적 없는 환율 변동을 보낸 사람에게 지우게 되고, 환율이 움직인 만큼
 * 플랫폼이 이득이나 손해를 봅니다. 받은 수량을 그대로 돌려주는 것이 유일하게 다툼의
 * 여지가 없는 금액입니다.
 *
 * <h2>트랜잭션 경계</h2>
 *
 * <p>이 클래스에는 {@code @Transactional} 이 없습니다. 온체인 송금은 수 초가 걸리는데
 * DB 트랜잭션 안에서 부르면 커넥션과 잠금이 그만큼 묶입니다. 기록은
 * {@link RefundLedgerService} 와 {@link CryptoTxRecordService} 의 짧은 트랜잭션이
 * 담당합니다 — {@code RefundOrchestrationService} 와 같은 구조입니다.
 */
@Service
public class CryptoDepositRefundService {

    private static final Logger log = LoggerFactory.getLogger(CryptoDepositRefundService.class);

    /** 돌려보낼 수 있는 입금 상태. {@code MATCHED} 는 결제에 붙었으므로 제외합니다. */
    private static final List<String> REFUNDABLE_STATUSES = List.of(
            CryptoDeposit.STATUS_UNMATCHED,
            CryptoDeposit.STATUS_UNDERPAID,
            CryptoDeposit.STATUS_OVERPAID);

    private final CryptoDepositRepository cryptoDepositRepository;
    private final CryptoAssetRepository cryptoAssetRepository;
    private final CryptoRefundService cryptoRefundService;
    private final CryptoTxRecordService recordService;
    private final RefundLedgerService ledgerService;
    private final ExchangeRateProvider exchangeRateProvider;

    public CryptoDepositRefundService(CryptoDepositRepository cryptoDepositRepository,
                                      CryptoAssetRepository cryptoAssetRepository,
                                      CryptoRefundService cryptoRefundService,
                                      CryptoTxRecordService recordService,
                                      RefundLedgerService ledgerService,
                                      ExchangeRateProvider exchangeRateProvider) {
        this.cryptoDepositRepository = cryptoDepositRepository;
        this.cryptoAssetRepository = cryptoAssetRepository;
        this.cryptoRefundService = cryptoRefundService;
        this.recordService = recordService;
        this.ledgerService = ledgerService;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    /** 돌려보낼 수 있는 입금 목록. 오래된 것부터 보여 줍니다 — 먼저 들어온 돈이 먼저 나갑니다. */
    public List<CryptoDeposit> findRefundable() {
        return cryptoDepositRepository.findByStatusInOrderByDetectedAtAsc(REFUNDABLE_STATUSES);
    }

    /**
     * 입금 원금 반환. 공통 원장의 실행권을 송신 전에 커밋하며 결과 불명은 다시 보내지 않는다.
     * 기존 COMPLETED 원장은 자금 이동 없이 입금 상태만 복구한다.
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public Refund refund(Long depositId) {
        CryptoDeposit deposit = cryptoDepositRepository.findById(depositId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "입금 건을 찾을 수 없습니다. depositId=" + depositId));

        Refund existing = ledgerService.findDepositRefund(depositId).orElse(null);
        if (existing != null && existing.isCompleted()) {
            return finalizeDeposit(depositId, existing);
        }
        if (!REFUNDABLE_STATUSES.contains(deposit.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "돌려보낼 수 있는 입금이 아닙니다. depositId=" + depositId);
        }
        if (existing != null && (!existing.isProcessing() || existing.isExecutionClaimed())) {
            return existing;
        }

        CryptoAsset asset = findAsset(deposit);
        if (asset.getRequiredConfirmations() <= 0
                || deposit.getConfirmations() < asset.getRequiredConfirmations()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "입금의 필요 컨펌이 완료되지 않아 아직 반환할 수 없습니다.");
        }
        BigInteger rawAmount = new BigInteger(deposit.getRawAmount());
        OpenResult opened = existing == null
                ? ledgerService.openForUnmatchedDeposit(depositId,
                    krwReference(asset, deposit.getAssetAmount()), deposit.getFromAddress())
                : new OpenResult(existing, false);
        Refund refund = opened.refund();
        if (refund.isCompleted()) { return finalizeDeposit(depositId, refund); }
        if (!opened.created() && (!refund.isProcessing() || refund.isExecutionClaimed())) { return refund; }

        Long refundId = refund.getRefundId();
        if (!ledgerService.tryClaimExecution(refundId, null)) { return ledgerService.get(refundId); }
        if (!cryptoRefundService.isEnabled()) {
            return ledgerService.failRejected(refundId,
                    "환불 지갑이 설정되지 않아 코인 환불을 보낼 수 없습니다.");
        }

        Refund completed;
        try {
            CryptoRefundService.RefundTransfer transfer = cryptoRefundService.refundAsReceived(
                    asset.getAssetId(), deposit.getFromAddress(), rawAmount);
            completed = ledgerService.completeByCrypto(refundId, transfer.txHash(), transfer.assetAmount());
        } catch (RuntimeException exception) {
            // 어댑터는 미송신과 전송 후 타임아웃을 같은 오류로 보고한다. 확정 거절로 추정하지 않는다.
            Refund current = ledgerService.get(refundId);
            if (current != null && current.isCompleted()) { return finalizeDeposit(depositId, current); }
            log.warn("입금 환불 결과 대조 필요 depositId={} refundId={}", depositId, refundId);
            return ledgerService.fail(refundId,
                    "송금 결과를 확인하지 못했습니다. 체인에서 확인한 뒤 재처리하세요.");
        }
        return finalizeDeposit(depositId, completed);
    }

    private Refund finalizeDeposit(Long depositId, Refund completed) {
        try {
            recordService.markDepositRefunded(depositId);
        } catch (RuntimeException exception) {
            // 외부 성공 원장을 FAILED로 되돌리지 않는다. 같은 관리자 요청으로 기록만 재시도한다.
            log.error("입금 환불 종결 기록 재처리 필요 depositId={} refundId={}",
                    depositId, completed.getRefundId(), exception);
        }
        return completed;
    }

    /**
     * 입금 로그에는 자산 ID 가 없습니다. 체인과 토큰 주소로 되찾습니다.
     *
     * <p>비활성 자산도 찾아야 합니다 — 지원을 내린 뒤에도 그 전에 들어온 돈은 돌려줘야
     * 하기 때문입니다.
     */
    private CryptoAsset findAsset(CryptoDeposit deposit) {
        return cryptoAssetRepository
                .findByChainIdAndTokenContractIgnoreCase(
                        deposit.getChainId(), deposit.getTokenContract())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "입금에 쓰인 자산을 찾을 수 없습니다. chainId=%d token=%s"
                                .formatted(deposit.getChainId(), deposit.getTokenContract())));
    }

    /**
     * 기록용 원화 환산액.
     *
     * <p>실제 송금액은 이 값과 무관합니다. 다만 {@code refunds.amount} 가 원화 단위라
     * 비워둘 수 없고, 0 으로 두면 환불 통계가 어긋납니다. 시세를 못 가져오면 기록을
     * 지어내지 않고 실패시킵니다 — 잘못된 숫자가 장부에 남는 것보다 낫습니다.
     */
    private long krwReference(CryptoAsset asset, BigDecimal assetAmount) {
        BigDecimal rate = exchangeRateProvider.getKrwRate(asset.getSymbol());
        if (rate == null || rate.signum() <= 0) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "환율을 조회하지 못해 입금 환불을 기록할 수 없습니다. symbol=" + asset.getSymbol());
        }
        return assetAmount.multiply(rate).setScale(0, RoundingMode.DOWN).longValue();
    }
}
