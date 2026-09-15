import { describe, expect, it } from 'vitest'

import { optionalText } from './productForm'

describe('optionalText', () => {
  it('값이 있으면 앞뒤 공백을 털어 그대로 보낸다', () => {
    expect(optionalText('  MGN63KH/A ')).toBe('MGN63KH/A')
  })

  /** 빈 문자열로 보내면 DB 에 '' 가 저장돼 "적지 않음" 과 구분되지 않습니다. */
  it('비어 있으면 null 을 보낸다', () => {
    expect(optionalText('')).toBeNull()
  })

  it('공백만 있어도 null 을 보낸다', () => {
    expect(optionalText('   ')).toBeNull()
  })
})
