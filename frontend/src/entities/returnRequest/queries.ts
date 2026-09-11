import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'
import type { Id } from '@/shared/api/types'

import { returnApi } from './api'
import type { ReturnListParams } from './model'

export const returnQueries = {
  buyerList: (params: ReturnListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.returns.buyerList(params),
      queryFn: () => returnApi.buyerList(params),
    }),

  buyerDetail: (returnId: Id) =>
    queryOptions({
      queryKey: queryKeys.returns.buyerDetail(returnId),
      queryFn: () => returnApi.buyerDetail(returnId),
    }),

  sellerList: (params: ReturnListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.returns.sellerList(params),
      queryFn: () => returnApi.sellerList(params),
    }),
} as const
