import { useEffect } from 'react'

import { recommendationApi } from '@/entities/recommendation/api'
import type { RecommendationEvent } from '@/entities/recommendation/model'
import { useIsSignedIn } from '@/features/auth/sessionStore'

const SESSION_KEY = 'gongguhan.recommendation-session'

export function recommendationSessionId(): string {
  const saved = sessionStorage.getItem(SESSION_KEY)
  if (saved !== null) return saved
  const created = crypto.randomUUID()
  sessionStorage.setItem(SESSION_KEY, created)
  return created
}

export async function recordRecommendationEvents(
  events: readonly RecommendationEvent[],
): Promise<void> {
  if (events.length === 0) return
  try {
    await recommendationApi.recordEvents(recommendationSessionId(), events)
  } catch {
    // 추천 로그 실패가 상품 탐색을 막아서는 안 됩니다.
  }
}

/** 탭 종료·새로고침 때 현재 세션을 닫습니다. 시연용 1초 무활동 판정을 서버가 보완합니다. */
export function useRecommendationSessionLifecycle(): void {
  const isSignedIn = useIsSignedIn()

  useEffect(() => {
    if (!isSignedIn) return

    const close = (event: PageTransitionEvent) => {
      if (event.persisted) return
      const sessionId = sessionStorage.getItem(SESSION_KEY)
      if (sessionId === null) return
      sessionStorage.removeItem(SESSION_KEY)
      void recommendationApi.endSession(sessionId).catch(() => {
        // pagehide 중 실패해도 서버의 1초 무활동 판정이 세션을 학습 대상에 포함합니다.
      })
    }
    window.addEventListener('pagehide', close)
    return () => {
      window.removeEventListener('pagehide', close)
    }
  }, [isSignedIn])
}
