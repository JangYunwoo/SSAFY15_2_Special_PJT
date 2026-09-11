import { api } from '@/shared/api/client'
import type { Id } from '@/shared/api/types'

import type {
  PenaltyOverview,
  SettlementDetail,
  SettlementListParams,
  SettlementSummary,
} from './model'

/** 정산·제재 API (STL-01, STL-02, PEN-01) */
export const settlementApi = {
  /** STL-01 */
  list: (params: SettlementListParams = {}) =>
    api.getPage<SettlementSummary>('/sellers/settlements', { query: { ...params } }),

  /** STL-02 포함·제외 참여 건까지 함께 조회 */
  detail: (settlementId: Id) => api.get<SettlementDetail>(`/sellers/settlements/${settlementId}`),

  /** PEN-01 */
  myPenalties: () => api.get<PenaltyOverview>('/sellers/me/penalties'),
} as const
