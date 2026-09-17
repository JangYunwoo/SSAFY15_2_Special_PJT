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
 * 백엔드는 전용 DTO 없이 PAY-01/02 와 같은 PaymentResponse 를 페이지로 돌려줍니다
 * (PaymentService.listHistory). 그래서 목록에 있으면 좋을 상품명과 결제 시각이
 * 응답에 없습니다. 백엔드에 추가를 요청해 둔 상태입니다.
 */
export type PaymentHistoryItem = Payment

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
  asset: string
  chainId: number
  krwAmount: Krw
  marketRate: Decimal
  bufferRate: Decimal
  appliedRate: Decimal
  assetAmount: Decimal
  depositAddress: string
  requiredConfirmations: number
  expiresAt: IsoDateTime
}

export interface CryptoTransactionRequest {
  quoteId: Id
  txHash: string
  fromAddress: string
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
