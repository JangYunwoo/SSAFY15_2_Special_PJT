import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'

import { authApi } from './api'

export const authQueries = {
  /** USR-01 내 프로필 */
  me: () =>
    queryOptions({
      queryKey: queryKeys.auth.me(),
      queryFn: () => authApi.me(),
    }),
} as const
