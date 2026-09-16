package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.payment.crypto.entity.CryptoAsset;
import com.gongguhan.domain.payment.crypto.entity.CryptoPayment;
import com.gongguhan.domain.payment.crypto.repository.CryptoAssetRepository;
import com.gongguhan.domain.payment.crypto.repository.CryptoPaymentRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 코인 환불 송금 (S15P21B205-80, FR-RFD-03).
 *
 * <h2>정책 — 원화로 보관하고, 환불 시점 환율로 코인을 돌려준다</h2>
 *
 * <p>구매자가 코인으로 결제하면 <b>즉시 원화로 환산해 보관</b>합니다. 환불할 때는 그
 * 원화 가치에 해당하는 코인을 지급합니다.
 *
 * <pre>
 *   결제  100 USDT × 1,380원 = 138,000원 보관
 *   환불  환율 1,500원 -> 138,000 ÷ 1,500 =  92 USDT 지급
 *         환율 1,200원 -> 138,000 ÷ 1,200 = 115 USDT 지급
 * </pre>
 *
 * <p><b>플랫폼은 원화 기준으로 항상 정확하고, 구매자가 코인 가격 변동을 집니다.</b>
 * 100 USDT 를 내고 92 USDT 를 받으면 원화 가치가 같아도 손해로 느끼므로, 환불 화면에
 * "환불 시점 환율 기준" 을 표시해야 분쟁이 줄어듭니다(프론트 협의 사항).
 *
 * <h2>계산 규칙</h2>
 *
 * <table border="1">
 *   <caption>결정 사항</caption>
 *   <tr><th>항목</th><th>규칙</th><th>이유</th></tr>
 *   <tr><td>Buffer</td><td><b>적용하지 않음</b></td>
 *       <td>결제 때 이미 0.3% 를 받았습니다. 환불에 또 적용하면 이중 징수입니다</td></tr>
 *   <tr><td>반올림</td><td><b>내림</b>({@code decimals} 자리 절단)</td>
 *       <td>올리면 보관액보다 많이 내보냅니다. 잔여는 플랫폼에 남습니다</td></tr>
 *   <tr><td>시세 조회 실패</td><td>송금하지 않고 실패</td>
 *       <td>낡은 환율로 보내면 금액이 틀어집니다. 결제 Quote 와 같은 원칙입니다</td></tr>
 * </table>
 *
 * <h2>수취 주소</h2>
 *
 * <p><b>구매자가 결제 때 송금한 지갑({@code crypto_payments.from_address})으로만
 * 돌려보냅니다.</b> 사용자가 입력한 주소로 보내면, 계정이 탈취됐을 때 공격자가 환불을
 * 자기 지갑으로 빼돌릴 수 있습니다. 결제 시점에 체인으로 검증된 주소가 가장 안전합니다.
 */
@Service
public class CryptoRefundService {

    private static final Logger log = LoggerFactory.getLogger(CryptoRefundService.class);

    /** 환율 나눗셈의 중간 정밀도. 최종 수량은 {@code decimals} 자리에서 내림합니다. */
    private static final int RATE_DIVISION_SCALE = 18;

    private final CryptoPaymentRepository cryptoPaymentRepository;
    private final CryptoAssetRepository cryptoAssetRepository;
    private final ExchangeRateProvider exchangeRateProvider;
    private final ChainTransferClient transferClient;

    public CryptoRefundService(CryptoPaymentRepository cryptoPaymentRepository,
                               CryptoAssetRepository cryptoAssetRepository,
                               ExchangeRateProvider exchangeRateProvider,
                               ChainTransferClient transferClient) {
        this.cryptoPaymentRepository = cryptoPaymentRepository;
        this.cryptoAssetRepository = cryptoAssetRepository;
        this.exchangeRateProvider = exchangeRateProvider;
        this.transferClient = transferClient;
    }

    /** 코인 환불을 보낼 수 있는 상태인지. 개인키가 없으면 false. */
    public boolean isEnabled() {
        return transferClient.isEnabled();
    }

    /**
     * 구매자가 결제 때 송금한 지갑 주소.
     *
     * <p>송금하기 <b>전에</b> 원장에 기록하려고 따로 꺼냅니다. 송금 직후 장애가 나도
     * 어디로 보냈는지 남아 있어야 체인에서 대조할 수 있습니다.
     */
    public String findBuyerWallet(Long paymentId) {
        return loadPayment(paymentId).getFromAddress();
    }

