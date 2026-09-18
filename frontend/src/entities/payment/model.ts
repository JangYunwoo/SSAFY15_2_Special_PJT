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
