/**
 * 디지털 자산(코인) 결제 도메인 - MetaMask + Polygon Amoy + ERC-20(USDT).
 *
 * <p>담당: 코인결제 담당자
 * <p>관련 API: CRYP-01~04 (공구한_API_명세서_v1.md 6장)
 * <p>관련 테이블: crypto_assets, crypto_wallets, crypto_quotes, crypto_payments,
 * crypto_deposits, chain_scan_cursors
 *
 * <p>Quote(시세+Buffer 적용 환율)는 10분 유효. 온체인 입금 감지는 (tx_hash, log_index)
 * UNIQUE 제약으로 중복 처리를 차단하고, 미매칭 입금도 무조건 crypto_deposits에 기록한다.
 * 체인 스캔 폴링 스케줄러도 이 패키지에서 관리한다.
 */
package com.gongguhan.domain.payment.crypto;