    /**
     * 결제 건에 대한 코인 환불을 송금합니다.
     *
     * <p>외부 호출이라 트랜잭션 밖에서 실행합니다. 결과 기록은 호출 측이 짧은
     * 트랜잭션으로 합니다.
     *
     * @param paymentId 환불 대상 결제
     * @param krwAmount 환불할 원화 금액. 부분 환불이면 그 금액입니다
     * @return 송금 결과. 기록에 필요한 해시와 실제 지급 수량
     * @throws BusinessException 시세 조회 실패, 송금 실패, 결과 불명
     */
    public RefundTransfer refundByPayment(Long paymentId, long krwAmount) {
        CryptoPayment payment = loadPayment(paymentId);

        CryptoAsset asset = cryptoAssetRepository.findById(payment.getAssetId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "결제에 쓰인 자산을 찾을 수 없습니다. assetId=" + payment.getAssetId()));

        return transfer(asset, payment.getFromAddress(), krwAmount);
    }

    /**
     * 결제에 붙이지 못한 입금을 돌려보냅니다 (FR-PAY-26).
     *
     * <p>금액 불일치·기간 만료로 어느 결제에도 매칭되지 않은 입금입니다. 참여 건이
     * 없으므로 보낸 지갑으로 그대로 되돌립니다.
     *
     * @param toAddress 되돌려 보낼 주소. 입금을 보낸 지갑입니다
     */
    public RefundTransfer refundUnmatchedDeposit(Long assetId, String toAddress, long krwAmount) {
        CryptoAsset asset = cryptoAssetRepository.findById(assetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                        "자산을 찾을 수 없습니다. assetId=" + assetId));
        return transfer(asset, toAddress, krwAmount);
    }

    private CryptoPayment loadPayment(Long paymentId) {
        return cryptoPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "코인 결제 건을 찾을 수 없습니다. paymentId=" + paymentId));
    }

    private RefundTransfer transfer(CryptoAsset asset, String toAddress, long krwAmount) {
        if (toAddress == null || toAddress.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "환불 수취 주소가 없습니다. asset=" + asset.getSymbol());
        }
        if (krwAmount <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "환불 금액은 0보다 커야 합니다. amount=" + krwAmount);
        }

        BigDecimal rate = currentRate(asset.getSymbol());
        BigDecimal assetAmount = toAssetAmount(krwAmount, rate, asset.getDecimals());
        BigInteger rawAmount = asset.toRawAmount(assetAmount);

        if (rawAmount.signum() <= 0) {
            // 환율이 너무 높아 최소 단위에도 못 미친다. 보내봐야 0 이 나간다.
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "환불 금액이 토큰 최소 단위보다 작습니다. krw=%d rate=%s"
                            .formatted(krwAmount, rate.toPlainString()));
        }

        log.info("코인 환불 계산 krw={} 환율={} 수량={} raw={} to={}",
                krwAmount, rate.toPlainString(), assetAmount.toPlainString(), rawAmount, toAddress);

        String txHash = transferClient.transferToken(
                asset.getTokenContract(), toAddress, rawAmount);
        return new RefundTransfer(txHash, assetAmount, rate, toAddress);
    }

    /**
     * 환불 시점의 시장 환율.
     *
     * <p>Buffer 를 적용하지 않습니다 — 결제 때 이미 받았으므로 환불에 또 적용하면
     * 이중 징수입니다.
     *
     * <p>조회에 실패하면 예외가 그대로 올라갑니다. 낡은 환율로 보내면 금액이 틀어지므로
     * 캐시된 값을 재사용하지 않는 것이 결제 Quote 와 같은 원칙입니다.
     */
    private BigDecimal currentRate(String symbol) {
        BigDecimal rate = exchangeRateProvider.getKrwRate(symbol);
        if (rate == null || rate.signum() <= 0) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE,
                    "환율을 조회하지 못해 코인 환불을 보낼 수 없습니다. symbol=" + symbol);
        }
        return rate;
    }

    /**
     * 원화를 토큰 수량으로 환산합니다.
     *
     * <p><b>내림</b>합니다. 올리면 보관한 원화보다 많은 코인을 내보내게 되고, 그 차이가
     * 쌓이면 플랫폼이 손실을 떠안습니다. 잘려나간 잔여는 플랫폼에 남습니다.
     */
    private BigDecimal toAssetAmount(long krwAmount, BigDecimal rate, int decimals) {
        return BigDecimal.valueOf(krwAmount)
                .divide(rate, RATE_DIVISION_SCALE, RoundingMode.DOWN)
                .setScale(decimals, RoundingMode.DOWN);
    }

    /**
     * 송금 결과.
     *
     * @param assetAmount 실제로 보낸 토큰 수량. 원장에 증빙으로 남깁니다
     * @param appliedRate 적용한 환율. 나중에 "왜 이 수량인가" 를 되짚는 근거입니다
     */
    public record RefundTransfer(String txHash, BigDecimal assetAmount,
                                 BigDecimal appliedRate, String toAddress) {
    }
}
