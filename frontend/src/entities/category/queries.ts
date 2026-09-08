import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'

import { categoryApi } from './api'

export const categoryQueries = {
  tree: () =>
    queryOptions({
      queryKey: queryKeys.categories.tree(),
      queryFn: () => categoryApi.tree(),
      // 카테고리는 거의 바뀌지 않습니다.
      staleTime: 60 * 60 * 1000,
    }),
} as const
