import { describe, expect, it } from 'vitest'

import { isChunkLoadError, shouldReloadForChunkError } from './lazyRoute'

const NOW = 1_700_000_000_000

describe('청크 로드 오류 판별', () => {
  /** 실제로 운영에서 본 메시지 (판매자센터 주문 관리 진입 시) */
  it('Chrome 메시지', () => {
    expect(
      isChunkLoadError(
        new TypeError(
          'Failed to fetch dynamically imported module: https://j15b205.p.ssafy.io/assets/SellerOrdersPage-C3VjzwF8.js',
        ),
      ),
    ).toBe(true)
  })

  it('Firefox 메시지', () => {
    expect(isChunkLoadError(new TypeError('error loading dynamically imported module'))).toBe(true)
  })

  it('Safari 메시지', () => {
    expect(isChunkLoadError(new TypeError('Importing a module script failed.'))).toBe(true)
  })

  it('관계없는 오류는 아니다', () => {
    expect(isChunkLoadError(new Error('Network request failed'))).toBe(false)
    expect(isChunkLoadError(new TypeError('x is not a function'))).toBe(false)
  })

  it('Error 가 아닌 값도 다룬다', () => {
    expect(isChunkLoadError('Failed to fetch dynamically imported module: /a.js')).toBe(true)
    expect(isChunkLoadError(undefined)).toBe(false)
    expect(isChunkLoadError(null)).toBe(false)
  })
})

describe('새로고침 판정', () => {
  it('청크 오류이고 새로고침한 적 없으면 한다', () => {
    const error = new TypeError('Failed to fetch dynamically imported module: /a.js')
    expect(shouldReloadForChunkError(error, NOW, null)).toBe(true)
  })

  it('청크 오류가 아니면 하지 않는다', () => {
    expect(shouldReloadForChunkError(new Error('boom'), NOW, null)).toBe(false)
  })

  /**
   * 이 케이스가 이 파일의 이유다. 서버에 파일이 정말 없으면 새로고침해도 같은
   * 오류가 나고, 막지 않으면 화면이 무한히 깜빡여 사용자가 아무것도 못 한다.
   */
  it('방금 새로고침했으면 또 하지 않는다', () => {
    const error = new TypeError('Failed to fetch dynamically imported module: /a.js')
    expect(shouldReloadForChunkError(error, NOW, NOW - 1_000)).toBe(false)
    expect(shouldReloadForChunkError(error, NOW, NOW - 9_999)).toBe(false)
  })

  it('쿨다운이 지났으면 다시 한다', () => {
    const error = new TypeError('Failed to fetch dynamically imported module: /a.js')
    expect(shouldReloadForChunkError(error, NOW, NOW - 10_001)).toBe(true)
  })
})
