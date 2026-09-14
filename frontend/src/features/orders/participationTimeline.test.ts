import { describe, expect, it } from 'vitest'

import { PARTICIPATION_STATUS } from '@/entities/model'

import { isPastOrder, participationTimeline } from './participationTimeline'

/** 단계 상태만 뽑아 비교합니다. */
const states = (status: Parameters<typeof participationTimeline>[0]) =>
  participationTimeline(status).map((step) => step.state)

describe('participationTimeline', () => {
  it('모든 상태에서 5단계를 돌려줍니다', () => {
    for (const status of PARTICIPATION_STATUS) {
      expect(participationTimeline(status)).toHaveLength(5)
    }
  })

  it('결제완료는 첫 단계를 끝난 것으로 표시합니다', () => {
    expect(states('PAID')).toEqual(['done', 'todo', 'todo', 'todo', 'todo'])
  })

  it('배송중은 앞 단계를 채우고 현재 단계를 표시합니다', () => {
    expect(states('SHIPPING')).toEqual(['done', 'done', 'now', 'todo', 'todo'])
  })

  it('구매확정·정산완료는 마지막 단계까지 끝난 것으로 표시합니다', () => {
    expect(states('CONFIRMED')).toEqual(['done', 'done', 'done', 'done', 'done'])
    expect(states('SETTLED')).toEqual(['done', 'done', 'done', 'done', 'done'])
  })

  it('정상 경로를 벗어난 상태는 결제완료까지만 채웁니다', () => {
    for (const status of ['CANCELED', 'RETURNING', 'RETURNED', 'REFUNDING', 'REFUNDED'] as const) {
      expect(states(status)).toEqual(['now', 'todo', 'todo', 'todo', 'todo'])
    }
  })
})

describe('isPastOrder', () => {
  it('종결된 건만 지난 거래로 봅니다', () => {
    expect(isPastOrder('CONFIRMED')).toBe(true)
    expect(isPastOrder('SETTLED')).toBe(true)
    expect(isPastOrder('CANCELED')).toBe(true)
    expect(isPastOrder('RETURNED')).toBe(true)
    expect(isPastOrder('REFUNDED')).toBe(true)
  })

  it('진행 중인 건은 지난 거래가 아닙니다', () => {
    expect(isPastOrder('PAID')).toBe(false)
    expect(isPastOrder('SHIPPING')).toBe(false)
    expect(isPastOrder('DELIVERED')).toBe(false)
    // 반품·환불이 끝나기 전이라 아직 진행 중입니다.
    expect(isPastOrder('RETURNING')).toBe(false)
    expect(isPastOrder('REFUNDING')).toBe(false)
  })
})
