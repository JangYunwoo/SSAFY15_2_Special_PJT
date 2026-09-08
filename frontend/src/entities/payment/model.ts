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

/** PAY-04 결제 이력 */
export interface PaymentHistoryItem {
  paymentId: Id
  groupBuyId: Id
  productName: string
  method: PaymentMethod
  status: PaymentStatus
  totalAmount: Krw
  paidAt: IsoDateTime | null
  createdAt: IsoDateTime
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
  id: Id
  symbol: string
  name: string
  chainId: number
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

/** CRYP-04 입금·컨펌 상태 */
export interface CryptoStatus {
  paymentId: Id
  state: 'AWAITING_DEPOSIT' | 'CONFIRMING' | 'MATCHED' | 'MISMATCHED' | 'EXPIRED'
  confirmations: number
  requiredConfirmations: number
  receivedAmount: Decimal | null
  expectedAmount: Decimal
  txHash: string | null
}
