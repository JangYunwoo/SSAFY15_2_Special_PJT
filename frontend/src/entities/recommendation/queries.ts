import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'

import { recommendationApi } from './api'

export const recommendationQueries = {
  list: (size = 8) =>
    queryOptions({
      queryKey: queryKeys.recommendations.list(size),
      queryFn: () => recommendationApi.list(size),
      staleTime: 30_000,
    }),
} as const
