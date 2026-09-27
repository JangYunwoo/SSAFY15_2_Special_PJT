import { describe, expect, it } from 'vitest'

import { aiHintText } from './useAiExtraction'

describe('AI 추출 안내 문구', () => {
  it('신뢰도가 높으면 검수 안내를 붙이지 않는다', () => {
    expect(aiHintText({ confidence: 0.95, needsReview: false })).toBe('AI 추출 · 신뢰도 95%')
  })

  it('신뢰도가 낮으면 검수를 안내한다', () => {
    expect(aiHintText({ confidence: 0.4, needsReview: true })).toBe(
      'AI 추출 · 신뢰도 40% · 값을 확인해 주세요',
    )
  })

  /**
   * 이 케이스가 이 파일의 이유다. AI 모듈이 신뢰도를 보내지 않아 백엔드가 0 을
   * 채우던 시절, 화면에 "신뢰도 0% · 값을 확인해 주세요" 가 떠서 정상 추출한
   * 카테고리를 판매자가 틀린 값으로 오해했다.
   */
  it('신뢰도를 모르면 숫자를 지어내지 않는다', () => {
    const text = aiHintText({ confidence: null, needsReview: true })
    expect(text).toBe('AI 추출 · 값을 확인해 주세요')
    expect(text).not.toMatch(/%/)
  })

  it('진짜 0% 와 모르는 것은 다르게 보여준다', () => {
    expect(aiHintText({ confidence: 0, needsReview: true })).toContain('신뢰도 0%')
    expect(aiHintText({ confidence: null, needsReview: true })).not.toContain('신뢰도')
  })

  it('안내할 항목이 없으면 문구도 없다', () => {
    expect(aiHintText(undefined)).toBeUndefined()
  })
})
