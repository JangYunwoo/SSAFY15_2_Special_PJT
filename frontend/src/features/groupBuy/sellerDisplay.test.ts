import { describe, expect, it } from 'vitest'

import { sellerInitial } from './sellerDisplay'

describe('판매자 아바타 글자', () => {
  it('보통 이름은 첫 글자', () => {
    expect(sellerInitial('싸피')).toBe('싸')
  })

  /** 운영 데이터의 판매자명 상당수가 이 모양이다. charAt(0) 이면 "[" 가 나온다. */
  it('대괄호 표기로 시작하면 그 뒤 첫 글자', () => {
    expect(sellerInitial('[DEMO] 테크 셀렉트')).toBe('테')
    expect(sellerInitial('[DEMO] 리빙룸')).toBe('리')
  })

  it('영문은 대문자로 맞춘다', () => {
    expect(sellerInitial('apple store')).toBe('A')
  })

  it('숫자로 시작해도 글자로 본다', () => {
    expect(sellerInitial('2nd Hand')).toBe('2')
  })

  it('괄호 표기도 건너뛴다', () => {
    expect(sellerInitial('(주) 한빛상사')).toBe('한')
  })

  it('표기가 여러 개 붙어 있어도 건너뛴다', () => {
    expect(sellerInitial('[DEMO] (주) 테크')).toBe('테')
  })

  it('표기를 떼면 남는 것이 없으면 원본에서 찾는다', () => {
    expect(sellerInitial('[DEMO]')).toBe('D')
  })

  it('공백·기호만 있거나 비어 있으면 물음표', () => {
    expect(sellerInitial('')).toBe('?')
    expect(sellerInitial('   ')).toBe('?')
    expect(sellerInitial('[]')).toBe('?')
  })
})
