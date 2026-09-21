import { describe, expect, it, vi } from 'vitest'

import type { Payment } from '@/entities/payment/model'
import { ApiError } from '@/shared/api/errors'
import { isUsablePayment, PaymentSlotController, type SlotSelection } from './paymentSlotController'

const NOW = Date.parse('2026-09-21T00:00:00Z')
const card: SlotSelection = { groupBuyId: '1', addressId: '2', method: 'CARD', cryptoAssetId: null }
const kakao: SlotSelection = { ...card, method: 'KAKAOPAY' }
const coin: SlotSelection = { ...card, method: 'CRYPTO', cryptoAssetId: '3' }
const payment = (id: string, selection = card): Payment => ({
  paymentId: id, groupBuyId: selection.groupBuyId, method: selection.method, status: 'PROCESSING',
  slotExpiresAt: new Date(NOW + 300_000).toISOString(), remainingSeconds: 300,
  participationId: null, amount: { shareAmount: 1000, shippingFee: 0, totalAmount: 1000, currency: 'KRW' },
})
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
async function flush() { for (let i = 0; i < 12; i += 1) await Promise.resolve() }
function setup() {
  let sequence = 0
  const prepare = vi.fn((_group: string, body: Omit<SlotSelection, 'groupBuyId'>, _key: string) =>
    Promise.resolve(payment(String(++sequence), { groupBuyId: '1', ...body })))
  const abort = vi.fn((_id: string) => Promise.resolve(undefined))
  let keySequence = 0
  const newKey = vi.fn(() => `key-${String(++keySequence)}`)
  const controller = new PaymentSlotController({ prepare, abort, newKey, now: () => NOW })
  return { controller, prepare, abort, newKey }
}

