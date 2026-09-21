import { describe, expect, it, vi } from 'vitest'

const { post } = vi.hoisted(() => ({ post: vi.fn() }))
vi.mock('@/shared/api/client', () => ({ api: { post }, newIdempotencyKey: () => 'test-key' }))
import { paymentApi } from './api'

describe('B01 카카오 복귀 state 계약', () => {
  it.each(['cancel', 'fail'] as const)('%s는 state를 POST 본문으로 전달한다', async (flow) => {
    const callback = flow === 'cancel' ? paymentApi.kakaoCancelFlow : paymentApi.kakaoFailFlow
    await callback('9001', 'fixture-signed-state')
    expect(post).toHaveBeenLastCalledWith(`/payments/9001/kakaopay/${flow}-flow`, {
      state: 'fixture-signed-state',
    })
  })
})
