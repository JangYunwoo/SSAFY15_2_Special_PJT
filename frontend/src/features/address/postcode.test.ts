import { describe, expect, it } from 'vitest'

import { selectedAddress, type PostcodeResult } from './postcode'

const result: PostcodeResult = {
  zonecode: '04524', address: '서울 중구 세종대로 110',
  roadAddress: '서울 중구 세종대로 110', jibunAddress: '서울 중구 태평로1가 31', userSelectedType: 'R',
}

describe('주소 선택 계약', () => {
  it('도로명 주소와 우편번호를 함께 사용한다', () => {
    expect(selectedAddress(result)).toEqual({ zipcode: '04524', address1: result.roadAddress })
  })
  it('지번 선택을 도로명으로 덮어쓰지 않는다', () => {
    expect(selectedAddress({ ...result, userSelectedType: 'J' }).address1).toBe(result.jibunAddress)
  })
  it('선택 유형 주소가 없으면 공식 기본 주소를 사용한다', () => {
    expect(selectedAddress({ ...result, roadAddress: '' }).address1).toBe(result.address)
  })
  it.each(['', '1234', '123456', 'abcde'])('잘못된 우편번호 %s를 거절한다', (zonecode) => {
    expect(() => selectedAddress({ ...result, zonecode })).toThrow()
  })
  it('비어 있는 주소는 저장하지 않는다', () => {
    expect(() => selectedAddress({ ...result, roadAddress: '', address: ' ' })).toThrow()
  })
})
