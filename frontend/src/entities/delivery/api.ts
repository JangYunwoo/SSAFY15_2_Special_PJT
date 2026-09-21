import { api } from '@/shared/api/client'
import type { Id, NoContent } from '@/shared/api/types'

import { CARRIERS, type Delivery, type DeliveryBoard, type InvoiceInput } from './model'
import type { DeliveryStatus } from '@/entities/model'

/**
 * 백엔드 SellerDeliveryItemResponse / DeliveryResponse.
 *
 * 화면이 쓰는 이름과 달라 여기서 한 번 옮겨 담습니다. id 가 숫자로 오는 것도
 * 이 계층에서 문자열로 맞춥니다(다른 엔티티와 같은 규칙).
 */
interface DeliveryApiResponse {
  deliveryId: number | string
  participationId: number | string
  recipientName?: string
  recipientPhone?: string
  zipcode?: string
  address1?: string
  address2?: string
  deliveryRequest?: string | null
  participationStatus?: string
  /** 목록은 deliveryStatus, 단건 응답은 status 로 옵니다. */
  deliveryStatus?: string
  status?: string
  carrierCode: string | null
  trackingNumber: string | null
  shippedAt: string | null
  deliveredAt: string | null
}

/** 백엔드 SellerDeliveryListResponse */
interface DeliveryBoardApiResponse {
  totalCount: number
  invoiceRegisteredCount: number
  /** 0~100(%) 입니다. 화면이 쓰는 0~1 로 바꿉니다. */
  invoiceRegistrationRate: number
  deliveries: DeliveryApiResponse[]
}

/** 택배사 이름은 응답에 없어 코드로 찾습니다. 모르는 코드면 코드를 그대로 씁니다. */
function carrierNameOf(code: string | null): string | null {
  if (code === null) return null
  return CARRIERS.find((carrier) => carrier.code === code)?.name ?? code
}

function toDelivery(response: DeliveryApiResponse): Delivery {
  return {
    id: String(response.deliveryId),
    participationId: String(response.participationId),
    recipientName: response.recipientName ?? '',
    recipientPhone: response.recipientPhone ?? '',
    zipcode: response.zipcode ?? '',
    address1: response.address1 ?? '',
    address2: response.address2 ?? '',
    deliveryRequest: response.deliveryRequest ?? null,
    status: (response.deliveryStatus ?? response.status ?? 'READY') as DeliveryStatus,
    carrierCode: response.carrierCode,
    carrierName: carrierNameOf(response.carrierCode),
    trackingNumber: response.trackingNumber,
    shippedAt: response.shippedAt,
    deliveredAt: response.deliveredAt,
  }
}

function toBoard(response: DeliveryBoardApiResponse): DeliveryBoard {
  return {
    items: response.deliveries.map(toDelivery),
    totalCount: response.totalCount,
    registeredCount: response.invoiceRegisteredCount,
    // 백엔드는 퍼센트(50.0), 화면은 비율(0.5)을 씁니다.
    invoiceRegisteredRate: response.invoiceRegistrationRate / 100,
  }
}

/** 배송 API (DLV-01 ~ DLV-05) */
export const deliveryApi = {
  /** DLV-01 참여자별 배송 목록·송장 등록률 */
  board: (groupBuyId: Id) =>
    api
      .get<DeliveryBoardApiResponse>(`/sellers/group-buys/${groupBuyId}/deliveries`)
      .then(toBoard),

  /** DLV-02 송장 등록. 동일 송장번호 중복 등록은 거부됩니다. */
  registerInvoice: (deliveryId: Id, body: InvoiceInput) =>
    api.put<DeliveryApiResponse>(`/sellers/deliveries/${deliveryId}/invoice`, body).then(toDelivery),

  /** DLV-03 참여자별 배송 상태 변경 */
  updateStatus: (deliveryId: Id, status: DeliveryStatus) =>
    api
      .patch<DeliveryApiResponse>(`/sellers/deliveries/${deliveryId}/status`, { status })
      .then(toDelivery),

  /**
   * DLV-04 배송 상태 일괄 변경.
   *
   * 공동구매의 배송 건 전체가 대상입니다. 백엔드는 상태만 받고 대상을 고르지
   * 않습니다(DeliveryStatusUpdateRequest). 예전에는 deliveryIds 를 함께
   * 보냈는데, 서버가 그 값을 읽지 않아 화면에서 고른 몇 건만 바뀔 것처럼
   * 보이고 실제로는 전부 바뀌었습니다.
   */
  updateStatusBulk: (groupBuyId: Id, status: DeliveryStatus) =>
    api.patch<NoContent>(`/sellers/group-buys/${groupBuyId}/deliveries/status`, { status }),

  /** DLV-05 구매자 배송 조회 */
  forParticipation: (participationId: Id) =>
    api
      .get<DeliveryApiResponse>(`/users/me/participations/${participationId}/delivery`)
      .then(toDelivery),
} as const
