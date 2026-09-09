import { keepPreviousData, queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'
import type { Id } from '@/shared/api/types'

import { productApi } from './api'
import type { ProductListParams } from './model'

export const productQueries = {
  list: (params: ProductListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.sellerProducts.list(params),
      queryFn: () => productApi.list(params),
      // 쪽을 옮기는 동안 이전 목록을 그대로 두어 화면이 비지 않게 합니다.
      placeholderData: keepPreviousData,
    }),

  detail: (productId: Id) =>
    queryOptions({
      queryKey: queryKeys.sellerProducts.detail(productId),
      queryFn: () => productApi.detail(productId),
    }),

  withdrawalReasons: () =>
    queryOptions({
      queryKey: queryKeys.withdrawalReasons.active(),
      queryFn: () => productApi.withdrawalReasons(),
      staleTime: 60 * 60 * 1000,
    }),
} as const
