import { useEffect } from 'react'

import { recommendationApi } from '@/entities/recommendation/api'
import type { RecommendationEvent } from '@/entities/recommendation/model'
import { useSessionStore } from '@/features/auth/sessionStore'

const SESSION_KEY = 'gongguhan.recommendation-session'

function savedSession(userId: string): string | null {
  const raw = sessionStorage.getItem(SESSION_KEY)
  if (raw === null) return null
  try {
    const saved: unknown = JSON.parse(raw)
    if (typeof saved === 'object' && saved !== null && 'userId' in saved && 'id' in saved
        && saved.userId === userId && typeof saved.id === 'string'
        && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(saved.id)) return saved.id
  } catch { /* 소유자를 알 수 없는 이전 버전 UUID는 새로 발급한다. */ }
  return null
}

export function recommendationSessionId(): string | null {
  const { actor, accessToken } = useSessionStore.getState()
  if (actor === null || accessToken === null) {
    sessionStorage.removeItem(SESSION_KEY)
    return null
  }
  const saved = savedSession(actor.id)
  if (saved !== null) return saved
  const created = crypto.randomUUID()
  sessionStorage.setItem(SESSION_KEY, JSON.stringify({ userId: actor.id, id: created }))
  return created
}

export async function recordRecommendationEvents(
  events: readonly RecommendationEvent[],
): Promise<void> {
  if (events.length === 0) return
  try {
    const sessionId = recommendationSessionId()
    if (sessionId === null) return
    await recommendationApi.recordEvents(sessionId, events)
  } catch {
    // 추천 로그 실패가 상품 탐색을 막아서는 안 됩니다.
  }
}

/** 탭 종료·새로고침 때 현재 세션을 닫습니다. 시연용 1초 무활동 판정을 서버가 보완합니다. */
export function useRecommendationSessionLifecycle(): void {
  const userId = useSessionStore((state) => state.accessToken !== null ? state.actor?.id ?? null : null)

  useEffect(() => {
    try { recommendationSessionId() } catch { /* 저장소 차단은 페이지 사용을 막지 않는다. */ }
    if (userId === null) return

    const close = (event: PageTransitionEvent) => {
      if (event.persisted) return
      const current = useSessionStore.getState()
      if (current.accessToken === null || current.actor?.id !== userId) return
      try {
        const sessionId = savedSession(userId)
        if (sessionId === null) return
        sessionStorage.removeItem(SESSION_KEY)
        void recommendationApi.endSession(sessionId).catch(() => {
          // pagehide 중 실패해도 서버의 1초 무활동 판정이 세션을 학습 대상에 포함합니다.
        })
      } catch { /* 저장소가 차단돼도 종료를 방해하지 않는다. */ }
    }
    window.addEventListener('pagehide', close)
    return () => {
      window.removeEventListener('pagehide', close)
    }
  }, [userId])
}
