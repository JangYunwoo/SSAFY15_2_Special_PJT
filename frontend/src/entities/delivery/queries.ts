import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'
import type { Id } from '@/shared/api/types'

import { deliveryApi } from './api'

export const deliveryQueries = {
  board: (groupBuyId: Id) =>
    queryOptions({
      queryKey: queryKeys.sellerGroupBuys.deliveries(groupBuyId),
      queryFn: () => deliveryApi.board(groupBuyId),
    }),

  forParticipation: (participationId: Id) =>
    queryOptions({
      queryKey: queryKeys.participations.delivery(participationId),
      queryFn: () => deliveryApi.forParticipation(participationId),
    }),
} as const
