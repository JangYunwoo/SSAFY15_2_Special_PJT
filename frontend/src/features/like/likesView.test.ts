import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api/errors'

import { firstRealError, isMissingGroupBuy, likesView } from './likesView'

const apiError = (status: number) =>
  new ApiError(status, { code: 'RESOURCE_NOT_FOUND', message: '', fieldErrors: [] })

describe('찜 목록 화면 선택', () => {
  it('찜한 것이 없으면 빈 화면', () => {
    expect(likesView({ likedCount: 0, loadedCount: 0, pending: false, errors: [] })).toBe('empty')
  })

  it('아직 받는 중이고 받은 것이 없으면 로딩', () => {
    expect(likesView({ likedCount: 2, loadedCount: 0, pending: true, errors: [] })).toBe('loading')
  })

  it('한 건이라도 받았으면 목록', () => {
    expect(likesView({ likedCount: 3, loadedCount: 1, pending: false, errors: [] })).toBe('list')
  })

  it('일부가 사라져도 남은 것이 있으면 목록', () => {
    expect(
      likesView({ likedCount: 3, loadedCount: 1, pending: false, errors: [apiError(404)] }),
    ).toBe('list')
  })

  /**
   * 이 케이스가 이 파일의 이유다. 찜은 브라우저에만 있어서 서버가 모르는 id 가
   * 남을 수 있고, 그때 오류 화면이 뜨면 기능이 고장난 것처럼 보인다.
   */
  it('사라진 건만 남았으면 오류가 아니라 빈 화면', () => {
    expect(
      likesView({
        likedCount: 2,
        loadedCount: 0,
        pending: false,
        errors: [apiError(404), apiError(404)],
      }),
    ).toBe('empty')
  })

  it('404 가 아닌 오류로 하나도 못 받았으면 오류 화면', () => {
    expect(
      likesView({ likedCount: 2, loadedCount: 0, pending: false, errors: [apiError(500)] }),
    ).toBe('error')
  })

  it('사라진 건과 실제 오류가 섞이면 오류 화면', () => {
    expect(
      likesView({
        likedCount: 2,
        loadedCount: 0,
        pending: false,
        errors: [apiError(404), apiError(503)],
      }),
    ).toBe('error')
  })
})

describe('사라진 건 판별', () => {
  it('404 만 사라진 건으로 본다', () => {
    expect(isMissingGroupBuy(apiError(404))).toBe(true)
    expect(isMissingGroupBuy(apiError(500))).toBe(false)
  })

  it('ApiError 가 아닌 것은 사라진 건이 아니다', () => {
    expect(isMissingGroupBuy(new Error('network'))).toBe(false)
    expect(isMissingGroupBuy(undefined)).toBe(false)
  })
})

describe('오류 화면에 보여줄 오류', () => {
  it('404 는 건너뛰고 실제 오류를 고른다', () => {
    const real = apiError(500)
    expect(firstRealError([apiError(404), real])).toBe(real)
  })

  it('404 뿐이면 보여줄 오류가 없다', () => {
    expect(firstRealError([apiError(404)])).toBeUndefined()
  })
})
