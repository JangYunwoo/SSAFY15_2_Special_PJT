import type { Id, IsoDateTime, Krw, PageParams } from '@/shared/api/types'
import type { SettlementStatus } from '@/entities/model'

/** STL-01 판매자 정산 목록 */
export interface SettlementSummary {
  settlementId: Id
  groupBuyId: Id
  productName: string
  status: SettlementStatus
  targetCount: number
  paidCount: number
  confirmedCount: number
  netAmount: Krw
  /** 배송완료 + 7일. 이 시각 전에는 정산이 실행되지 않습니다. */
  settlementEligibleAt: IsoDateTime | null
  paidOutAt: IsoDateTime | null
}

/** STL-02 정산 상세 */
export interface SettlementDetail extends SettlementSummary {
  sellerId: Id
  shareAmountTotal: Krw
  shippingFeeTotal: Krw
  roundingSurplus: Krw
  platformFeeRate: number
  platformFee: Krw
  items: SettlementItem[]
  holdReason: string | null
}

export interface SettlementItem {
  participationId: Id
  included: boolean
  amount?: Krw
  /** 제외된 이유. 예) RETURNED */
  excludedReason?: string
}

export interface SettlementListParams extends PageParams {
  status?: SettlementStatus
}

/** PEN-01 판매자 위반 이력 */
export interface Penalty {
  id: Id
  reasonCode: string
  reasonLabel: string
  groupBuyId: Id | null
  status: 'CANDIDATE' | 'CONFIRMED' | 'DISMISSED' | 'REVOKED'
  occurredAt: IsoDateTime
  confirmedAt: IsoDateTime | null
}

export interface PenaltyOverview {
  /** 확정 누적 횟수. 3회면 신규 등록·생성이 차단됩니다. */
  confirmedCount: number
  suspended: boolean
  items: Penalty[]
}
