import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'
import type { Id } from '@/shared/api/types'

import { participationApi } from './api'
import type { ParticipationListParams } from './model'

export const participationQueries = {
  list: (params: ParticipationListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.participations.list(params),
      queryFn: () => participationApi.list(params),
    }),

  detail: (participationId: Id) =>
    queryOptions({
      queryKey: queryKeys.participations.detail(participationId),
      queryFn: () => participationApi.detail(participationId),
    }),

  escrow: (participationId: Id) =>
    queryOptions({
      queryKey: queryKeys.participations.escrow(participationId),
      queryFn: () => participationApi.escrow(participationId),
    }),
} as const
