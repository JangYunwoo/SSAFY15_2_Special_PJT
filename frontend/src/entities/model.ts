/**
 * 도메인 상태값 (명세서 17장 상태 전이 규칙).
 * 서버가 대문자 Enum 문자열로 주고받습니다.
 */

export const PAYMENT_STATUS = ['PROCESSING', 'PAID', 'FAILED', 'EXPIRED', 'REFUNDED'] as const
export type PaymentStatus = (typeof PAYMENT_STATUS)[number]

export const PAYMENT_METHOD = ['CARD', 'KAKAOPAY', 'CRYPTO'] as const
export type PaymentMethod = (typeof PAYMENT_METHOD)[number]

export const GROUP_BUY_STATUS = ['RECRUITING', 'FULFILLED', 'FAILED', 'CANCELED'] as const
export type GroupBuyStatus = (typeof GROUP_BUY_STATUS)[number]

export const PARTICIPATION_STATUS = [
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
] as const
export type ParticipationStatus = (typeof PARTICIPATION_STATUS)[number]

export const ESCROW_STATUS = ['HELD', 'RELEASED', 'REFUNDED'] as const
export type EscrowStatus = (typeof ESCROW_STATUS)[number]

export const DELIVERY_STATUS = ['READY', 'SHIPPING', 'DELIVERED'] as const
export type DeliveryStatus = (typeof DELIVERY_STATUS)[number]

export const RETURN_STATUS = [
  'REQUESTED',
  'APPROVED',
  'REJECTED',
  'DISPUTED',
  'PICKED_UP',
  'REFUND_REQUESTED',
  'REFUND_FAILED',
  'REFUNDED',
] as const
export type ReturnStatus = (typeof RETURN_STATUS)[number]

export const RETURN_REASON_TYPE = [
  /** 단순변심 — 최초 배송비 미환불, 반품비 구매자 부담 */
  'CHANGE_OF_MIND',
  /** 상품 하자 — 전액 환불, 반품비 판매자 부담 */
  'DEFECT',
] as const
export type ReturnReasonType = (typeof RETURN_REASON_TYPE)[number]

/**
 * 정산 진행 상태 (백엔드 `Settlement.STATUS_*`).
 *
 * 예전에는 PARTIAL·FAILED 도 여기 있었지만 백엔드에는 세 가지뿐입니다.
 * 이체 실패는 별도 축인 {@link TRANSFER_STATUS} 로 표현됩니다.
 */
export const SETTLEMENT_STATUS = ['PENDING', 'COMPLETED', 'HOLD'] as const
export type SettlementStatus = (typeof SETTLEMENT_STATUS)[number]

/** 정산금 이체 상태 (백엔드 `Settlement.TRANSFER_*`). 정산 상태와 별개로 움직입니다. */
export const TRANSFER_STATUS = ['PENDING', 'COMPLETED', 'FAILED'] as const
export type TransferStatus = (typeof TRANSFER_STATUS)[number]

export const SHIPPING_FEE_BEARER = ['BUYER', 'SELLER'] as const
export type ShippingFeeBearer = (typeof SHIPPING_FEE_BEARER)[number]

export const RESTRICTION_REVIEW_STATUS = ['NONE', 'PENDING', 'APPROVED', 'REJECTED'] as const
export type RestrictionReviewStatus = (typeof RESTRICTION_REVIEW_STATUS)[number]

/* ---------- 화면 표기 ---------- */

import type { Display } from '@/entities/statusDisplay'

export const GROUP_BUY_STATUS_DISPLAY: Record<GroupBuyStatus, Display> = {
  RECRUITING: { label: '모집중', tone: 'live' },
  FULFILLED: { label: '모집완료', tone: 'ok' },
  FAILED: { label: '모집실패', tone: 'alert' },
  CANCELED: { label: '취소', tone: 'neutral' },
}

/** 결제 이력에서 쓰는 표기 (PAY-04) */
export const PAYMENT_STATUS_DISPLAY: Record<PaymentStatus, Display> = {
  PROCESSING: { label: '결제 진행중', tone: 'live' },
  PAID: { label: '결제 완료', tone: 'ok' },
  FAILED: { label: '승인 실패', tone: 'alert' },
  EXPIRED: { label: '시간 만료', tone: 'neutral' },
  REFUNDED: { label: '환불', tone: 'keep' },
}

export const PARTICIPATION_STATUS_DISPLAY: Record<ParticipationStatus, Display> = {
  PAID: { label: '에스크로 보관중', tone: 'keep' },
  FULFILLED: { label: '모집완료', tone: 'keep' },
  SHIPPING: { label: '배송중', tone: 'ship' },
  DELIVERED: { label: '구매확정 대기', tone: 'hold' },
  CONFIRMED: { label: '구매확정 완료', tone: 'ok' },
  SETTLED: { label: '정산 완료', tone: 'ok' },
  CANCELED: { label: '참여 취소', tone: 'neutral' },
  RETURNING: { label: '반품 진행', tone: 'back' },
  RETURNED: { label: '반품 완료', tone: 'back' },
  REFUNDING: { label: '환불 진행', tone: 'back' },
  REFUNDED: { label: '환불 완료', tone: 'back' },
}

export const DELIVERY_STATUS_DISPLAY: Record<DeliveryStatus, Display> = {
  READY: { label: '발송대기', tone: 'neutral' },
  SHIPPING: { label: '배송중', tone: 'ship' },
  DELIVERED: { label: '배송완료', tone: 'ok' },
}

export const RETURN_STATUS_DISPLAY: Record<ReturnStatus, Display> = {
  REQUESTED: { label: '반품신청', tone: 'hold' },
  APPROVED: { label: '반품승인', tone: 'hold' },
  REJECTED: { label: '반품거부', tone: 'alert' },
  DISPUTED: { label: '이의제기', tone: 'alert' },
  PICKED_UP: { label: '수거완료', tone: 'back' },
  REFUND_REQUESTED: { label: '환불요청', tone: 'back' },
  REFUND_FAILED: { label: '환불실패', tone: 'alert' },
  REFUNDED: { label: '환불완료', tone: 'ok' },
}

export const SETTLEMENT_STATUS_DISPLAY: Record<SettlementStatus, Display> = {
  PENDING: { label: '정산대기', tone: 'hold' },
  COMPLETED: { label: '정산완료', tone: 'ok' },
  HOLD: { label: '정산보류', tone: 'alert' },
}

export const TRANSFER_STATUS_DISPLAY: Record<TransferStatus, Display> = {
  PENDING: { label: '이체대기', tone: 'hold' },
  COMPLETED: { label: '이체완료', tone: 'ok' },
  FAILED: { label: '이체실패', tone: 'alert' },
}

export const PAYMENT_METHOD_LABEL: Record<PaymentMethod, string> = {
  CARD: '카드',
  KAKAOPAY: '카카오페이',
  CRYPTO: '코인',
}

export const RETURN_REASON_LABEL: Record<ReturnReasonType, string> = {
  CHANGE_OF_MIND: '단순변심',
  DEFECT: '상품 하자·오배송',
}

/** 단순변심만 구매자가 반품비를 부담합니다 (명세서 10장) */
export function isSellerFault(reason: ReturnReasonType): boolean {
  return reason !== 'CHANGE_OF_MIND'
}
