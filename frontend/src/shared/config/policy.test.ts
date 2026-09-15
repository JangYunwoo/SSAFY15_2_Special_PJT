import { describe, expect, it } from 'vitest'

import {
  calculateAmounts,
  payableAmount,
  POLICY_DEFAULTS,
  progressRate,
  unitShareAmount,
} from './policy'

/**
 * 금액 규칙은 명세서 4장에 고정돼 있고 화면 여러 곳이 이 함수를 씁니다.
 * 올림·버림 방향이 뒤집히면 결제 미리보기와 정산 예상액이 조용히 어긋나므로
 * 경계값을 테스트로 잠가둡니다.
 */
describe('unitShareAmount', () => {
  it('나누어떨어지지 않으면 원 단위로 올립니다', () => {
    expect(unitShareAmount({ productPrice: 100_000, targetCount: 3, shippingFee: 0 })).toBe(33_334)
  })

  it('나누어떨어지면 그대로입니다', () => {
    expect(unitShareAmount({ productPrice: 100_000, targetCount: 4, shippingFee: 0 })).toBe(25_000)
  })

  it('목표 인원이 0 이하면 0 을 돌려줍니다 (0 으로 나누지 않습니다)', () => {
    expect(unitShareAmount({ productPrice: 100_000, targetCount: 0, shippingFee: 0 })).toBe(0)
  })
})

describe('payableAmount', () => {
  it('분담금에 배송비를 더합니다', () => {
    expect(payableAmount({ productPrice: 100_000, targetCount: 3, shippingFee: 3_000 })).toBe(
      36_334,
    )
  })
})

describe('calculateAmounts', () => {
  const terms = { productPrice: 100_000, targetCount: 3, shippingFee: 3_000 }

  it('수수료는 배송비를 뺀 분담금 합계에만 붙고 원 단위로 버립니다', () => {
    const result = calculateAmounts(terms)

    // 분담금 합계 33,334 × 3 = 100,002 → 5% = 5,000.1 → 버림 5,000
    expect(result.estimatedPlatformFee).toBe(5_000)
    expect(result.platformFeeRate).toBe(POLICY_DEFAULTS.platformFeeRate)
  })

  it('정산 예상액은 분담금 합계 + 배송비 합계 − 수수료입니다', () => {
    const result = calculateAmounts(terms)

    expect(result.estimatedSettlementAmount).toBe(100_002 + 9_000 - 5_000)
  })

  it('절상 차액은 판매자에게 남습니다', () => {
    expect(calculateAmounts(terms).roundingSurplus).toBe(2)
  })

  it('구매확정 인원이 목표보다 적으면 그 인원으로만 정산액을 계산합니다', () => {
    const result = calculateAmounts(terms, { confirmedCount: 2 })

    // 분담금 66,668 → 수수료 3,333 (버림), 배송비 6,000
    expect(result.estimatedPlatformFee).toBe(3_333)
    expect(result.estimatedSettlementAmount).toBe(66_668 + 6_000 - 3_333)
    // 절상 차액은 목표 인원 기준이라 확정 인원과 무관합니다.
    expect(result.roundingSurplus).toBe(2)
  })
})

describe('progressRate', () => {
  it('목표를 넘겨도 100 을 넘지 않습니다', () => {
    expect(progressRate(5, 3)).toBe(100)
  })

  it('목표 인원이 0 이면 0 입니다', () => {
    expect(progressRate(1, 0)).toBe(0)
  })
})
