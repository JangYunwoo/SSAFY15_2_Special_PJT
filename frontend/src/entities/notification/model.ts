import type { Id, IsoDateTime, PageParams } from '@/shared/api/types'

/**
 * NOTI-01 알림.
 * 배송 알림은 제공하지 않고 모집·환불·정산·제재 등 거래 알림만 옵니다 (명세서 14장).
 */
export interface Notification {
  id: Id
  type:
    | 'GROUP_BUY_FULFILLED'
    | 'GROUP_BUY_FAILED'
    | 'REFUND_COMPLETED'
    | 'SETTLEMENT_COMPLETED'
    | 'PENALTY_CONFIRMED'
    | 'RETURN_STATUS_CHANGED'
  title: string
  body: string
  linkUrl: string | null
  readAt: IsoDateTime | null
  createdAt: IsoDateTime
}

export type NotificationListParams = PageParams
