/**
 * 코인 결제 입력 검증.
 *
 * 백엔드 CryptoTxSubmitRequest 가 같은 정규식으로 막고 있어, 형식이 틀리면
 * 요청 자체가 400 으로 거절됩니다. 보내기 전에 화면에서 먼저 알려줍니다.
 */

/** 지갑 주소: 0x + 40자리 16진수 */
const WALLET = /^0x[0-9a-fA-F]{40}$/
/** 트랜잭션 해시: 0x + 64자리 16진수 */
const TX_HASH = /^0x[0-9a-fA-F]{64}$/

export function isWalletAddress(value: string): boolean {
  return WALLET.test(value.trim())
}

export function isTxHash(value: string): boolean {
  return TX_HASH.test(value.trim())
}
