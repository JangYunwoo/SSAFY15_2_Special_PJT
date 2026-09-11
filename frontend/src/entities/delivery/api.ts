import { api } from '@/shared/api/client'
import type { Id, NoContent } from '@/shared/api/types'

import type { Delivery, DeliveryBoard, InvoiceInput } from './model'
import type { DeliveryStatus } from '@/entities/model'

/** 배송 API (DLV-01 ~ DLV-05) */
export const deliveryApi = {
  /** DLV-01 참여자별 배송 목록·송장 등록률 */
  board: (groupBuyId: Id) => api.get<DeliveryBoard>(`/sellers/group-buys/${groupBuyId}/deliveries`),

  /** DLV-02 송장 등록. 동일 송장번호 중복 등록은 거부됩니다. */
  registerInvoice: (deliveryId: Id, body: InvoiceInput) =>
    api.put<Delivery>(`/sellers/deliveries/${deliveryId}/invoice`, body),

  /** DLV-03 참여자별 배송 상태 변경 */
  updateStatus: (deliveryId: Id, status: DeliveryStatus) =>
    api.patch<Delivery>(`/sellers/deliveries/${deliveryId}/status`, { status }),

  /** DLV-04 배송 상태 일괄 변경 */
  updateStatusBulk: (groupBuyId: Id, status: DeliveryStatus, deliveryIds: Id[]) =>
    api.patch<NoContent>(`/sellers/group-buys/${groupBuyId}/deliveries/status`, {
      status,
      deliveryIds,
    }),

  /** DLV-05 구매자 배송 조회 */
  forParticipation: (participationId: Id) =>
    api.get<Delivery>(`/users/me/participations/${participationId}/delivery`),
} as const
