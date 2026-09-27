import { describe, expect, it } from 'vitest'

import { AI_EXPECTED_SECONDS, aiProgressText } from './aiProgress'

describe('AI 분석 진행 문구', () => {
  it('시작 직후에는 예상 시간만 알린다', () => {
    expect(aiProgressText(0)).toBe('이미지를 분석하고 있습니다 · 보통 1분 안팎 걸립니다')
  })

  it('진행 중에는 경과 초를 함께 보여준다', () => {
    expect(aiProgressText(12)).toContain('12초 경과')
    expect(aiProgressText(12)).toContain('보통 1분 안팎')
  })

  /** 예상 시간을 넘겨도 실패가 아니다. 문구만 바꿔 계속 기다리게 한다. */
  it('예상 시간을 넘기면 문구가 바뀐다', () => {
    expect(aiProgressText(AI_EXPECTED_SECONDS)).toContain('아직 분석 중입니다')
    expect(aiProgressText(AI_EXPECTED_SECONDS)).toContain('60초 경과')
    expect(aiProgressText(90)).toContain('90초 경과')
  })

  it('예상 시간 직전까지는 문구가 바뀌지 않는다', () => {
    expect(aiProgressText(AI_EXPECTED_SECONDS - 1)).toContain('이미지를 분석하고 있습니다')
  })

  it('음수는 시작 직후로 다룬다', () => {
    expect(aiProgressText(-1)).toBe('이미지를 분석하고 있습니다 · 보통 1분 안팎 걸립니다')
  })
})
