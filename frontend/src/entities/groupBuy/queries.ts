import { keepPreviousData, queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'
import type { Id, PageParams } from '@/shared/api/types'

import { groupBuyApi } from './api'
import type { GroupBuyListParams, GroupBuyPreviewRequest } from './model'

/**
 * queryOptions 팩토리로 키와 fetcher 를 묶어둡니다.
 * 컴포넌트에서는 useQuery(groupBuyQueries.list(params)) 처럼 쓰고,
 * 무효화는 queryKeys 로 합니다.
 */
export const groupBuyQueries = {
  list: (params: GroupBuyListParams) =>
    queryOptions({
      queryKey: queryKeys.groupBuys.list(params),
      queryFn: () => groupBuyApi.list(params),
      // 쪽을 옮기는 동안 이전 목록을 그대로 두어 화면이 비지 않게 합니다.
      placeholderData: keepPreviousData,
    }),

  detail: (groupBuyId: Id) =>
    queryOptions({
      queryKey: queryKeys.groupBuys.detail(groupBuyId),
      queryFn: () => groupBuyApi.detail(groupBuyId),
    }),

  /**
   * GRP-03 금액 미리보기.
   *
   * POST 이지만 서버가 값을 바꾸지 않는 계산이라 쿼리로 다룹니다.
   * 입력이 바뀌는 동안 숫자가 사라지지 않도록 이전 결과를 유지합니다.
   */
  preview: (body: GroupBuyPreviewRequest) =>
    queryOptions({
      queryKey: queryKeys.sellerGroupBuys.preview(body),
      queryFn: () => groupBuyApi.preview(body),
      placeholderData: keepPreviousData,
    }),

  sellerList: (params: PageParams = {}) =>
    queryOptions({
      queryKey: queryKeys.sellerGroupBuys.list(params),
      queryFn: () => groupBuyApi.sellerList(params),
    }),

  sellerDetail: (groupBuyId: Id) =>
    queryOptions({
      queryKey: queryKeys.sellerGroupBuys.detail(groupBuyId),
      queryFn: () => groupBuyApi.sellerDetail(groupBuyId),
    }),

  participants: (groupBuyId: Id, params: PageParams = {}) =>
    queryOptions({
      queryKey: queryKeys.sellerGroupBuys.participants(groupBuyId, params),
      queryFn: () => groupBuyApi.participants(groupBuyId, params),
      placeholderData: keepPreviousData,
    }),
} as const
