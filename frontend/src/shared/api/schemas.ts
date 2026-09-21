import { z } from 'zod'

/**
 * 응답 검증 스키마.
 *
 * 모든 응답에 스키마를 만들지는 않습니다. 실제로 어긋났던 곳과 백엔드가
 * 이미 구현해 곧 붙일 곳부터 덮습니다. 새 도메인이 붙을 때 하나씩 늘리면 됩니다.
 *
 * 명세서 1.5에 맞춰 식별자는 문자열만 허용합니다. JavaScript의 안전 정수 범위를
 * 넘어가는 BIGINT도 손실 없이 다루기 위한 서버·클라이언트 공통 계약입니다.
 *
 * 이 모듈은 개발 빌드에서만 실행됩니다. contract.ts 가 `import()` 로만
 * 가져가므로 프로덕션 번들에는 zod 도 이 파일도 들어가지 않습니다. 그래서
 * 호출부는 스키마 객체가 아니라 아래 `schemas` 레지스트리의 이름을 넘깁니다.
 * 새 스키마를 추가할 땐 상수를 만든 뒤 맨 아래 레지스트리에도 등록하세요.
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

export const userProfileSchema = z.object({
  id,
  email: z.string(),
  nickname: z.string(),
  phone: z.string().nullable(),
  isSeller: z.boolean(),
  createdAt: isoDateTime.nullable(),
  ssafyAccountNo: z.string().nullable(),
  ssafyLinked: z.boolean(),
})

export const sellerProfileSchema = z.object({
  id,
  businessName: z.string(),
  businessPhone: z.string().nullable(),
  ssafyAccountNo: z.string().nullable(),
  accountHolder: z.string().nullable(),
  accountVerified: z.boolean(),
  returnZipcode: z.string().nullable(),
  returnAddress1: z.string().nullable(),
  returnAddress2: z.string().nullable(),
  penaltyCount: z.number(),
  status: z.string(),
})

/* ---------- 배송지 ---------- */

export const addressSchema = z.object({
  id,
  recipientName: z.string(),
  recipientPhone: z.string(),
  zipcode: z.string(),
  address1: z.string(),
  address2: z.string().nullable(),
  isDefault: z.boolean(),
  deliveryRequest: z.string().nullable(),
})

/* ---------- 참여 ---------- */

export const participationSummarySchema = z.object({
  id,
  groupBuyId: id,
  productName: z.string(),
  // entities/model.ts 의 PARTICIPATION_STATUS 와 같은 목록입니다.
  // shared 는 entities 를 import 하지 않는 규칙이라 값을 그대로 적습니다.
  status: z.enum([
    'PAID',
    'FULFILLED',
    'SHIPPING',
    'DELIVERED',
    'CONFIRMED',
    'SETTLED',
    'CANCELED',
    'RETURNING',
    'RETURNED',
    'REFUNDING',
    'REFUNDED',
  ]),
  totalAmount: z.number(),
  paidAt: isoDateTime,
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
  refund: z.object({
    status: z.enum(['NONE', 'PROCESSING', 'COMPLETED', 'FAILED', 'UNKNOWN']),
    completedAmount: z.number().nonnegative(),
  }).optional(),
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

/* ---------- 레지스트리 ----------
   호출부가 문자열로 지목할 수 있게 한곳에 모읍니다. 목록 응답은 항목 하나만
   검사하지만, 응답 자체가 배열인 엔드포인트는 배열 스키마로 등록합니다. */

export const schemas = {
  actor: actorSchema,
  authTokens: authTokensSchema,
  userProfile: userProfileSchema,
  sellerProfile: sellerProfileSchema,
  address: addressSchema,
  addressList: z.array(addressSchema),
  participationSummary: participationSummarySchema,
  groupBuySummary: groupBuySummarySchema,
  amountPreview: amountPreviewSchema,
  payment: paymentSchema,
  cryptoStatus: cryptoStatusSchema,
  escrow: escrowSchema,
  category: categorySchema,
  categoryList: z.array(categorySchema),
  product: productSchema,
} as const

/** 호출부가 넘길 수 있는 스키마 이름. 오타는 컴파일 단계에서 걸립니다. */
export type SchemaName = keyof typeof schemas

/** contract.ts 가 동적 import 결과를 담을 때 씁니다. */
export type SchemaRegistry = typeof schemas
