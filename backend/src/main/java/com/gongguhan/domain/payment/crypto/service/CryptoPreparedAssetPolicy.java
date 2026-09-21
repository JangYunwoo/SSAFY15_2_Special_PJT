package com.gongguhan.domain.payment.crypto.service;

import com.gongguhan.domain.payment.common.entity.Payment;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;

/** 최초 선택 자산을 나중 견적이나 클라이언트 본문으로 대체하지 않는다. */
final class CryptoPreparedAssetPolicy {
    private CryptoPreparedAssetPolicy() { }

    static void requireMatches(Payment payment, Long assetId) {
        Long original = payment.getPrepareCryptoAssetId();
        if (!Payment.METHOD_CRYPTO.equals(payment.getMethod()) || original == null
                || original <= 0 || !original.equals(assetId)) {
            throw new BusinessException(ErrorCode.PAYMENT_REQUEST_CONFLICT,
                    "최초 선택한 코인 자산과 일치하지 않거나 원선택을 확인할 수 없습니다.");
        }
    }
}
