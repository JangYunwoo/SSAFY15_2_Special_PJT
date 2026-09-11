import { z } from 'zod'

/**
 * 응답 검증 스키마.
 *
 * 모든 응답에 스키마를 만들지는 않습니다. 실제로 어긋났던 곳과 백엔드가
 * 이미 구현해 곧 붙일 곳부터 덮습니다. 새 도메인이 붙을 때 하나씩 늘리면 됩니다.
 *
 * 식별자는 문자열로 오기를 기대하지만(명세서 1.5) 공동구매 응답은 아직
 * JSON number 로 옵니다. 그 사실이 로그에 드러나도록 id 는 문자열만 허용합니다.
 */

const id = z.string()
const isoDateTime = z.string()

/* ---------- 인증 ---------- */

export const actorSchema = z.object({
  id,
  role: z.enum(['USER', 'ADMIN']),
  isSeller: z.boolean(),
  name: z.string(),
})

export const authTokensSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  tokenType: z.literal('Bearer'),
  expiresIn: z.number(),
  actor: actorSchema,
})

/* ---------- 공동구매 ---------- */

export const groupBuySummarySchema = z.object({
  id,
  productId: id,
  productName: z.string(),
  status: z.enum(['RECRUITING', 'FULFILLED', 'FAILED', 'CANCELED']),
  unitShareAmount: z.number(),
  shippingFee: z.number(),
  personalPaymentAmount: z.number(),
  targetCount: z.number(),
  paidCount: z.number(),
  participationRate: z.number(),
  recruitEndAt: isoDateTime,
})

export const amountPreviewSchema = z.object({
  productPrice: z.number(),
  targetCount: z.number(),
  unitShareAmount: z.number(),
  roundingSurplus: z.number(),
  shippingFee: z.number(),
  platformFeeRate: z.number(),
  estimatedPlatformFee: z.number(),
  estimatedSettlementAmount: z.number(),
  feePolicy: z.enum(['RATE', 'FLAT']),
})

/* ---------- 결제 ---------- */

export const paymentSchema = z.object({
  paymentId: id,
  groupBuyId: id,
  status: z.enum(['PROCESSING', 'PAID', 'FAILED', 'EXPIRED', 'REFUNDED']),
  method: z.enum(['CARD', 'KAKAOPAY', 'CRYPTO']),
  amount: z.object({
    shareAmount: z.number(),
    shippingFee: z.number(),
    totalAmount: z.number(),
    currency: z.literal('KRW'),
  }),
  slotExpiresAt: isoDateTime,
  remainingSeconds: z.number(),
})

export const cryptoStatusSchema = z.object({
  paymentId: id,
  phase: z.enum(['AWAITING_TX', 'AWAITING_RECEIPT', 'CONFIRMING', 'COMPLETED', 'FAILED']),
  confirmations: z.number(),
  requiredConfirmations: z.number(),
  txHash: z.string().nullable(),
  failReason: z.string().nullable(),
})

/* ---------- 에스크로 ---------- */

export const escrowSchema = z.object({
  escrowId: id,
  participationId: id,
  groupBuyId: id,
  amount: z.number(),
  status: z.enum(['HELD', 'RELEASED', 'REFUNDED']),
  heldAt: isoDateTime,
  releasedAt: isoDateTime.nullable(),
  refundedAt: isoDateTime.nullable(),
})

/* ---------- 상품·카테고리 ---------- */

export const categorySchema = z.object({
  categoryId: id,
  name: z.string(),
  depth: z.number(),
})

export const productSchema = z.object({
  id,
  name: z.string(),
  categoryId: id,
  categoryName: z.string(),
  price: z.number(),
})
