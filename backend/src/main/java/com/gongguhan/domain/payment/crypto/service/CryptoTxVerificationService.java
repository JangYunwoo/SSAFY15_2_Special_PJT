package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.crypto.dto.CryptoPaymentStatusResponse;
import com.gongguhan.domain.payment.crypto.dto.CryptoTxSubmitRequest;
import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import com.gongguhan.domain.payment.crypto.entity.CryptoQuote;
import com.gongguhan.domain.payment.crypto.entity.CryptoWallet;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoPaymentRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoQuoteRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoWalletRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 트랜잭션 해시 등록과 검증 (CRYP-03), 상태 조회 (CRYP-04).
 *
 * <p><b>프론트가 보낸 txHash 는 신뢰하지 않는다.</b> 백엔드가 체인에서 영수증을 직접
 * 가져와 수취 주소·토큰·금액·송신 주소·revert 여부를 대조한 뒤에만 결제로 인정한다.
 * 그러지 않으면 남의 트랜잭션 해시를 복사해 제출하는 것만으로 결제 완료를 받아낼 수 있다.
 *
 * <p><b>트랜잭션 경계.</b> 이 클래스에는 {@code @Transactional}을 붙이지 않는다.
 * 체인 RPC 는 수 초가 걸릴 수 있고, 그동안 DB 트랜잭션을 붙잡으면 커넥션과 잠금이
 * 묶인다. 검증 전 조회 → 체인 호출 → 짧은 쓰기 트랜잭션 순서로 나눠
 * {@link CryptoTxRecordService}에 위임한다.
 * (API 명세서 5장 원자성 규칙 5: 외부 결제 호출은 DB 트랜잭션 밖에서 수행한다)
 */
