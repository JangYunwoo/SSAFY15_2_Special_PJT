import type { Id, IsoDateTime, Krw, PageParams } from '@/shared/api/types'
import type { ReturnReasonType, ReturnStatus, ShippingFeeBearer } from '@/entities/model'

/** RTN-01 반품 미리보기 요청 */
export interface ReturnPreviewRequest {
  reasonType: ReturnReasonType
  detailReason: string
}

/**
 * RTN-01 응답.
 * 단순변심이면 최초 배송비를 환불하지 않고 반품 배송비는 공동구매 배송비의 2배입니다.
 * 판매자 귀책이면 최초 배송비까지 전액 환불하고 반품비는 판매자가 부담합니다.
 */
export interface ReturnPreview {
  eligible: boolean
  /** 왜 불가한지 (eligible=false 일 때) */
  ineligibleReason: string | null
  applicationDeadline: IsoDateTime
  paymentAmount: Krw
  refundableShareAmount: Krw
  refundableOriginalShippingFee: Krw
  returnShippingFee: Krw
  refundAmount: Krw
  shippingFeeBearer: ShippingFeeBearer
}

export interface ReturnRequestInput extends ReturnPreviewRequest {
  /** 수거지 */
  pickupZipcode: string
  pickupAddress1: string
  pickupAddress2: string
}

export interface ReturnRequestSummary {
  id: Id
  participationId: Id
  groupBuyId: Id
  productName: string
  buyerName: string
  reasonType: ReturnReasonType
  detailReason: string
  status: ReturnStatus
  refundAmount: Krw
  returnShippingFee: Krw
  shippingFeeBearer: ShippingFeeBearer
  requestedAt: IsoDateTime
  /** 판매자 무응답 시 자동 승인되는 시각 */
  autoApproveAt: IsoDateTime | null
  /** 구매확정 후 접수된 건. 관리자 검토 대상입니다. */
  postConfirm: boolean
  adminReviewRequired: boolean
}

export interface ReturnRequestDetail extends ReturnRequestSummary {
  pickupZipcode: string
  pickupAddress1: string
  pickupAddress2: string
  rejectReason: string | null
  history: { status: ReturnStatus; occurredAt: IsoDateTime; note: string | null }[]
}

export interface ReturnListParams extends PageParams {
  status?: ReturnStatus
  groupBuyId?: Id
}
