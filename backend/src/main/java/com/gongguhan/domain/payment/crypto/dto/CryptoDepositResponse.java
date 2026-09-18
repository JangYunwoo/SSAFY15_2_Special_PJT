package com.gongguhan.domain.payment.crypto.dto;

import com.gongguhan.domain.payment.crypto.entity.CryptoDeposit;
import java.time.ZoneOffset;

/**
 * 결제에 붙지 못한 입금 응답 (FR-PAY-26).
 *
 * <p>관리자가 "이 돈을 돌려보낼지" 를 판단하는 데 필요한 것만 담습니다. 온체인
 * 데이터는 공개 정보라 주소·해시를 가리지 않습니다 — 관리자가 블록 탐색기에서 직접
 * 대조할 수 있어야 합니다.
 *
 * <p>{@code rawAmount} 와 {@code assetAmount} 를 <b>둘 다</b> 내보냅니다. 되돌려 보내는
 * 값은 {@code rawAmount}(최소 단위 정수)이고, {@code assetAmount} 는 사람이 읽기 위한
 * 값입니다. 둘 다 문자열인 이유는 자바스크립트 {@code Number} 가 배정밀도라 큰 정수와
 * 18자리 소수를 그대로 담지 못하기 때문입니다(Quote·환불 응답과 같은 이유).
 */
public record CryptoDepositResponse(
        String depositId,
        String status,
        String txHash,
        long blockNumber,
        String fromAddress,
        String tokenContract,
        String rawAmount,
        String assetAmount,
        int confirmations,
        String memo,
        String detectedAt
) {

    public static CryptoDepositResponse from(CryptoDeposit deposit) {
        return new CryptoDepositResponse(
                String.valueOf(deposit.getDepositId()),
                deposit.getStatus(),
                deposit.getTxHash(),
                deposit.getBlockNumber(),
                deposit.getFromAddress(),
                deposit.getTokenContract(),
                deposit.getRawAmount(),
                deposit.getAssetAmount().toPlainString(),
                deposit.getConfirmations(),
                deposit.getMemo(),
                deposit.getDetectedAt().atOffset(ZoneOffset.UTC).toString());
    }
}
