import { expect, it } from 'vitest'
import { PAYMENT_STATUS_DISPLAY } from '@/entities/model'

it('실패 enum을 승인 거절이라고 단정하지 않는다', () => {
  expect(PAYMENT_STATUS_DISPLAY.FAILED.label).toBe('결제 종료')
  expect(PAYMENT_STATUS_DISPLAY.FAILED.label).not.toContain('승인 실패')
})
it('확인 가능한 만료와 승인 완료는 구분한다', () => {
  expect(PAYMENT_STATUS_DISPLAY.EXPIRED.label).toBe('시간 만료')
  expect(PAYMENT_STATUS_DISPLAY.PAID.label).toBe('결제 완료')
})
