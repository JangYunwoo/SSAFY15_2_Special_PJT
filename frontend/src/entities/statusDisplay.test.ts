import { afterEach, describe, expect, it, vi } from 'vitest'

import { PAYMENT_STATUS_DISPLAY } from './model'
import { displayFor } from './statusDisplay'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('displayFor', () => {
  it('아는 값은 정해진 라벨과 색을 돌려준다', () => {
    expect(displayFor(PAYMENT_STATUS_DISPLAY, 'PAID', '결제 상태')).toEqual({
      label: '결제 완료',
      tone: 'ok',
    })
  })

  /**
   * 예전에는 표에서 바로 꺼내 써서, 모르는 값이 오면 undefined 가 되고
   * 이어서 .tone 을 읽다가 화면 전체가 터졌습니다.
   */
  it('모르는 값이어도 터지지 않고 받은 값을 그대로 보여준다', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    expect(displayFor(PAYMENT_STATUS_DISPLAY, 'APPROVED', '결제 상태')).toEqual({
      label: 'APPROVED',
      tone: 'neutral',
    })
  })

  it('빈 문자열이면 칩이 빈 칸으로 보이지 않게 줄표를 넣는다', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    expect(displayFor(PAYMENT_STATUS_DISPLAY, '', '결제 상태')).toEqual({
      label: '-',
      tone: 'neutral',
    })
  })

  it('모르는 값을 만나면 개발 중에 콘솔로 알린다', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    displayFor(PAYMENT_STATUS_DISPLAY, 'NEVER_SEEN_BEFORE', '결제 상태')

    expect(spy).toHaveBeenCalledOnce()
    expect(spy.mock.calls[0]?.[0]).toContain('NEVER_SEEN_BEFORE')
  })

  /** 목록에서 같은 값이 여러 번 그려져도 콘솔이 도배되지 않아야 합니다. */
  it('같은 값은 한 번만 알린다', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    displayFor(PAYMENT_STATUS_DISPLAY, 'REPEATED_VALUE', '결제 상태')
    displayFor(PAYMENT_STATUS_DISPLAY, 'REPEATED_VALUE', '결제 상태')
    displayFor(PAYMENT_STATUS_DISPLAY, 'REPEATED_VALUE', '결제 상태')

    expect(spy).toHaveBeenCalledOnce()
  })
})
