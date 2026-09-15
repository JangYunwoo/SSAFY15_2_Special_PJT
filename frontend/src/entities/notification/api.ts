import { api } from '@/shared/api/client'
import type { Id, NoContent } from '@/shared/api/types'

import type { Notification, NotificationListParams } from './model'

/** 백엔드 NotificationResponse. id 가 숫자로 오고 이름도 다릅니다. */
interface NotificationApiResponse {
  notificationId: number | string
  type: string
  title: string
  body: string
  linkUrl: string | null
  read: boolean
  createdAt: string
}

function toNotification(response: NotificationApiResponse): Notification {
  return {
    id: String(response.notificationId),
    type: response.type,
    title: response.title,
    body: response.body,
    linkUrl: response.linkUrl,
    read: response.read,
    createdAt: response.createdAt,
  }
}

/** 알림 API (NOTI-01 ~ NOTI-03) */
export const notificationApi = {
  list: (params: NotificationListParams = {}) =>
    api
      .getPage<NotificationApiResponse>('/notifications', { query: { ...params } })
      .then((page) => ({ ...page, items: page.items.map(toNotification) })),

  /** 백엔드가 주는 이름은 unreadCount 입니다. */
  unreadCount: () =>
    api
      .get<{ unreadCount: number }>('/notifications/unread-count')
      .then((data) => ({ count: data.unreadCount })),

  markRead: (notificationId: Id) => api.post<NoContent>(`/notifications/${notificationId}/read`),
} as const
