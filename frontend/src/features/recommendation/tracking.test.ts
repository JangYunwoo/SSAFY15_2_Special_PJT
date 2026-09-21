import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const auth = vi.hoisted((): { userId: string | null } => ({ userId: '101' }))
const api = vi.hoisted(() => ({ recordEvents: vi.fn(), endSession: vi.fn() }))
vi.mock('react', () => ({ useEffect: (effect: () => void) => { effect() } }))
vi.mock('@/entities/recommendation/api', () => ({ recommendationApi: api }))
vi.mock('@/features/auth/sessionStore', () => ({
  useIsSignedIn: () => auth.userId !== null,
  useSessionStore: Object.assign((select: (state: unknown) => unknown) => select({
    actor: auth.userId ? { id: auth.userId } : null, accessToken: auth.userId ? 'test-token' : null,
  }), {
    getState: () => ({ actor: auth.userId ? { id: auth.userId } : null, accessToken: auth.userId ? 'test-token' : null }),
  }),
}))

import { recommendationSessionId, recordRecommendationEvents, useRecommendationSessionLifecycle } from './tracking'
import { recommendationQueries } from '@/entities/recommendation/queries'

describe('추천 세션의 사용자 경계', () => {
  let storage: Map<string, string>
  let onPageHide: ((event: { persisted: boolean }) => void) | undefined
  beforeEach(() => {
    auth.userId = '101'
    api.recordEvents.mockReset().mockResolvedValue(undefined)
    api.endSession.mockReset().mockResolvedValue(undefined)
    onPageHide = undefined
    vi.stubGlobal('window', {
      addEventListener: (_type: string, listener: typeof onPageHide) => { onPageHide = listener },
      removeEventListener: vi.fn(),
    })
    storage = new Map()
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => { storage.set(key, value) },
      removeItem: (key: string) => { storage.delete(key) },
    })
  })
  afterEach(() => { vi.unstubAllGlobals() })

  it('동일 사용자의 연속 행동은 같은 세션을 쓴다', () => {
    expect(recommendationSessionId()).toBe(recommendationSessionId())
  })
  it('다른 계정으로 전환하면 이전 세션을 재사용하지 않는다', () => {
    const before = recommendationSessionId()
    auth.userId = '202'
    expect(recommendationSessionId()).not.toBe(before)
  })
  it('로그아웃 상태에는 세션을 발급하지 않는다', () => {
    recommendationSessionId()
    auth.userId = null
    expect(recommendationSessionId()).toBeNull()
    expect(storage.size).toBe(0)
  })
  it('소유자가 없는 기존 버전 세션은 새 세션으로 바꾼다', () => {
    const legacy = '11111111-1111-4111-8111-111111111111'
    storage.set('gongguhan.recommendation-session', legacy)
    expect(recommendationSessionId()).not.toBe(legacy)
  })
  it('로그아웃 이후 도착한 행동 콜백은 전송하지 않는다', async () => {
    auth.userId = null
    await recordRecommendationEvents([{ groupBuyId: '1', eventType: 'CLICK', source: 'HOME_POPULAR' }])
    expect(api.recordEvents).not.toHaveBeenCalled()
  })
  it('스토리지 장애가 상품 탐색을 실패시키지 않는다', async () => {
    vi.stubGlobal('sessionStorage', { getItem: () => { throw new Error('blocked') } })
    await expect(recordRecommendationEvents([{ groupBuyId: '1', eventType: 'CLICK', source: 'HOME_POPULAR' }])).resolves.toBeUndefined()
    expect(api.recordEvents).not.toHaveBeenCalled()
  })
  it('추천 캐시도 사용자별로 구분하며 비로그인 키와 다르다', () => {
    expect(recommendationQueries.list('101').queryKey).not.toEqual(recommendationQueries.list('202').queryKey)
    expect(recommendationQueries.list('101').queryKey).not.toEqual(recommendationQueries.list(null).queryKey)
  })
  it('pagehide는 현재 사용자 세션만 종료한다', () => {
    useRecommendationSessionLifecycle()
    const id = recommendationSessionId()
    onPageHide?.({ persisted: false })
    expect(api.endSession).toHaveBeenCalledWith(id)
    expect(storage.size).toBe(0)
  })
  it('BFCache 이동에서는 세션을 종료하지 않는다', () => {
    useRecommendationSessionLifecycle()
    onPageHide?.({ persisted: true })
    expect(api.endSession).not.toHaveBeenCalled()
    expect(storage.size).toBe(1)
  })
  it('계정 전환 전에 등록된 오래된 종료 콜백은 전송하지 않는다', () => {
    useRecommendationSessionLifecycle()
    auth.userId = '202'
    onPageHide?.({ persisted: false })
    expect(api.endSession).not.toHaveBeenCalled()
  })
})
