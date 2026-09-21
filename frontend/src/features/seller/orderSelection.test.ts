import { describe, expect, it } from 'vitest'
import { orderSelection, sellerOrdersPath } from './orderSelection'

describe('주문 관리의 명시적 공구 선택', () => {
  it('상세 링크에 공구 ID를 보존한다', () => {
    expect(sellerOrdersPath('101')).toBe('/seller/orders?groupBuyId=101')
  })
  it('선택한 공구가 첫 목록 밖이어도 다른 공구로 바꾸지 않는다', () => {
    expect(orderSelection('101', '281')).toEqual({ id: '101', invalid: false })
  })
  it('파라미터 없이 진입할 때만 첫 공구를 쓴다', () => {
    expect(orderSelection(null, '281')).toEqual({ id: '281', invalid: false })
    expect(orderSelection(null, null)).toEqual({ id: null, invalid: false })
  })
  it.each(['', '0', '-1', 'abc', '1/2', '1.5'])('잘못된 %s는 다른 주문으로 대체하지 않는다', (value) => {
    expect(orderSelection(value, '281')).toEqual({ id: null, invalid: true })
  })
})
