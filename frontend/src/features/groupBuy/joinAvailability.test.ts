import { describe, expect, it } from 'vitest'

import { joinAvailability, type JoinAvailabilityInput } from './joinAvailability'

const recruiting: JoinAvailabilityInput = {
  status: 'RECRUITING',
  paidCount: 3,
  targetCount: 5,
  sellerSuspended: false,
}

describe('참여 가능 여부', () => {
  it('모집 중이고 자리가 남으면 참여할 수 있다', () => {
    expect(joinAvailability(recruiting)).toEqual({
      canJoin: true,
      label: '공동구매 참여하기',
    })
  })

  it('자리가 다 차면 막는다', () => {
    const result = joinAvailability({ ...recruiting, paidCount: 5 })
    expect(result.canJoin).toBe(false)
    expect(result.label).toBe('모집 인원이 모두 찼습니다')
  })

  it('목표를 넘겨도 막는다', () => {
    expect(joinAvailability({ ...recruiting, paidCount: 6 }).canJoin).toBe(false)
  })
})

/** 예전에는 셋 다 "참여할 수 없습니다" 하나였다. 구분되는지가 이 파일의 핵심이다. */
describe('막힌 이유를 상태별로 구분한다', () => {
  it('모집 완료', () => {
    expect(joinAvailability({ ...recruiting, status: 'FULFILLED' }).label).toBe(
      '모집이 완료되었습니다',
    )
  })

  it('모집 실패', () => {
    expect(joinAvailability({ ...recruiting, status: 'FAILED' }).label).toBe(
      '모집에 실패한 공동구매입니다',
    )
  })

  it('취소됨', () => {
    expect(joinAvailability({ ...recruiting, status: 'CANCELED' }).label).toBe(
      '취소된 공동구매입니다',
    )
  })

  it('네 상태의 문구가 서로 다르다', () => {
    const labels = (['RECRUITING', 'FULFILLED', 'FAILED', 'CANCELED'] as const).map(
      (status) => joinAvailability({ ...recruiting, status }).label,
    )
    expect(new Set(labels).size).toBe(4)
  })
})

describe('판매자 정지', () => {
  /** 서버가 새 결제를 거부하므로(S15P21B205-245) 결제 화면까지 보내지 않는다. */
  it('모집 중이어도 막는다', () => {
    const result = joinAvailability({ ...recruiting, sellerSuspended: true })
    expect(result.canJoin).toBe(false)
    expect(result.label).toBe('현재 참여할 수 없는 상품입니다')
  })

  it('다른 사유보다 먼저 판정한다', () => {
    expect(
      joinAvailability({ ...recruiting, status: 'FULFILLED', sellerSuspended: true }).label,
    ).toBe('현재 참여할 수 없는 상품입니다')
  })

  /** 구매자 화면에서 판매자의 위반 이력을 알릴 이유가 없다. */
  it('문구에 제재·정지 같은 표현을 쓰지 않는다', () => {
    const label = joinAvailability({ ...recruiting, sellerSuspended: true }).label
    expect(label).not.toMatch(/제재|정지|위반/)
  })
})
