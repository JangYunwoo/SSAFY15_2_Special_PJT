package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoDeposit;
import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoDepositRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제에 붙지 못한 입금을 남기는 전용 빈 (FR-PAY-26).
 *
 * <h2>왜 별도 트랜잭션인가</h2>
 *
 * <p>금액이 견적과 다르면 {@code CryptoTxRecordService.record} 가 예외를 던져 결제를
 * 거부합니다. 그 메서드는 {@code @Transactional} 이라 <b>같은 트랜잭션에서 입금을
 * 기록하면 롤백과 함께 사라집니다.</b> 그러면 돈은 우리 지갑에 들어와 있는데 DB 에는
 * 아무 흔적이 없고, 나중에 누구에게 돌려줘야 하는지 알 수 없습니다.
 *
 * <p>{@code REQUIRES_NEW} 로 바깥 트랜잭션을 잠시 멈추고 따로 커밋합니다. 결제는
 * 거부되지만 <b>입금 사실은 남습니다.</b>
 *
 * <p>자기 호출은 프록시를 우회해 트랜잭션 전파가 걸리지 않으므로 반드시 다른 빈이어야
 * 합니다 — 정산·환불에서 빈을 나눈 것과 같은 이유입니다.
 *
 * <h2>왜 payment_id 를 비워 두는가</h2>
 *
 * <p>그 결제는 거부됐습니다. 연결해 두면 "이 결제에 들어온 돈" 으로 보여 나중에 결제
 * 기준으로 집계할 때 섞입니다. 어느 결제를 노린 입금이었는지는 memo 에 남겨 추적만
 * 가능하게 합니다.
 */
@Service
public class CryptoDepositRecorder {

    private static final Logger log = LoggerFactory.getLogger(CryptoDepositRecorder.class);

    private final CryptoDepositRepository cryptoDepositRepository;
    private final CryptoAssetRepository cryptoAssetRepository;
    private final Clock clock;

    public CryptoDepositRecorder(CryptoDepositRepository cryptoDepositRepository,
                                 CryptoAssetRepository cryptoAssetRepository,
                                 Clock clock) {
        this.cryptoDepositRepository = cryptoDepositRepository;
        this.cryptoAssetRepository = cryptoAssetRepository;
        this.clock = clock;
    }

    /**
     * 견적과 금액이 다른 입금을 남깁니다.
     *
     * <p>바깥 트랜잭션이 롤백돼도 이 기록은 유지됩니다. 관리자가
     * {@code GET /admin/crypto/deposits} 에서 보고 돌려보낼 수 있습니다.
     *
     * @param transfer 체인 로그에서 읽은 <b>실제</b> 입금. 견적 수량이 아닙니다
     * @param status   {@code UNDERPAID} 또는 {@code OVERPAID}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMismatched(CryptoPayment cryptoPayment, ChainClient.TransferLog transfer,
                                 long blockNumber, String status) {
        String txHash = cryptoPayment.getTxHash();
        if (cryptoDepositRepository.existsByTxHashAndLogIndex(txHash, transfer.logIndex())) {
            // 같은 해시를 여러 번 제출하는 흔한 경우다. UNIQUE(tx_hash, log_index) 가
            // 최종 방어선이고, 여기서 미리 걸러 예외 없이 넘어간다.
            return;
        }

        CryptoAsset asset = cryptoAssetRepository.findById(cryptoPayment.getAssetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "결제에 쓰인 자산을 찾을 수 없습니다. assetId=" + cryptoPayment.getAssetId()));

        LocalDateTime now = LocalDateTime.now(clock);
        CryptoDeposit deposit = CryptoDeposit.detected(
                cryptoPayment.getChainId(), txHash, transfer.logIndex(), blockNumber,
                transfer.tokenContract(), transfer.from(), transfer.to(), transfer.rawAmount(),
                // 견적 수량이 아니라 실제로 들어온 수량을 기록한다. 돌려줄 때 이 값이
                // 기준이 되므로 틀리면 남의 돈을 더 주거나 덜 주게 된다.
                asset.toAssetAmount(transfer.rawAmount()),
                0, now);
        deposit.matchTo(null, status,
                "결제 %d 견적과 금액이 달라 매칭하지 않음".formatted(cryptoPayment.getPaymentId()), now);
        cryptoDepositRepository.save(deposit);

        log.warn("견적과 다른 입금을 기록했습니다. 관리자 환불 대상입니다. "
                        + "txHash={} logIndex={} status={} raw={} from={}",
                txHash, transfer.logIndex(), status, transfer.rawAmount(), transfer.from());
    }
}
