import { apiRequest } from './request'
import type {
  NotificationCategory,
  NotificationDeleteBatchResponse,
  NotificationDeleteResponse,
  NotificationDetailResponse,
  NotificationListResponse,
  NotificationReadAllResponse,
  NotificationReadResponse,
  NotificationUnreadCountResponse
} from './types'

export function getNotificationUnreadCount() {
  return apiRequest<NotificationUnreadCountResponse>({
    path: '/api/notifications/unread-count'
  })
}

export function getNotifications(category?: NotificationCategory | null, cursor?: string | null, size = 20) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (category) {
    query.set('category', category)
  }
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<NotificationListResponse>({
    path: `/api/notifications?${query.toString()}`
  })
}

export function getNotificationDetail(notificationId: string) {
  return apiRequest<NotificationDetailResponse>({
    path: `/api/notifications/${notificationId}`
  })
}

export function markNotificationRead(notificationId: string) {
  return apiRequest<NotificationReadResponse>({
    method: 'POST',
    path: `/api/notifications/${notificationId}/read`
  })
}

export function markAllNotificationsRead(category?: NotificationCategory | null) {
  return apiRequest<NotificationReadAllResponse>({
    method: 'POST',
    path: '/api/notifications/read-all',
    data: {
      category: category ?? null
    }
  })
}

export function deleteNotification(notificationId: string) {
  return apiRequest<NotificationDeleteResponse>({
    method: 'DELETE',
    path: `/api/notifications/${notificationId}`
  })
}

export function deleteNotifications(notificationIds: string[]) {
  return apiRequest<NotificationDeleteBatchResponse>({
    method: 'DELETE',
    path: '/api/notifications',
    data: {
      notificationIds
    }
  })
}
