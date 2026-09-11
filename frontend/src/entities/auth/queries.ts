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

  /** SEL-06 판매자 프로필. 판매자로 전환된 계정만 호출할 수 있습니다. */
  sellerMe: () =>
    queryOptions({
      queryKey: queryKeys.auth.sellerMe(),
      queryFn: () => authApi.sellerMe(),
    }),
} as const
