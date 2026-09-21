import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/errors'
const api = vi.hoisted(() => ({ abort: vi.fn(), detail: vi.fn() }))
vi.mock('@/entities/payment/api', () => ({ paymentApi: api }))
import { releasePaymentSlot } from './releasePaymentSlot'

const conflict = (code = 'INVALID_STATE_TRANSITION') => new ApiError(409, { code, message: '상태 변경 불가', fieldErrors: [] })
beforeEach(() => vi.resetAllMocks())
describe('슬롯 해제 API 상태 재확인', () => {
  it('정상 해제는 추가 조회하지 않는다', async () => {
    api.abort.mockResolvedValue(undefined)
    await releasePaymentSlot('10')
    expect(api.detail).not.toHaveBeenCalled()
  })
  it.each(['FAILED', 'EXPIRED'])('%s 종료 확인 후 다음 준비를 허용한다', async (status) => {
    api.abort.mockRejectedValue(conflict())
    api.detail.mockResolvedValue({ status })
    await expect(releasePaymentSlot('10')).resolves.toBeUndefined()
    expect(api.detail).toHaveBeenCalledWith('10')
  })
  it.each(['PAID', 'PROCESSING'])('%s는 해제 성공으로 처리하지 않는다', async (status) => {
    const error = conflict()
    api.abort.mockRejectedValue(error)
    api.detail.mockResolvedValue({ status })
    await expect(releasePaymentSlot('10')).rejects.toBe(error)
  })
  it.each(['PAYMENT_SLOT_EXPIRED', 'PAYMENT_ALREADY_PROCESSED'])('%s 오류도 실제 종료를 확인한다', async (code) => {
    api.abort.mockRejectedValue(conflict(code))
    api.detail.mockResolvedValue({ status: 'EXPIRED' })
    await expect(releasePaymentSlot('10')).resolves.toBeUndefined()
  })
  it('상태 조회 실패는 정리 성공으로 숨기지 않는다', async () => {
    const lost = new Error('detail network lost')
    api.abort.mockRejectedValue(conflict())
    api.detail.mockRejectedValue(lost)
    await expect(releasePaymentSlot('10')).rejects.toBe(lost)
  })
  it('해제 통신 오류 자체는 조회 우회하지 않고 재시도 대상으로 남긴다', async () => {
    const lost = new Error('abort network lost')
    api.abort.mockRejectedValue(lost)
    await expect(releasePaymentSlot('10')).rejects.toBe(lost)
    expect(api.detail).not.toHaveBeenCalled()
  })
})
