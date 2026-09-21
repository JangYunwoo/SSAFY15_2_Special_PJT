import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

describe('주소 검색 SDK 로더', () => {
  let scripts: { src: string; async: boolean; onload: () => void; onerror: () => void; remove: ReturnType<typeof vi.fn> }[]
  let browser: { kakao?: { Postcode: unknown }; setTimeout: typeof setTimeout; clearTimeout: typeof clearTimeout }
  beforeEach(() => {
    vi.resetModules()
    vi.useFakeTimers()
    scripts = []
    browser = { setTimeout, clearTimeout }
    vi.stubGlobal('window', browser)
    vi.stubGlobal('document', {
      createElement: () => ({ src: '', async: false, onload: vi.fn(), onerror: vi.fn(), remove: vi.fn() }),
      head: { append: (script: typeof scripts[number]) => { scripts.push(script) } },
    })
  })
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

  it('동시 호출은 공식 스크립트를 하나만 요청한다', async () => {
    const { loadPostcode } = await import('./postcode')
    const a = loadPostcode(), b = loadPostcode()
    expect(a).toBe(b)
    expect(scripts).toHaveLength(1)
    expect(scripts[0]?.src).toBe('https://t1.kakaocdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js')
    const Postcode = vi.fn()
    browser.kakao = { Postcode }
    scripts[0]?.onload()
    await expect(a).resolves.toBe(Postcode)
  })

  it('로드 실패 후에는 새 요청으로 재시도할 수 있다', async () => {
    const { loadPostcode } = await import('./postcode')
    const first = loadPostcode()
    const rejected = expect(first).rejects.toThrow('인터넷 연결')
    scripts[0]?.onerror()
    await rejected
    expect(scripts[0]?.remove).toHaveBeenCalledOnce()
    const second = loadPostcode()
    expect(scripts).toHaveLength(2)
    const Postcode = vi.fn()
    browser.kakao = { Postcode }
    scripts[1]?.onload()
    await expect(second).resolves.toBe(Postcode)
  })

  it('응답이 오지 않으면 15초 후 무한 로딩 대신 실패한다', async () => {
    const { loadPostcode } = await import('./postcode')
    const rejected = expect(loadPostcode()).rejects.toThrow('인터넷 연결')
    await vi.advanceTimersByTimeAsync(15_000)
    await rejected
    expect(scripts[0]?.remove).toHaveBeenCalledOnce()
  })
})
