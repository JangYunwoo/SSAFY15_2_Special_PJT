import { api } from '@/shared/api/client'
import type { NoContent } from '@/shared/api/types'

import type { RecommendationEvent, RecommendationResult } from './model'

export const recommendationApi = {
  list: (size = 8) =>
    api.get<RecommendationResult>('/recommendations/group-buys', { query: { size } }),

  recordEvents: (sessionId: string, events: readonly RecommendationEvent[]) =>
    api.post<NoContent>('/recommendations/events', { sessionId, events }),

  endSession: (sessionId: string) =>
    api.postKeepalive<NoContent>('/recommendations/sessions/end', { sessionId }),
} as const
