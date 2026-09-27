import { describe, expect, it } from 'vitest'

import { AI_EXPECTED_SECONDS, aiProgressText } from './aiProgress'

describe('AI 분석 진행 문구', () => {
  it('시작 직후에는 예상 시간만 알린다', () => {
    expect(aiProgressText(0)).toBe('이미지를 분석하고 있어요. 보통 1분 정도 소요돼요. 잠시만 기다려주세요')
  })

  it('진행 중에는 경과 초를 함께 보여준다', () => {
    expect(aiProgressText(12)).toContain('12초 경과')
    expect(aiProgressText(12)).toContain('보통 1분 정도 소요돼요')
  })

  /** 예상 시간을 넘겨도 실패가 아니다. 문구만 바꿔 계속 기다리게 한다. */
  it('예상 시간을 넘기면 문구가 바뀐다', () => {
    expect(aiProgressText(AI_EXPECTED_SECONDS)).toContain('분석이 조금 길어지고 있어요')
    expect(aiProgressText(AI_EXPECTED_SECONDS)).toContain('60초 경과')
    expect(aiProgressText(90)).toContain('90초 경과')
  })

  it('예상 시간 직전까지는 문구가 바뀌지 않는다', () => {
    expect(aiProgressText(AI_EXPECTED_SECONDS - 1)).toContain('이미지를 분석하고 있어요')
  })

  it('음수는 시작 직후로 다룬다', () => {
    expect(aiProgressText(-1)).toBe('이미지를 분석하고 있어요. 보통 1분 정도 소요돼요. 잠시만 기다려주세요')
  })
})
