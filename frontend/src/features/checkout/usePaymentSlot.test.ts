import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PaymentMethod } from '@/entities/model'
import type * as ControllerModule from './paymentSlotController'

const mocks = vi.hoisted(() => ({ select: vi.fn(), retry: vi.fn() }))
vi.mock('react', () => ({
  useEffect: (effect: () => unknown) => { effect() },
  useSyncExternalStore: (_subscribe: unknown, get: () => unknown) => get(),
}))
vi.mock('@/shared/lib/hooks/useNow', () => ({ useNow: () => Date.now() }))
vi.mock('./checkoutStore', () => ({ useCheckoutStore: () => vi.fn() }))
vi.mock('./paymentSlotController', async (original) => ({
  ...await original<typeof ControllerModule>(),
  PaymentSlotController: class {
    select = mocks.select
    retry = mocks.retry
    handoff = vi.fn()
    subscribe = vi.fn()
    getSnapshot = () => ({ selection: null, payment: null, error: null, busy: false })
  },
}))
import { usePaymentSlot } from './usePaymentSlot'

function render(method: PaymentMethod | null, addressId: string | null, cryptoAssetId: string | null) {
  // 훅 연결 계약만 검사한다. 실제 비동기 수명은 controller 테스트에서 검증한다.
  // eslint-disable-next-line react-hooks/rules-of-hooks
  return usePaymentSlot({ groupBuyId: 'group', addressId, method, cryptoAssetId })
}
beforeEach(() => vi.clearAllMocks())
describe('명시적 수단 및 B07 자산 선택 게이트', () => {
  it.each([null, '', '   '])('자산 %s는 슬롯 선택에서 제외한다', (asset) => {
    render('CRYPTO', 'address', asset)
    expect(mocks.select).toHaveBeenCalledWith(null)
  })
  it('결제수단 미선택은 준비하지 않는다', () => {
    render(null, 'address', null)
    expect(mocks.select).toHaveBeenCalledWith(null)
  })
  it.each(['CARD', 'KAKAOPAY'] as const)('%s는 자산 없이 준비 가능하다', (method) => {
    render(method, 'address', null)
    expect(mocks.select).toHaveBeenCalledWith({ groupBuyId: 'group', addressId: 'address', method, cryptoAssetId: null })
  })
  it.each(['CARD', 'KAKAOPAY', 'CRYPTO'] as const)('%s도 배송지 없이 준비하지 않는다', (method) => {
    render(method, null, 'asset')
    expect(mocks.select).toHaveBeenCalledWith(null)
  })
  it('자산 변경을 현재 선택 ID로 전달한다', () => {
    for (const cryptoAssetId of ['asset-1', 'asset-2']) {
      render('CRYPTO', 'address', cryptoAssetId)
      expect(mocks.select).toHaveBeenLastCalledWith({ groupBuyId: 'group', addressId: 'address', method: 'CRYPTO', cryptoAssetId })
    }
  })
})
