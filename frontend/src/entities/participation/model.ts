import type { Id, IsoDateTime, Krw, PageParams } from '@/shared/api/types'
import type {
  EscrowStatus,
  GroupBuyStatus,
  ParticipationStatus,
  PaymentMethod,
} from '@/entities/model'

/** PAR-01 내 참여 목록 */
export interface ParticipationSummary {
  id: Id
  groupBuyId: Id
  productName: string
  sellerName: string
  status: ParticipationStatus
  groupBuyStatus: GroupBuyStatus
  escrowStatus: EscrowStatus
  method: PaymentMethod
  shareAmount: Krw
  shippingFee: Krw
  totalAmount: Krw
  paidAt: IsoDateTime
  targetCount: number
  paidCount: number
  recruitEndAt: IsoDateTime
  deliveredAt: IsoDateTime | null
  /** 배송완료 + 7일. 이 시각이 지나면 자동 구매확정됩니다. */
  autoConfirmAt: IsoDateTime | null
  /** 청약철회 신청 기한 */
  withdrawalDeadlineAt: IsoDateTime | null
  confirmedAt: IsoDateTime | null
  /** 자동 확정이면 true */
  autoConfirmed: boolean
}

/** PAR-02 상세 · 상태 이력 */
export interface ParticipationDetail extends ParticipationSummary {
  paymentId: Id
  recipientName: string
  recipientPhone: string
  zipcode: string
  address1: string
  address2: string
  history: ParticipationHistoryEntry[]
  canCancel: boolean
  canConfirm: boolean
  canRequestReturn: boolean
}

export interface ParticipationHistoryEntry {
  status: ParticipationStatus
  occurredAt: IsoDateTime
  note: string | null
}

export interface ParticipationListParams extends PageParams {
  status?: ParticipationStatus
}

/** ESC-01 에스크로 (백엔드 EscrowResponse) */
export interface Escrow {
  escrowId: Id
  participationId: Id
  groupBuyId: Id
  amount: Krw
  currency: 'KRW'
  status: EscrowStatus
  heldAt: IsoDateTime
  /** 구매확정으로 풀린 시각 */
  releasedAt: IsoDateTime | null
  /** 환불로 돌아간 시각 */
  refundedAt: IsoDateTime | null
}

/** ESC-02 상태별 집계. 해당 상태가 없어도 0 으로 채워져 옵니다. */
export interface EscrowSummary {
  heldCount: number
  heldAmount: Krw
  releasedCount: number
  releasedAmount: Krw
  refundedCount: number
  refundedAmount: Krw
  totalCount: number
  totalAmount: Krw
  currency: 'KRW'
}

/** ESC-02 공동구매 하나의 에스크로 (판매자) */
export interface GroupBuyEscrow {
  groupBuyId: Id
  summary: EscrowSummary
  escrows: Escrow[]
}

/** CFM-02 이의제기 */
export interface DisputeRequest {
  type: 'NOT_DELIVERED' | 'ITEM_MISMATCH'
  detailReason: string
}
