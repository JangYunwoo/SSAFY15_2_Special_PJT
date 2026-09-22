package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import com.gongguhan.domain.payment.crypto.repository.CryptoPaymentRepository;
import com.gongguhan.global.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 컨펌 폴링 (CRYP-04).
 *
 * <p>구매자가 트랜잭션을 제출한 시점에는 컨펌이 0~1개뿐이라 아직 결제 완료가 아니다.
 * 필요 컨펌 수(자산별 정책값, USDT 기준 5개)에 도달할 때까지 5초마다 확인한다.
 *
 * <p><b>왜 프론트 폴링이 아니라 스케줄러인가.</b> 프론트가 상태 조회를 반복하는
 * 방식은 구매자가 브라우저를 닫으면 멈춘다. 송금은 이미 체인에 올라간 상태이므로
 * 결제가 영원히 미완료로 남고, 자금은 우리 지갑에 들어와 있다. 서버가 책임져야 한다.
 *
 * <p><b>RPC 호출을 아끼는 방식.</b> 최신 블록 번호는 주기당 한 번만 조회하고 모든
 * 건에 재사용한다. 건당 조회하면 공용 테스트넷 노드의 호출 한도에 금방 걸린다.
 *
 * <p><b>폴링을 끄는 방법은 주기를 0 으로 두는 것이 아니다.</b>
 * {@code @Scheduled} 의 {@code fixedDelay} 는 양수여야 하고, 0 을 넣으면 스케줄러가
 * 조용히 멈추는 대신 <b>애플리케이션 기동이 실패</b>한다. 끄려면
 * {@code app.crypto.confirmation-poll-enabled=false} 로 빈 자체를 만들지 않는다.
 * 전역 스위치 {@code app.scheduling.enabled=false} 로도 함께 꺼진다.
 */
@Component
@ConditionalOnProperty(name = "app.crypto.confirmation-poll-enabled",
        havingValue = "true", matchIfMissing = true)
public class CryptoConfirmationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CryptoConfirmationScheduler.class);

    private final CryptoPaymentRepository cryptoPaymentRepository;
    private final CryptoTxRecordService cryptoTxRecordService;
    private final ChainClient chainClient;
    private final CryptoProperties properties;

    public CryptoConfirmationScheduler(CryptoPaymentRepository cryptoPaymentRepository,
                                        CryptoTxRecordService cryptoTxRecordService,
                                        ChainClient chainClient,
                                        CryptoProperties properties) {
        this.cryptoPaymentRepository = cryptoPaymentRepository;
        this.cryptoTxRecordService = cryptoTxRecordService;
        this.chainClient = chainClient;
        this.properties = properties;
    }

    /**
     * {@code fixedDelay} 를 쓴다. {@code fixedRate} 는 이전 실행이 끝나지 않아도
     * 다음 실행을 시작하므로, 체인이 느릴 때 실행이 겹쳐 같은 건을 두 번 처리한다.
     */
    @Scheduled(fixedDelayString = "${app.crypto.confirmation-poll-interval-ms:5000}",
            initialDelayString = "${app.crypto.confirmation-poll-initial-delay-ms:10000}")
    public void pollConfirmations() {
        Pageable page = PageRequest.of(0, properties.confirmationPollBatchSize());

        List<CryptoPayment> awaitingReceipt = cryptoPaymentRepository.findAwaitingReceipt(page);
        List<CryptoPayment> pending = cryptoPaymentRepository.findPendingConfirmation(page);
        var unmatched = cryptoTxRecordService.findUnconfirmedDeposits(properties.chainId(), page);

        if (awaitingReceipt.isEmpty() && pending.isEmpty() && unmatched.isEmpty()) {
            return;   // 할 일이 없으면 RPC 를 부르지 않는다.
        }

        long latestBlock;
        try {
            latestBlock = chainClient.getLatestBlockNumber();
        } catch (BusinessException e) {
            // 노드 장애는 흔하다. 다음 주기에 다시 시도하면 되므로 경고만 남긴다.
            log.warn("최신 블록 조회 실패 - 이번 주기 건너뜀: {}", e.getMessage());
            return;
        }

        int receiptResolved = resolveLateReceipts(awaitingReceipt, latestBlock);
        int completed = advanceConfirmations(pending, latestBlock);
        // 결제 상세가 롤백된 미달/초과 입금도 독립 원장을 기준으로 추적한다.
        for (var deposit : unmatched) {
            try {
                var receipt = chainClient.findReceipt(deposit.getTxHash());
                cryptoTxRecordService.refreshDepositConfirmations(
                        deposit.getDepositId(), receipt.orElse(null), latestBlock);
            } catch (RuntimeException error) {
                log.warn("반환 대상 입금 컨펌 조회 실패 depositId={}", deposit.getDepositId(), error);
            }
        }

        if (receiptResolved > 0 || completed > 0) {
            log.info("컨펌 폴링 완료 block={} 영수증확인={}건 결제확정={}건",
                    latestBlock, receiptResolved, completed);
        }
    }

    /** 제출 시점에 노드가 몰랐던 트랜잭션의 영수증을 다시 확인한다. */
    private int resolveLateReceipts(List<CryptoPayment> targets, long latestBlock) {
        int resolved = 0;
        for (CryptoPayment cryptoPayment : targets) {
            try {
                Optional<ChainClient.ReceiptView> receipt =
                        chainClient.findReceipt(cryptoPayment.getTxHash());
                if (receipt.isEmpty()) {
                    continue;   // 여전히 모른다. 다음 주기에 재시도.
                }
                cryptoTxRecordService.applyLateReceipt(
                        cryptoPayment.getPaymentId(), receipt.get(), latestBlock);
                resolved++;
            } catch (RuntimeException e) {
                // 한 건이 실패해도 나머지는 처리한다. 배치가 통째로 죽으면
                // 그 주기의 모든 결제가 지연된다.
                log.warn("영수증 재확인 실패 payment={}", cryptoPayment.getPaymentId(), e);
            }
        }
        return resolved;
    }

    /** 컨펌 수를 갱신하고 충족된 건을 확정한다. */
    private int advanceConfirmations(List<CryptoPayment> targets, long latestBlock) {
        int completed = 0;
        for (CryptoPayment cryptoPayment : targets) {
            try {
                if (cryptoTxRecordService.refreshConfirmations(
                        cryptoPayment.getPaymentId(), latestBlock)) {
                    completed++;
                }
            } catch (RuntimeException e) {
                log.warn("컨펌 갱신 실패 payment={}", cryptoPayment.getPaymentId(), e);
            }
        }
        return completed;
    }
}
