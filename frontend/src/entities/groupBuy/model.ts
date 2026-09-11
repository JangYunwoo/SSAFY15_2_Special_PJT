import type { Id, IsoDateTime, Krw, PageParams } from '@/shared/api/types'
import type { GroupBuyStatus } from '@/entities/model'

/** GRP-01 공개 목록의 카드 한 장 */
export interface GroupBuySummary {
  id: Id
  productId: Id
  productName: string
  /** 대표 이미지. 없으면 회색 플레이스홀더를 씁니다. */
  thumbnailUrl: string | null
  sellerName: string
  categoryId: Id
  categoryName: string
  status: GroupBuyStatus
  /** 1인 기준 정상가 (할인 폭 표시용) */
  listPrice: Krw
  /** 공동구매 1회분 상품 총액 */
  productPrice: Krw
  unitShareAmount: Krw
  shippingFee: Krw
  /** 1인이 실제로 결제하는 금액. 서버가 확정하므로 화면에서 다시 더하지 않습니다. */
  personalPaymentAmount: Krw
  targetCount: number
  paidCount: number
  /** 모집 진행률 0~1 */
  participationRate: number
  recruitEndAt: IsoDateTime
  createdAt: IsoDateTime
}

/** GRP-02 상세 */
export interface GroupBuyDetail extends GroupBuySummary {
  description: string
  images: string[]
  brand: string | null
  modelName: string | null
  color: string | null
  roundingSurplus: Krw
  recruitStartAt: IsoDateTime
  /** 청약철회가 제한된 상품인지 */
  withdrawalRestricted: boolean
  /** 청약철회 제한 상품이면 사유가 들어옵니다 (명세서 3장) */
  withdrawalRestrictionReason: string | null
  /** 판매자 제재로 노출이 제한된 건 (FR-PEN-14) */
  sellerSuspended: boolean
}

export const GROUP_BUY_SORT = [
  'popular',
  'deadline',
  'newest',
  'oldest',
  'priceAsc',
  'priceDesc',
] as const
export type GroupBuySort = (typeof GROUP_BUY_SORT)[number]

export const GROUP_BUY_SORT_LABEL: Record<GroupBuySort, string> = {
  popular: '인기순',
  deadline: '마감 임박순',
  newest: '최신순',
  oldest: '오래된순',
  priceAsc: '낮은 가격순',
  priceDesc: '높은 가격순',
}

export interface GroupBuyListParams extends PageParams {
  keyword?: string
  categoryId?: Id
  status?: GroupBuyStatus
  sort?: GroupBuySort
}

/** GRP-03 금액 계산 응답 */
export interface AmountPreview {
  productPrice: Krw
  targetCount: number
  unitShareAmount: Krw
  roundingSurplus: Krw
  shippingFee: Krw
  platformFeeRate: number
  estimatedPlatformFee: Krw
  estimatedSettlementAmount: Krw
  feePolicy: 'RATE' | 'FLAT'
}

export interface GroupBuyPreviewRequest {
  productId: Id
  productPrice: Krw
  targetCount: number
  shippingFee: Krw
}

export interface CreateGroupBuyRequest extends GroupBuyPreviewRequest {
  recruitStartAt: IsoDateTime
  recruitEndAt: IsoDateTime
}

/** GRP-05 판매자 목록 */
export interface SellerGroupBuySummary {
  id: Id
  productId: Id
  productName: string
  status: GroupBuyStatus
  targetCount: number
  paidCount: number
  confirmedCount: number
  productPrice: Krw
  unitShareAmount: Krw
  shippingFee: Krw
  recruitEndAt: IsoDateTime
  /** 송장이 등록된 비율 (DLV-01) */
  invoiceRegisteredRate: number | null
}

/** GRP-06 판매자 운영 상세 */
export interface SellerGroupBuyDetail extends SellerGroupBuySummary {
  recruitStartAt: IsoDateTime
  roundingSurplus: Krw
  platformFeeRate: number
  escrowHeldAmount: Krw
  estimatedSettlementAmount: Krw
  returnCount: number
  canEdit: boolean
  canCancel: boolean
}

/** GRP-09 참여자 현황 */
export interface Participant {
  participationId: Id
  buyerName: string
  recipientName: string
  zipcode: string
  address1: string
  address2: string
  paidAt: IsoDateTime
  amount: Krw
  status: string
}
