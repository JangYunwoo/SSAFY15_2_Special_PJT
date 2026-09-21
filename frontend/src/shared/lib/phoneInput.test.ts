import { describe, expect, it } from 'vitest'

import { formatPhoneInput, phoneCaret, phoneDigits } from './phoneInput'

describe('전화번호 입력 서식', () => {
  it.each([
    ['', ''], ['0', '0'], ['010', '010'], ['0101', '010-1'],
    ['0101234', '010-1234'], ['0101234567', '010-123-4567'],
    ['01012345678', '010-1234-5678'], ['02', '02'], ['02123', '02-123'],
    ['021234567', '02-123-4567'], ['0212345678', '02-1234-5678'],
    ['0311234567', '031-123-4567'], ['07012345678', '070-1234-5678'],
  ])('%s → %s', (input, expected) => { expect(formatPhoneInput(input)).toBe(expected) })

  it('붙여넣기의 공백·기존 하이픈을 제거하고 국내 최대 길이를 적용한다', () => {
    expect(phoneDigits(' 010-1234-5678 ')).toBe('01012345678')
    expect(phoneDigits('01012345678999')).toBe('01012345678')
    expect(formatPhoneInput(' 010-1234-5678 ')).toBe('010-1234-5678')
  })

  it('서식만 달라도 API 값과 변경 여부 비교는 같다', () => {
    expect(phoneDigits('010-1234-5678')).toBe(phoneDigits('01012345678'))
  })

  it('중간 편집 시 숫자 기준으로 커서를 복구한다', () => {
    expect(phoneCaret('010-1234-5678', 0)).toBe(0)
    expect(phoneCaret('010-1234-5678', 3)).toBe(3)
    expect(phoneCaret('010-1234-5678', 4)).toBe(5)
    expect(phoneCaret('010-1234-5678', 11)).toBe(13)
  })
})
