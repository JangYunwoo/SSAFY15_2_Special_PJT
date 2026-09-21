import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { PaymentMethod } from '@/entities/model'

const mocks = vi.hoisted(() => ({ query: vi.fn(), prepare: vi.fn() }))
vi.mock('react', () => ({
  useRef: (value: unknown) => ({ current: value }),
  useCallback: (callback: unknown) => callback,
  useEffect: vi.fn(),
}))
vi.mock('@tanstack/react-query', () => ({
  useQuery: (options: unknown) => {
    mocks.query(options)
    return { data: undefined, isFetching: false, error: null }
  },
}))
vi.mock('@/entities/payment/api', () => ({ paymentApi: { prepare: mocks.prepare } }))
vi.mock('@/shared/api/client', () => ({ newIdempotencyKey: () => 'test-key' }))
vi.mock('./checkoutStore', () => ({ useCheckoutStore: () => vi.fn() }))

import { usePaymentSlot } from './usePaymentSlot'

function options(method: PaymentMethod, addressId: string | null, cryptoAssetId: string | null) {
  // React와 useQuery를 위에서 대체한 순수 설정 계약 테스트로, 렌더러 밖에서 검사한다.
  // eslint-disable-next-line react-hooks/rules-of-hooks
  usePaymentSlot({ groupBuyId: 'group', addressId, method, cryptoAssetId })
  return mocks.query.mock.lastCall?.[0] as { enabled: boolean; queryFn: () => unknown }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('B07 코인 자산 선택 전 prepare 방지', () => {
  it.each([null, '', '   '])('자산 %s이면 자동 요청하지 않는다', (asset) => {
    expect(options('CRYPTO', 'address', asset).enabled).toBe(false)
    expect(mocks.prepare).not.toHaveBeenCalled()
  })
  it('수동 재조회도 자산 없이 서버에 요청하지 않는다', () => {
    expect(() => options('CRYPTO', 'address', null).queryFn()).toThrow()
    expect(mocks.prepare).not.toHaveBeenCalled()
  })
  it.each(['CARD', 'KAKAOPAY'] as const)('%s는 자산 없이 준비 가능하다', (method) => {
    const query = options(method, 'address', null)
    expect(query.enabled).toBe(true)
    query.queryFn()
    expect(mocks.prepare).toHaveBeenCalledWith(
      'group',
      {
        method,
        addressId: 'address',
        cryptoAssetId: null,
      },
      'test-key',
    )
  })
  it.each(['CARD', 'KAKAOPAY', 'CRYPTO'] as const)(
    '%s도 배송지가 없으면 준비하지 않는다',
    (method) => {
      const query = options(method, null, 'asset')
      expect(query.enabled).toBe(false)
      expect(() => query.queryFn()).toThrow()
      expect(mocks.prepare).not.toHaveBeenCalled()
    },
  )
  it('자산 선택 및 변경 후 현재 선택한 ID로 요청한다', () => {
    for (const asset of ['asset-1', 'asset-2']) {
      const query = options('CRYPTO', 'address', asset)
      expect(query.enabled).toBe(true)
      query.queryFn()
      expect(mocks.prepare).toHaveBeenLastCalledWith(
        'group',
        {
          method: 'CRYPTO',
          addressId: 'address',
          cryptoAssetId: asset,
        },
        'test-key',
      )
    }
  })
})
