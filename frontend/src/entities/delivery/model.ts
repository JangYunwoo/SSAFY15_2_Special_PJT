import type { Id, IsoDateTime } from '@/shared/api/types'
import type { DeliveryStatus } from '@/entities/model'

/** DLV-01 참여자별 배송 */
export interface Delivery {
  id: Id
  participationId: Id
  recipientName: string
  recipientPhone: string
  zipcode: string
  address1: string
  address2: string
  deliveryRequest?: string | null
  status: DeliveryStatus
  carrierCode: string | null
  carrierName: string | null
  trackingNumber: string | null
  shippedAt: IsoDateTime | null
  deliveredAt: IsoDateTime | null
}

export interface DeliveryBoard {
  items: Delivery[]
  /** 송장 등록률 0~1 */
  invoiceRegisteredRate: number
  totalCount: number
  registeredCount: number
}

/** DLV-02 송장 등록 */
export interface InvoiceInput {
  carrierCode: string
  trackingNumber: string
  shippedAt: IsoDateTime
}

/** 실시간 택배사 API 는 호출하지 않고 코드만 저장합니다 (명세서 9장). */
export const CARRIERS = [
  { code: 'CJ', name: 'CJ대한통운' },
  { code: 'HANJIN', name: '한진택배' },
  { code: 'LOTTE', name: '롯데택배' },
  { code: 'POST', name: '우체국택배' },
  { code: 'LOGEN', name: '로젠택배' },
] as const

export type CarrierCode = (typeof CARRIERS)[number]['code']
