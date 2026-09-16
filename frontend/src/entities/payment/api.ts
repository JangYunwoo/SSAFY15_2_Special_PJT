import { api, newIdempotencyKey } from '@/shared/api/client'
import type { Id, NoContent } from '@/shared/api/types'

import type {
  CardApproveRequest,
  CryptoAsset,
  CryptoQuote,
  CryptoStatus,
  CryptoTransactionRequest,
  KakaoPayApproveRequest,
  KakaoPayReady,
  Payment,
  PaymentHistoryItem,
  PaymentHistoryParams,
  PreparePaymentRequest,
} from './model'

/** 결제 API (PAY-01 ~ CRYP-04) */
export const paymentApi = {
  /**
   * PAY-01 금액 검증·슬롯 선점·PROCESSING 결제 생성.
   * Idempotency-Key 가 필수라 호출부에서 키를 넘기지 않으면 여기서 만듭니다.
   */
  prepare: (groupBuyId: Id, body: PreparePaymentRequest, idempotencyKey = newIdempotencyKey()) =>
    api.post<Payment>(`/group-buys/${groupBuyId}/payments/prepare`, body, { idempotencyKey }),

  /** PAY-02 */
  detail: (paymentId: Id) => api.get<Payment>(`/payments/${paymentId}`, { schema: 'payment' }),

  /** PAY-03 구매자 이탈 시 슬롯 해제 */
  abort: (paymentId: Id) => api.post<NoContent>(`/payments/${paymentId}/abort`),

  /** PAY-04 */
  history: (params: PaymentHistoryParams = {}) =>
    api.getPage<PaymentHistoryItem>('/users/me/payments', {
      query: { ...params },
      schema: 'payment',
    }),

  /** CARD-01 카드 승인 및 서버 검증 */
  approveCard: (paymentId: Id, body: CardApproveRequest, idempotencyKey = newIdempotencyKey()) =>
    api.post<Payment>(`/payments/${paymentId}/card/approve`, body, { idempotencyKey }),

  /** KAKA-01 */
  kakaoReady: (paymentId: Id) =>
    api.post<KakaoPayReady>(`/payments/${paymentId}/kakaopay/ready`, undefined, {
      idempotencyKey: newIdempotencyKey(),
    }),

  /** KAKA-02 pg_token 으로 승인 후 참여 확정 */
  kakaoApprove: (body: KakaoPayApproveRequest, idempotencyKey = newIdempotencyKey()) =>
    api.post<Payment>('/payments/kakaopay/approve', body, { idempotencyKey }),

  /** KAKA-03 사용자 결제 취소 복귀 */
  kakaoCancelFlow: (paymentId: Id) =>
    api.post<NoContent>(`/payments/${paymentId}/kakaopay/cancel-flow`),

  /** KAKA-04 결제 실패 복귀 */
  kakaoFailFlow: (paymentId: Id) =>
    api.post<NoContent>(`/payments/${paymentId}/kakaopay/fail-flow`),

  /** CRYP-01 */
  cryptoAssets: () => api.get<CryptoAsset[]>('/crypto/assets'),

  /** CRYP-02 환율·Buffer 적용 Quote 생성 */
  cryptoQuote: (paymentId: Id, cryptoAssetId: Id) =>
    api.post<CryptoQuote>(`/payments/${paymentId}/crypto/quote`, { cryptoAssetId }),

  /** CRYP-03 트랜잭션 해시 등록 */
  submitCryptoTx: (paymentId: Id, body: CryptoTransactionRequest) =>
    api.post<CryptoStatus>(`/payments/${paymentId}/crypto/transactions`, body, {
      idempotencyKey: newIdempotencyKey(),
    }),

  /** CRYP-04 */
  cryptoStatus: (paymentId: Id) =>
    api.get<CryptoStatus>(`/payments/${paymentId}/crypto/status`, { schema: 'cryptoStatus' }),
} as const
