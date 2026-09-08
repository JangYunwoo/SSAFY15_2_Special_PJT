import { queryOptions } from '@tanstack/react-query'

import { queryKeys } from '@/shared/api/queryKeys'

import { notificationApi } from './api'
import type { NotificationListParams } from './model'

export const notificationQueries = {
  list: (params: NotificationListParams = {}) =>
    queryOptions({
      queryKey: queryKeys.notifications.list(params),
      queryFn: () => notificationApi.list(params),
    }),

  unreadCount: () =>
    queryOptions({
      queryKey: queryKeys.notifications.unreadCount(),
      queryFn: () => notificationApi.unreadCount(),
      refetchInterval: 60_000,
    }),
} as const
