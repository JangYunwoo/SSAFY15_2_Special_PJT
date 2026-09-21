package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.domain.payment.common.repository.PaymentRepository;
import com.gongguhan.domain.payment.crypto.dto.CryptoAssetResponse;
import com.gongguhan.domain.payment.crypto.dto.CryptoQuoteRequest;
import com.gongguhan.domain.payment.crypto.dto.CryptoQuoteResponse;
import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoQuote;
import com.gongguhan.domain.payment.crypto.entity.CryptoWallet;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoQuoteRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoWalletRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지원 자산 조회(CRYP-01)와 Quote 발급(CRYP-02).
 *
 * <p>체인을 건드리지 않는다. 시세 조회와 환산, 수취 주소 안내까지가 범위다.
 * 트랜잭션 해시 등록(CRYP-03)과 컨펌 폴링(CRYP-04)은 별도 작업이다.
 */
@Service
@Transactional(readOnly = true)
public class CryptoQuoteService {

    private static final Logger log = LoggerFactory.getLogger(CryptoQuoteService.class);
    private static final String METHOD_CRYPTO = "CRYPTO";

    private final CryptoAssetRepository cryptoAssetRepository;
    private final CryptoWalletRepository cryptoWalletRepository;
    private final CryptoQuoteRepository cryptoQuoteRepository;
    private final PaymentRepository paymentRepository;
    private final ExchangeRateProvider exchangeRateProvider;
    private final PolicyService policyService;
    private final Clock clock;

    public CryptoQuoteService(CryptoAssetRepository cryptoAssetRepository,
                               CryptoWalletRepository cryptoWalletRepository,
                               CryptoQuoteRepository cryptoQuoteRepository,
                               PaymentRepository paymentRepository,
                               ExchangeRateProvider exchangeRateProvider,
                               PolicyService policyService,
                               Clock clock) {
        this.cryptoAssetRepository = cryptoAssetRepository;
        this.cryptoWalletRepository = cryptoWalletRepository;
        this.cryptoQuoteRepository = cryptoQuoteRepository;
        this.paymentRepository = paymentRepository;
        this.exchangeRateProvider = exchangeRateProvider;
        this.policyService = policyService;
        this.clock = clock;
    }

    /** CRYP-01. 활성 자산만 노출한다. */
    public List<CryptoAssetResponse> listAssets() {
        return cryptoAssetRepository.findByEnabledTrueOrderByAssetIdAsc().stream()
                .map(CryptoAssetResponse::from)
                .toList();
    }

    /**
     * CRYP-02. 시세에 Buffer를 얹은 Quote를 발급한다.
     *
     * <p>재요청을 허용한다. Quote가 만료되면 새로 발급받아야 하고(FR-PAY-28),
     * 이전 Quote는 {@code used = false}로 남아 TTL이 지나면 자연히 죽는다.
     * 발급 자체는 자금을 움직이지 않으므로 멱등 처리를 걸 필요가 없다.
     *
     * @throws BusinessException 결제 건이 본인 것이 아니거나, 코인 결제가 아니거나,
     *                           슬롯이 이미 끝났을 때
     */
    @Transactional
    public CryptoQuoteResponse issueQuote(Long userId, Long paymentId, CryptoQuoteRequest request) {
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

        CryptoPreparedAssetPolicy.requireMatches(payment, request.assetId());
        CryptoAsset asset = cryptoAssetRepository.findByAssetIdAndEnabledTrue(request.assetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "지원하지 않는 디지털 자산입니다."));

        CryptoWallet receiveWallet = findReceiveWallet(asset.getChainId());

        BigDecimal marketRate = exchangeRateProvider.getKrwRate(asset.getSymbol());
        BigDecimal bufferRate = policyService.getRate(PolicyKey.CRYPTO_BUFFER_RATE);

        CryptoQuote quote = CryptoQuote.issue(
                asset.getAssetId(), marketRate, bufferRate,
                payment.getKrwAmount(), now, resolveExpiry(payment, now));
        cryptoQuoteRepository.save(quote);

        log.info("Quote 발급 payment={} asset={} krw={} applied={} amount={} expires={}",
                paymentId, asset.getSymbol(), quote.getKrwAmount(),
                quote.getAppliedRate(), quote.getAssetAmount(), quote.getExpiresAt());

        return CryptoQuoteResponse.of(quote, asset, receiveWallet, paymentId, now);
    }

    /**
     * Quote 만료 시각 = min(정책 TTL, 슬롯 만료).
     *
     * <p>FR-PAY-24는 Quote와 슬롯 유효시간을 같게 관리하라고 한다. 정책값을 그대로
     * 더하면 슬롯이 얼마 남지 않은 시점에 발급된 Quote가 슬롯보다 오래 살아남는다.
     * 그 상태에서 구매자가 송금하면, 자리는 이미 남에게 넘어갔는데 입금은 도착하는
     * 최악의 경우가 된다(FR-SLT-10 환불 대상). 이른 쪽으로 맞춰 애초에 막는다.
     */
    private LocalDateTime resolveExpiry(Payment payment, LocalDateTime now) {
        Duration ttl = policyService.getSeconds(PolicyKey.QUOTE_TTL_SEC);
        LocalDateTime byPolicy = now.plus(ttl);
        LocalDateTime slotExpiresAt = payment.getSlotExpiresAt();
        return byPolicy.isBefore(slotExpiresAt) ? byPolicy : slotExpiresAt;
    }

    /**
     * 수취 지갑을 찾는다.
     *
     * <p>V1 시드는 영주소를 넣어 두므로 실제 배포 주소로 바꾸기 전에는 발급을 막는다.
     * 그러지 않으면 구매자가 소각 주소로 송금하고 자금이 영구히 사라진다.
     */
    private CryptoWallet findReceiveWallet(long chainId) {
        CryptoWallet wallet = cryptoWalletRepository
                .findFirstByChainIdAndPurposeAndActiveTrue(chainId, CryptoWallet.PURPOSE_RECEIVE)
                .orElseThrow(() -> {
                    log.error("수취 지갑이 없음 chainId={}", chainId);
                    return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                            "수취 지갑이 설정되어 있지 않습니다.");
                });

        if (wallet.isPlaceholder()) {
            log.error("수취 지갑이 아직 플레이스홀더 주소임 walletId={}", wallet.getWalletId());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "수취 지갑 주소가 아직 설정되지 않았습니다.");
        }
        return wallet;
    }
}
