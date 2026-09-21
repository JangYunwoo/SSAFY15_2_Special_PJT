import type { Decimal, Id, IsoDateTime, Krw, PageParams } from '@/shared/api/types'
import type { PaymentMethod, PaymentStatus } from '@/entities/model'

export interface PaymentAmount {
  shareAmount: Krw
  shippingFee: Krw
  totalAmount: Krw
  currency: 'KRW'
}

/** PAY-01 결제 준비 요청 */
export interface PreparePaymentRequest {
  /** null/생략은 주소 기본값, 빈 문자열은 배송 요청 없음. */
  deliveryRequest?: string | null
  method: PaymentMethod
  addressId: Id
  /** 코인 결제일 때만 채웁니다. */
  cryptoAssetId: Id | null
}

/**
 * PAY-01 / PAY-02 응답.
 * 별도의 슬롯 리소스는 없고 status=PROCESSING 인 결제가 슬롯 역할을 합니다.
 */
export interface Payment {
  paymentId: Id
  groupBuyId: Id
  status: PaymentStatus
  method: PaymentMethod
  amount: PaymentAmount
  /** 이 시각까지 참여 자리가 보장됩니다. */
  slotExpiresAt: IsoDateTime
  remainingSeconds: number
  /** 승인 완료 시 생성된 참여 */
  participationId: Id | null
}

/**
 * PAY-04 결제 이력.
 *
 * 원승인 필드와 참여 환불 원장 요약을 함께 반환합니다.
 * PAID는 승인 이력이고 refund는 현재 반환 상태이므로 둘을 구분합니다.
 */
export interface PaymentHistoryItem extends Payment {
  /** 구 배포본과 함께 쓸 때 필드가 없을 수 있다. 없는 값을 환불 없음으로 단정하지 않는다. */
  refund?: { status: 'NONE' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'UNKNOWN'; completedAmount: Krw }
}

export type PaymentHistoryParams = PageParams

/* ---------- CARD-01 ---------- */

/**
 * 카드 인증정보. 외부 승인 요청에만 쓰고 저장·로그를 남기지 않습니다 (명세서 6장).
 * 그래서 상태에 담지 않고 제출 직전에만 만들어 넘깁니다.
 */
export interface CardApproveRequest {
  cardNumber: string
  cvc: string
}

/* ---------- KAKA-01 ~ KAKA-04 ---------- */

export interface KakaoPayReady {
  paymentId: Id
  tid: string
  nextRedirectPcUrl: string
  expiresAt: IsoDateTime
}

export interface KakaoPayApproveRequest {
  paymentId: Id
  pgToken: string
  /** 결제 ID·구매자 ID·만료시각을 서명한 일회용 값 */
  state: string
}

/* ---------- CRYP-01 ~ CRYP-04 ---------- */

export interface CryptoAsset {
  assetId: Id
  symbol: string
  chainId: number
  chainName: string
  tokenContract: string
  decimals: number
  requiredConfirmations: number
}

/** CRYP-02 Quote */
export interface CryptoQuote {
  quoteId: Id
  paymentId: Id
  asset: string
  chainId: number
  /** 이 토큰 컨트랙트로 transfer 를 호출합니다. */
  tokenContract: string
  krwAmount: Krw
  marketRate: Decimal
  bufferRate: Decimal
  appliedRate: Decimal
  assetAmount: Decimal
  /**
   * 실제로 보낼 최소 단위 정수. 서버가 정한 값을 그대로 씁니다.
   *
   * assetAmount x 10^decimals 를 프론트에서 다시 계산하면 부동소수점 때문에
   * 1 wei 어긋날 수 있고, 백엔드는 정확히 일치하는 입금만 통과시킵니다.
   */
  rawAmount: string
  decimals: number
  depositAddress: string
  requiredConfirmations: number
  expiresAt: IsoDateTime
}
export interface CryptoTransactionRequest {
  quoteId: Id
  txHash: string
  fromAddress: string
  challengeId: string
  signature: string
}

export type CryptoWalletChallengeRequest = Pick<CryptoTransactionRequest, 'quoteId' | 'txHash' | 'fromAddress'>
export interface CryptoWalletChallenge {
  challengeId: string
  message: string
  expiresAt: IsoDateTime
  chainId: number
}

/**
 * CRYP-04 입금·컨펌 진행 단계 (백엔드 CryptoPaymentStatusResponse.phase).
 *
 * 해시를 등록해도 결제 완료가 아닙니다. 필요 컨펌 수에 도달해야 확정되므로
 * COMPLETED 나 FAILED 가 나올 때까지 상태를 조회해야 합니다.
 */
export const CRYPTO_PHASE = [
  /** 아직 송금하지 않았거나 해시를 보내지 않음 */
  'AWAITING_TX',
  /** 해시는 받았으나 노드가 아직 트랜잭션을 모름 */
  'AWAITING_RECEIPT',
  /** 영수증 확인됨. 컨펌 대기 중 */
  'CONFIRMING',
  /** 컨펌 충족 + 결제 확정 완료 */
  'COMPLETED',
  /** revert · 금액 불일치 · 슬롯 만료 등으로 종료 */
  'FAILED',
] as const
export type CryptoPhase = (typeof CRYPTO_PHASE)[number]

export const CRYPTO_PHASE_LABEL: Record<CryptoPhase, string> = {
  AWAITING_TX: '송금 대기',
  AWAITING_RECEIPT: '트랜잭션 확인 중',
  CONFIRMING: '블록 컨펌 대기',
  COMPLETED: '결제 완료',
  FAILED: '결제 실패',
}

/** CRYP-04 입금·컨펌 상태 (백엔드 CryptoPaymentStatusResponse) */
export interface CryptoStatus {
  paymentId: Id
  phase: CryptoPhase
  paymentStatus: PaymentStatus
  txHash: string | null
  /** 체인 영수증 상태 (SUCCESS / REVERTED 등) */
  receiptStatus: string | null
  blockNumber: number | null
  confirmations: number
  requiredConfirmations: number
  /** 실제로 들어온 수량 */
  assetAmount: Decimal | null
  rawAmount: Decimal | null
  depositAddress: string | null
  confirmedAt: IsoDateTime | null
  /** FAILED 일 때만 채워집니다 */
  failReason: string | null
}

