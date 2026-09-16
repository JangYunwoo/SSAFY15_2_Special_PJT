import { describe, expect, it, vi } from 'vitest'

import { checkContract } from './contract'

/**
 * 이 검사는 개발 빌드에서만 돌고, 스키마 모듈을 `import()` 로 늦게 가져옵니다.
 * 그 경로가 끊기면 검사가 조용히 멈추기만 할 뿐 아무 신호도 남지 않으므로
 * 여기서 붙잡아 둡니다.
 */
describe('checkContract', () => {
  const valid = {
    id: '1',
    productId: '2',
    productName: '텀블러',
    status: 'RECRUITING',
    unitShareAmount: 9000,
    shippingFee: 3000,
    personalPaymentAmount: 12000,
    targetCount: 10,
    paidCount: 3,
    participationRate: 30,
    recruitEndAt: '2026-01-01T00:00:00Z',
  }

  it('모양이 어긋나면 어디가 어긋났는지 콘솔에 알린다', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    // 명세서 1.5 대로면 id 는 문자열입니다. 숫자로 오는 사고를 재현합니다.
    checkContract('groupBuySummary', { ...valid, id: 123 }, 'GET /group-buys')
    await vi.waitFor(() => {
      expect(spy).toHaveBeenCalled()
    })

    const [message] = spy.mock.calls[0] as [string]
    expect(message).toContain('[API 계약 불일치] GET /group-buys')
    expect(message).toContain('id:')
    spy.mockRestore()
  })

  it('모양이 맞으면 아무 말도 하지 않는다', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    checkContract('groupBuySummary', valid, 'GET /group-buys')
    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(spy).not.toHaveBeenCalled()
    spy.mockRestore()
  })

  it('스키마 이름을 넘기지 않으면 검사하지 않는다', async () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    checkContract(undefined, { 아무거나: true }, 'GET /group-buys')
    await new Promise((resolve) => setTimeout(resolve, 20))

    expect(spy).not.toHaveBeenCalled()
    spy.mockRestore()
  })
})
