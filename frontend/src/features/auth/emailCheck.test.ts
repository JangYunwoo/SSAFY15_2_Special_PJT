import { describe, expect, it } from 'vitest'

import { ApiError, NetworkError } from '@/shared/api/errors'

import {
  AVAILABLE,
  DUPLICATE,
  EMPTY,
  INVALID,
  UNSUPPORTED,
  blocksSignup,
  failed,
  isEmailRejection,
  isErrorOutcome,
  normalizeEmail,
  outcomeFor,
  validateEmail,
} from './emailCheck'

describe('validateEmail', () => {
  it('빈 값과 공백만 있는 값은 요청 전에 걸러낸다', () => {
    expect(validateEmail('')).toBe(EMPTY)
    expect(validateEmail('   ')).toBe(EMPTY)
  })

  it('형식이 어긋나면 요청을 보내지 않는다', () => {
    expect(validateEmail('user')).toBe(INVALID)
    expect(validateEmail('user@')).toBe(INVALID)
    expect(validateEmail('user@example')).toBe(INVALID)
    expect(validateEmail('us er@example.com')).toBe(INVALID)
  })

  it('앞뒤 공백은 잘라내고 통과시킨다', () => {
    expect(validateEmail('  user@example.com  ')).toBeNull()
  })
})

describe('normalizeEmail', () => {
  it('서버의 대소문자 무시 비교(utf8mb4_unicode_ci)와 기준을 맞춘다', () => {
    expect(normalizeEmail('  User@Example.COM ')).toBe('user@example.com')
  })
})

describe('outcomeFor', () => {
  const record = { email: 'user@example.com', outcome: AVAILABLE }

  it('확인한 적이 없으면 idle', () => {
    expect(outcomeFor(null, 'user@example.com').status).toBe('idle')
  })

  it('확인한 이메일 그대로면 결과를 유지한다', () => {
    expect(outcomeFor(record, 'user@example.com')).toBe(AVAILABLE)
  })

  it('대소문자·공백만 다른 값도 같은 이메일로 본다', () => {
    expect(outcomeFor(record, ' USER@example.com ')).toBe(AVAILABLE)
  })

  it('확인 후 이메일을 고치면 결과를 버린다', () => {
    // 통과시킨 주소와 실제 가입 주소가 어긋나는 것을 막는 핵심 규칙이다.
    expect(outcomeFor(record, 'other@example.com').status).toBe('idle')
  })
})

describe('blocksSignup', () => {
  it('확인 API가 있으면 확인 전까지 가입을 막는다', () => {
    expect(blocksSignup(DUPLICATE, true)).toBe(true)
    expect(blocksSignup(INVALID, true)).toBe(true)
    expect(blocksSignup(AVAILABLE, true)).toBe(false)
  })

  it('확인 API가 없으면 막지 않는다', () => {
    // 눌러도 확인되는 게 없는 버튼을 통과 의례로 강요하지 않는다.
    expect(blocksSignup(DUPLICATE, false)).toBe(false)
    expect(blocksSignup(UNSUPPORTED, false)).toBe(false)
  })

  it('준비 중 안내는 가입을 막지 않는다', () => {
    expect(UNSUPPORTED.confirmed).toBe(true)
  })
})

describe('isErrorOutcome', () => {
  it('중복·형식오류·확인실패만 오류로 표시한다', () => {
    expect(isErrorOutcome(DUPLICATE)).toBe(true)
    expect(isErrorOutcome(INVALID)).toBe(true)
    expect(isErrorOutcome(failed('네트워크 오류'))).toBe(true)
  })

  it('사용 가능·준비 중은 안내 문구로 표시한다', () => {
    expect(isErrorOutcome(AVAILABLE)).toBe(false)
    expect(isErrorOutcome(UNSUPPORTED)).toBe(false)
  })
})

describe('isEmailRejection', () => {
  const apiError = (code: string) => new ApiError(409, { code, message: '', fieldErrors: [] })

  it('이메일 때문에 가입이 막힌 오류를 모두 잡아낸다', () => {
    // 둘 다 원인이 이메일 한 칸이라 같은 자리에 떠야 한다.
    expect(isEmailRejection(apiError('EMAIL_ALREADY_EXISTS'))).toBe(true)
    expect(isEmailRejection(apiError('SSAFY_EMAIL_UNAVAILABLE'))).toBe(true)
  })

  it('다른 원인의 오류는 폼 하단 오류 상자에 맡긴다', () => {
    expect(isEmailRejection(apiError('VALIDATION_ERROR'))).toBe(false)
    expect(isEmailRejection(new NetworkError(new Error('offline')))).toBe(false)
    expect(isEmailRejection(null)).toBe(false)
  })
})
