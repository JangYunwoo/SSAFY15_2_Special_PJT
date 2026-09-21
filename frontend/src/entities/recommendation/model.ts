import type { GroupBuySummary } from '@/entities/groupBuy/model'
import type { Id } from '@/shared/api/types'

export type RecommendationEventType = 'IMPRESSION' | 'CLICK' | 'DETAIL_VIEW'

export interface RecommendationEvent {
  groupBuyId: Id
  eventType: RecommendationEventType
  source: 'HOME_RECOMMENDATION' | 'HOME_POPULAR' | 'GROUP_BUY_DETAIL'
  position?: number
}

export interface RecommendedGroupBuy {
  groupBuy: GroupBuySummary
  score: number
  reason: string
}

export interface RecommendationResult {
  items: RecommendedGroupBuy[]
  modelType: 'LOGISTIC_REGRESSION' | 'HEURISTIC_FALLBACK' | 'BACKEND_FALLBACK' | 'EMPTY'
  featureWeights: Record<string, number>
}
