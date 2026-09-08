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

/** ESC-01 에스크로 */
export interface Escrow {
  participationId: Id
  status: EscrowStatus
  amount: Krw
  heldAt: IsoDateTime
  releasedAt: IsoDateTime | null
}

/** CFM-02 이의제기 */
export interface DisputeRequest {
  type: 'NOT_DELIVERED' | 'ITEM_MISMATCH'
  detailReason: string
}
