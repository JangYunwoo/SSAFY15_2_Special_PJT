package com.gongguhan.domain.payment.crypto.service;

import java.math.BigDecimal;

/**
 * 디지털 자산 시세 조회 (IR-03).
 *
 * <p>거래소를 바꾸거나 테스트에서 고정 환율을 쓰려면 이 인터페이스만 갈아끼운다.
 */
public interface ExchangeRateProvider {

    /**
     * 자산 1개당 원화 시세를 돌려준다.
     *
     * @param symbol 자산 심볼 (예: {@code USDT})
     * @return KRW / 자산 1개
     * @throws com.gongguhan.global.error.BusinessException 조회 실패 시
     *         {@code EXTERNAL_SERVICE_UNAVAILABLE}. 낡은 값으로 견적을 내지 않는다.
     */
    BigDecimal getKrwRate(String symbol);
}
