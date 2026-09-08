import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'

import { addressApi } from './api'

export const addressQueries = {
  list: () =>
    queryOptions({
      queryKey: queryKeys.addresses.all,
      queryFn: () => addressApi.list(),
    }),
} as const
