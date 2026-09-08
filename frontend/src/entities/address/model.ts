import type { Id } from '@/shared/api/types'

/** BUY-03 배송지 */
export interface Address {
  id: Id
  recipientName: string
  recipientPhone: string
  zipcode: string
  address1: string
  address2: string
  /** 기본 배송지 여부 */
  isDefault: boolean
  /** 배송 요청사항 */
  deliveryRequest: string | null
}

export type AddressInput = Omit<Address, 'id'>

export const DELIVERY_REQUESTS = [
  '문 앞에 놓아주세요',
  '경비실에 맡겨주세요',
  '배송 전 연락해주세요',
] as const
