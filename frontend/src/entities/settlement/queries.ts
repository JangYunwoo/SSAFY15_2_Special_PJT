import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'
import type { Id } from '@/shared/api/types'

import { settlementApi } from './api'
import type { SettlementListParams } from './model'

export const settlementQueries = {
  list: (params: SettlementListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.settlements.list(params),
      queryFn: () => settlementApi.list(params),
    }),

  detail: (settlementId: Id) =>
    queryOptions({
      queryKey: queryKeys.settlements.detail(settlementId),
      queryFn: () => settlementApi.detail(settlementId),
    }),

  myPenalties: () =>
    queryOptions({
      queryKey: queryKeys.penalties.mine(),
      queryFn: () => settlementApi.myPenalties(),
    }),
} as const
