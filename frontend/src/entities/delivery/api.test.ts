import { describe, expect, it, vi } from 'vitest'
const get = vi.hoisted(() => vi.fn())
vi.mock('@/shared/api/client', () => ({ api: { get } }))
import { deliveryApi } from './api'

describe('배송 요청 스냅샷 응답 매핑', () => {
  it.each(['이번에는 경비실', '', null])('판매자 배송 목록의 %s 값을 전달한다', async (deliveryRequest) => {
    get.mockResolvedValue({ totalCount: 1, invoiceRegisteredCount: 0, invoiceRegistrationRate: 0,
      deliveries: [{ deliveryId: 1, participationId: 2, deliveryRequest,
        carrierCode: null, trackingNumber: null, shippedAt: null, deliveredAt: null }] })
    const board = await deliveryApi.board('3')
    expect(board.items[0]?.deliveryRequest).toBe(deliveryRequest)
  })
})
