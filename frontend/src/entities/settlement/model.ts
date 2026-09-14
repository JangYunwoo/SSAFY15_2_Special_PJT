import type { Id, IsoDateTime, Krw, PageParams } from '@/shared/api/types'
import type { SettlementStatus, TransferStatus } from '@/entities/model'

/**
 * STL-01·02 정산 한 건 (백엔드 `SettlementResponse`).
 *
 * 목록과 상세가 같은 레코드를 돌려줍니다. 상세는 여기에 참여 건 목록만 덧붙습니다.
 *
 * 예전 타입에는 `productName`·`targetCount`·`paidCount`·`settlementEligibleAt`
 * 이 있었지만 백엔드 응답에 없는 필드였습니다. 화면에 필요해서 백엔드에 추가를
 * 요청해 둔 상태이고, 그때까지는 공동구매 번호와 확정 건수로 대신 보여줍니다.
 */
export interface Settlement {
  settlementId: Id
  groupBuyId: Id
  sellerId: Id
  /** 정산 회차. 부분 정산이 생기면 2회차 이상이 붙습니다. */
  roundNo: number
  status: SettlementStatus
  /** 정산금 이체 상태. 정산 상태와 별개로 움직입니다. */
  transferStatus: TransferStatus
  /** 구매확정되어 이번 회차에 포함된 건수 */
  confirmedCount: number
  shareAmountTotal: Krw
  shippingFeeTotal: Krw
  /** 분담금을 나눌 때 생긴 절상 차액. 판매자 귀속입니다. */
  roundingSurplus: Krw
  platformFeeRate: number
  platformFee: Krw
  netAmount: Krw
  /** 이체가 실패했을 때만 채워집니다. */
  transferFailReason: string | null
  transferredAt: IsoDateTime | null
  createdAt: IsoDateTime
}

/** STL-02 정산 상세 (백엔드 `SettlementDetailResponse`). 정산 레코드를 중첩해서 돌려줍니다. */
export interface SettlementDetail {
  settlement: Settlement
  items: SettlementItem[]
}

export interface SettlementItem {
  participationId: Id
  included: boolean
  /** 포함된 건의 지급 기준 금액(분담금 + 배송비). 제외면 null 입니다. */
  amount: Krw | null
  /** 제외된 이유. 예) RETURNED. 포함이면 null 입니다. */
  excludedReason: string | null
}

export interface SettlementListParams extends PageParams {
  status?: SettlementStatus
}

/* ---------- PEN-01 ---------- */

/** 판매자 제재 한 건 (백엔드 `PenaltyResponse`). */
export interface Penalty {
  penaltyId: Id
  sellerId: Id
  groupBuyId: Id | null
  /** 위반 유형 코드. 표시용 문구는 백엔드가 주지 않아 프론트에서 매핑합니다. */
  reasonType: string
  detail: string | null
  status: string
  /** 관리자가 남긴 메모 */
  memo: string | null
  reviewedBy: Id | null
  detectedAt: IsoDateTime
  reviewedAt: IsoDateTime | null
}

/** PEN-01 판매자 제재 요약 (백엔드 `SellerPenaltySummaryResponse`). */
export interface PenaltyOverview {
  /** 확정 누적 횟수 */
  confirmedCount: number
  /** 이 횟수에 도달하면 정지됩니다. 예전에는 3으로 고정돼 있었습니다. */
  penaltyLimit: number
  suspended: boolean
  penalties: Penalty[]
}