describe('결제 슬롯 전환 직렬화', () => {
  it('완료 구독 콜백에서 바뀐 선택도 유실하지 않는다', async () => {
    const { controller } = setup()
    controller.subscribe(() => {
      if (controller.getSnapshot().payment?.method === 'CARD') controller.select(kakao)
    })
    controller.select(card)
    await flush()
    expect(controller.getSnapshot().payment?.method).toBe('KAKAOPAY')
  })
  it('StrictMode setup-cleanup-setup의 늦은 슬롯도 해제한다', async () => {
    const { controller, prepare, abort } = setup()
    const pending = deferred<Payment>()
    prepare.mockReturnValueOnce(pending.promise)
    controller.select(card)
    controller.select(null)
    controller.select(card)
    pending.resolve(payment('strict-old'))
    await flush()
    expect(abort).toHaveBeenCalledWith('strict-old')
    expect(controller.getSnapshot().payment?.paymentId).not.toBe('strict-old')
    expect(prepare.mock.calls[0]?.[2]).not.toBe(prepare.mock.calls[1]?.[2])
  })
  it('선택 전에는 준비하지 않는다', async () => {
    const { controller, prepare } = setup()
    controller.select(null)
    await flush()
    expect(prepare).not.toHaveBeenCalled()
  })
  it('동일 선택 반복은 같은 준비를 유지한다', async () => {
    const { controller, prepare } = setup()
    controller.select(card)
    controller.select({ ...card })
    await flush()
    expect(prepare).toHaveBeenCalledTimes(1)
    expect(controller.getSnapshot().payment?.method).toBe('CARD')
  })
  it('해제 성공 전에는 다음 준비를 호출하지 않는다', async () => {
    const { controller, prepare, abort } = setup()
    controller.select(card)
    await flush()
    const release = deferred<undefined>()
    abort.mockReturnValueOnce(release.promise)
    controller.select(kakao)
    await flush()
    expect(prepare).toHaveBeenCalledTimes(1)
    expect(controller.getSnapshot().payment).toBeNull()
    release.resolve(undefined)
    await flush()
    expect(controller.getSnapshot().payment?.method).toBe('KAKAOPAY')
    expect(abort.mock.invocationCallOrder[0]).toBeLessThan(prepare.mock.invocationCallOrder[1] ?? 0)
  })
  it('늦은 이전 prepare를 해제하고 마지막 선택만 준비한다', async () => {
    const { controller, prepare, abort } = setup()
    const pending = deferred<Payment>()
    prepare.mockReturnValueOnce(pending.promise)
    controller.select(card)
    controller.select(kakao)
    controller.select(coin)
    pending.resolve(payment('old'))
    await flush()
    expect(abort).toHaveBeenCalledWith('old')
    expect(prepare).toHaveBeenCalledTimes(2)
    expect(prepare.mock.calls[1]?.[1].method).toBe('CRYPTO')
    expect(controller.getSnapshot().payment?.method).toBe('CRYPTO')
  })
  it('해제 실패 시 새 슬롯을 만들지 않고 같은 슬롯 해제를 재시도한다', async () => {
    const { controller, prepare, abort } = setup()
    controller.select(card)
    await flush()
    abort.mockRejectedValueOnce(new Error('release failed'))
    controller.select(kakao)
    await flush()
    expect(prepare).toHaveBeenCalledTimes(1)
    expect(controller.getSnapshot().error).toBeInstanceOf(Error)
    controller.retry()
    await flush()
    expect(abort.mock.calls.map(([id]) => id)).toEqual(['1', '1'])
    expect(prepare).toHaveBeenCalledTimes(2)
  })
  it('응답 유실 재시도는 같은 준비 키를 사용한다', async () => {
    const { controller, prepare } = setup()
    prepare.mockRejectedValueOnce(new Error('network lost'))
    controller.select(card)
    await flush()
    controller.retry()
    await flush()
    expect(prepare.mock.calls[0]?.[2]).toBe(prepare.mock.calls[1]?.[2])
  })
  it('응답 유실 뒤 다른 수단 선택도 이전 키 확인·해제를 먼저 한다', async () => {
    const { controller, prepare, abort } = setup()
    prepare.mockRejectedValueOnce(new Error('network lost'))
    controller.select(card)
    await flush()
    controller.select(kakao)
    await flush()
    expect(prepare.mock.calls.map((call) => call[1].method)).toEqual(['CARD', 'CARD', 'KAKAOPAY'])
    expect(prepare.mock.calls[0]?.[2]).toBe(prepare.mock.calls[1]?.[2])
    expect(abort).toHaveBeenCalledTimes(1)
  })
  it('카드로 다시 돌아오면 종료 전 키를 재사용하지 않는다', async () => {
    const { controller, prepare } = setup()
    controller.select(card)
    await flush()
    controller.select(kakao)
    await flush()
    controller.select(card)
    await flush()
    expect(new Set(prepare.mock.calls.map((call) => call[2])).size).toBe(3)
  })
  it('서버가 종료를 확인한 요청은 사용자 재시도 시 새 키를 쓴다', async () => {
    const { controller, prepare } = setup()
    prepare.mockRejectedValueOnce(new ApiError(409, {
      code: 'PAYMENT_ALREADY_PROCESSED', message: 'ended', fieldErrors: [],
    }))
    controller.select(card)
    await flush()
    expect(prepare).toHaveBeenCalledTimes(1)
    controller.retry()
    await flush()
    expect(prepare.mock.calls[0]?.[2]).not.toBe(prepare.mock.calls[1]?.[2])
  })
  it('종료된 결제 응답은 준비 완료로 노출하지 않는다', async () => {
    const { controller, prepare } = setup()
    prepare.mockResolvedValueOnce({ ...payment('ended'), status: 'FAILED' })
    controller.select(card)
    await flush()
    expect(controller.getSnapshot().payment).toBeNull()
    expect(controller.getSnapshot().error).toBeInstanceOf(Error)
  })
  it('화면 이탈 후 늦게 도착한 슬롯도 해제한다', async () => {
    const { controller, prepare, abort } = setup()
    const pending = deferred<Payment>()
    prepare.mockReturnValueOnce(pending.promise)
    controller.select(card)
    controller.select(null)
    pending.resolve(payment('late'))
    await flush()
    expect(abort).toHaveBeenCalledWith('late')
    expect(controller.getSnapshot().payment).toBeNull()
    expect(controller.getSnapshot().busy).toBe(false)
  })
  it('승인/외부 결제창으로 넘긴 결제는 화면 이탈 때 해제하지 않는다', async () => {
    const { controller, abort } = setup()
    controller.select(card)
    await flush()
    controller.handoff()
    controller.select(null)
    await flush()
    expect(abort).not.toHaveBeenCalled()
  })
  it('만료된 응답은 해제하며 자동 준비를 무한 반복하지 않는다', async () => {
    const { controller, prepare, abort } = setup()
    prepare.mockResolvedValueOnce({ ...payment('expired'), slotExpiresAt: new Date(NOW).toISOString() })
    controller.select(card)
    await flush()
    expect(abort).toHaveBeenCalledWith('expired')
    expect(prepare).toHaveBeenCalledTimes(1)
    expect(controller.getSnapshot().payment).toBeNull()
  })
})

describe('결제 버튼의 서버 상태 조건', () => {
  it('선택 수단·공구·진행 상태·미만료 조건을 모두 요구한다', () => {
    expect(isUsablePayment(payment('1'), card, NOW)).toBe(true)
    expect(isUsablePayment(payment('1'), kakao, NOW)).toBe(false)
    expect(isUsablePayment(payment('1'), { ...card, groupBuyId: 'other' }, NOW)).toBe(false)
    expect(isUsablePayment({ ...payment('1'), status: 'PAID' }, card, NOW)).toBe(false)
    expect(isUsablePayment(payment('1'), card, NOW + 300_000)).toBe(false)
    expect(isUsablePayment(payment('1'), null, NOW)).toBe(false)
    expect(isUsablePayment(null, card, NOW)).toBe(false)
  })
})
