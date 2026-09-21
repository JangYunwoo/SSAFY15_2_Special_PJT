import { describe, expect, it } from 'vitest'
import type { Address } from '@/entities/address/model'
import { initialShipping } from './shippingSelection'

const address: Address = {
  id: '1', recipientName: '테스트', recipientPhone: '01012345678', zipcode: '04524',
  address1: '서울 중구 세종대로 110', address2: '테스트', isDefault: true, deliveryRequest: '문 앞에 놓아주세요',
}
const addresses = [address, { ...address, id: '2', isDefault: false }]
describe('배송 단계 선택 복원', () => {
  it('같은 공구의 비기본 배송지와 요청사항을 복원한다', () => {
    expect(initialShipping('10', addresses, { groupBuyId: '10', addressId: '2', deliveryRequest: '경비실에 맡겨주세요' }))
      .toEqual({ addressId: '2', deliveryRequest: '경비실에 맡겨주세요' })
  })
  it('의도적으로 비운 요청사항은 주소 기본값으로 되살리지 않는다', () => {
    expect(initialShipping('10', addresses, { groupBuyId: '10', addressId: '2', deliveryRequest: null }).deliveryRequest).toBe('')
  })
  it('다른 공구 값은 가져오지 않고 기본 주소를 제안한다', () => {
    expect(initialShipping('11', addresses, { groupBuyId: '10', addressId: '2', deliveryRequest: '예전 요청' }))
      .toEqual({ addressId: '1', deliveryRequest: address.deliveryRequest })
  })
  it('삭제된 선택 주소를 다른 주소로 자동 대체하지 않는다', () => {
    expect(initialShipping('10', [address], { groupBuyId: '10', addressId: '2', deliveryRequest: null }).addressId).toBe('')
  })
  it('배송지가 없으면 선택을 비운다', () => {
    expect(initialShipping('10', [], { groupBuyId: null, addressId: null, deliveryRequest: null }))
      .toEqual({ addressId: '', deliveryRequest: '' })
  })
})