@Service
public class CryptoTxVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CryptoTxVerificationService.class);
    private static final String METHOD_CRYPTO = "CRYPTO";

    private final PaymentRepository paymentRepository;
    private final CryptoPaymentRepository cryptoPaymentRepository;
    private final CryptoQuoteRepository cryptoQuoteRepository;
    private final CryptoAssetRepository cryptoAssetRepository;
    private final CryptoWalletRepository cryptoWalletRepository;
    private final ChainClient chainClient;
    private final CryptoWalletProofService walletProofService;
    private final Clock clock;

    public CryptoTxVerificationService(PaymentRepository paymentRepository,
                                        CryptoPaymentRepository cryptoPaymentRepository,
                                        CryptoQuoteRepository cryptoQuoteRepository,
                                        CryptoAssetRepository cryptoAssetRepository,
                                        CryptoWalletRepository cryptoWalletRepository,
                                        ChainClient chainClient,
                                        CryptoWalletProofService walletProofService,
                                        Clock clock) {
        this.paymentRepository = paymentRepository;
        this.cryptoPaymentRepository = cryptoPaymentRepository;
        this.cryptoQuoteRepository = cryptoQuoteRepository;
        this.cryptoAssetRepository = cryptoAssetRepository;
        this.cryptoWalletRepository = cryptoWalletRepository;
        this.chainClient = chainClient;
        this.walletProofService = walletProofService;
        this.clock = clock;
    }

    /**
     * CRYP-03. 구매자가 제출한 트랜잭션을 검증하고 기록한다.
     *
     * <p>결제 완료로 곧바로 이어지지 않는다. 컨펌이 필요 수에 도달해야 하며, 보통은
     * 폴링 스케줄러가 그 시점에 확정한다. 제출 시점에 이미 컨펌이 충족돼 있으면
     * 여기서 바로 확정된다.
     */
    public CryptoPaymentStatusResponse submitTransaction(Long userId, Long paymentId,
                                                          CryptoTxSubmitRequest request) {
        String txHash = request.txHash().toLowerCase();
        Optional<CryptoPayment> recovered = walletProofService.verifyAndRecover(userId, paymentId, request);
        if (recovered.isPresent()) {
            Payment payment = paymentRepository.findByPaymentIdAndUserId(paymentId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
            return CryptoPaymentStatusResponse.of(payment, recovered.get());
        }

        SubmitContext context;
        try {
            context = loadAndValidate(userId, paymentId, request.quoteId(), txHash);
        } catch (BusinessException error) {
            // 최초 검증 직후 다른 동일 요청이 커밋했을 수 있다. 새 거래를 만들지 않고 응답만 복구한다.
            Optional<CryptoPayment> completed = walletProofService.verifyAndRecover(userId, paymentId, request);
            if (completed.isPresent()) {
                Payment payment = paymentRepository.findByPaymentIdAndUserId(paymentId, userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
                return CryptoPaymentStatusResponse.of(payment, completed.get());
            }
            throw error;
        }

        // --- 체인 호출: 트랜잭션 밖에서 수행한다 ---
        Optional<ChainClient.ReceiptView> receipt = chainClient.findReceipt(txHash);
        long latestBlock = chainClient.getLatestBlockNumber();

        // paymentId 를 넘긴다. context.payment() 는 detached 라 쓰기 트랜잭션 안에서
        // 변경해도 반영되지 않는다. 기록 서비스가 직접 다시 읽는다.
        CryptoPayment cryptoPayment = walletProofService.recordVerified(
                userId, paymentId, request, context.quote(), context.asset(), context.wallet(),
                receipt.orElse(null), latestBlock);

        // 확정 여부가 반영된 최신 결제 상태를 다시 읽어 응답한다.
        Payment refreshed = paymentRepository.findById(paymentId).orElse(context.payment());
        return CryptoPaymentStatusResponse.of(refreshed, cryptoPayment);
    }

    /** CRYP-04. 입금·컨펌·매칭 상태 조회. */
    @Transactional(readOnly = true)
    public CryptoPaymentStatusResponse getStatus(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));
        if (!METHOD_CRYPTO.equals(payment.getMethod())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "디지털 자산 결제 건이 아닙니다.");
        }
        CryptoPayment cryptoPayment = cryptoPaymentRepository.findById(paymentId).orElse(null);
        return CryptoPaymentStatusResponse.of(payment, cryptoPayment);
    }

    // ------------------------------------------------------------------
    // 내부
    // ------------------------------------------------------------------

    /**
     * 체인을 호출하기 전에 확인할 수 있는 것을 모두 확인한다.
     *
     * <p>여기서 걸러내면 불필요한 RPC 호출을 하지 않는다. 공용 테스트넷 노드는
     * 호출 한도가 있어 헛호출을 줄이는 편이 낫다.
     *
     * <p>트랜잭션을 열지 않는다. 같은 클래스 안에서 호출되므로 {@code @Transactional}을
     * 붙여도 프록시를 우회해 적용되지 않는다. 여기서 하는 일은 조회뿐이라
     * 리포지토리 호출마다 짧은 트랜잭션이 생기는 것으로 충분하다.
     *
     * <p>따라서 돌려주는 엔티티는 <b>detached 상태</b>다. 값 읽기에만 쓰고, 변경이
     * 필요한 엔티티는 쓰기 트랜잭션 안에서 다시 읽어야 한다.
     */
    private SubmitContext loadAndValidate(Long userId, Long paymentId, Long quoteId,
                                           String txHash) {
        LocalDateTime now = LocalDateTime.now(clock);

        Payment payment = paymentRepository.findByPaymentIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_ACCESS_DENIED));

        if (!METHOD_CRYPTO.equals(payment.getMethod())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "디지털 자산 결제 건이 아닙니다.");
        }
        if (!payment.isProcessing()) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED);
        }
        // FR-PAY-09 / DR-30: 유효한 슬롯 없이는 결제를 진행하지 않는다.
        if (payment.isExpired(now)) {
            throw new BusinessException(ErrorCode.PAYMENT_SLOT_EXPIRED);
        }

        // 같은 결제에 두 번 제출하는 것을 막는다. crypto_payments 는 payment_id 가
        // PK 라 두 번째 저장이 제약 위반으로 터지는데, 그 전에 400 으로 돌려준다.
        if (cryptoPaymentRepository.existsById(paymentId)) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "이미 트랜잭션이 등록된 결제입니다.");
        }

        // 하나의 트랜잭션을 여러 결제에 재사용하는 것을 막는다.
        // tx_hash 가 UNIQUE 라 DB 도 막지만, 사유를 분명히 하려고 먼저 확인한다.
        if (cryptoPaymentRepository.existsByTxHash(txHash)) {
            throw new BusinessException(ErrorCode.CRYPTO_AMOUNT_MISMATCH,
                    "이미 다른 결제에 사용된 트랜잭션입니다.");
        }

        CryptoQuote quote = cryptoQuoteRepository.findById(quoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "견적을 찾을 수 없습니다."));

        CryptoPreparedAssetPolicy.requireMatches(payment, quote.getAssetId());
        // crypto_quotes 에 payment_id 컬럼이 없어 소유 결제를 DB 로 확인할 수 없다.
        // 원화 금액 일치로 대신 확인한다. 금액이 같은 남의 견적을 써도 환율만
        // 고정될 뿐 금전적 이득이 없고, used claim 이 중복 사용을 막는다.
        if (quote.getKrwAmount() != payment.getKrwAmount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "견적 금액이 결제 금액과 일치하지 않습니다.");
        }
        if (quote.isExpired(now)) {
            throw new BusinessException(ErrorCode.QUOTE_EXPIRED);
        }
        if (quote.isUsed()) {
            throw new BusinessException(ErrorCode.QUOTE_EXPIRED,
                    "이미 사용된 견적입니다. 견적을 다시 발급받아 주세요.");
        }

        CryptoAsset asset = cryptoAssetRepository.findByAssetIdAndEnabledTrue(quote.getAssetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "지원하지 않는 디지털 자산입니다."));

        CryptoWallet wallet = cryptoWalletRepository
                .findFirstByChainIdAndPurposeAndActiveTrue(
                        asset.getChainId(), CryptoWallet.PURPOSE_RECEIVE)
                .orElseThrow(() -> {
                    log.error("수취 지갑이 없음 chainId={}", asset.getChainId());
                    return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                            "수취 지갑이 설정되어 있지 않습니다.");
                });

        return new SubmitContext(payment, quote, asset, wallet);
    }

    /** 체인 호출 전에 모아둔 검증 결과. */
    protected record SubmitContext(
            Payment payment,
            CryptoQuote quote,
            CryptoAsset asset,
            CryptoWallet wallet
    ) {
    }
}
