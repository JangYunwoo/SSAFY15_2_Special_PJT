import { api } from '@/shared/api/client'
import type { Id, NoContent } from '@/shared/api/types'

import type { Notification, NotificationListParams } from './model'

/** 알림 API (NOTI-01 ~ NOTI-03) */
export const notificationApi = {
  list: (params: NotificationListParams = {}) =>
    api.getPage<Notification>('/notifications', { query: { ...params } }),
  unreadCount: () => api.get<{ count: number }>('/notifications/unread-count'),
  markRead: (notificationId: Id) => api.post<NoContent>(`/notifications/${notificationId}/read`),
} as const
