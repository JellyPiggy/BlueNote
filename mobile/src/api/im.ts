import { apiRequest } from './request'
import type {
  ImConversationItem,
  ImConversationListResponse,
  ImMessageListResponse,
  ImReadResponse,
  ImReceivedResponse,
  ImSendMessageResponse,
  ImUnreadCountResponse
} from './types'

export interface ImConversationSettingsRequest {
  pinned?: boolean
  mute?: boolean
}

export function createSingleConversation(targetUserId: string) {
  return apiRequest<ImConversationItem>({
    method: 'POST',
    path: '/api/im/conversations/single',
    data: { targetUserId }
  })
}

export function getImConversations(cursor?: string | null, pageSize = 20) {
  const query = new URLSearchParams()
  query.set('pageSize', String(pageSize))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<ImConversationListResponse>({
    path: `/api/im/conversations?${query.toString()}`
  })
}

export function sendImMessage(payload: {
  conversationId?: string | null
  targetUserId?: string | null
  clientMsgId: string
  text: string
}) {
  return apiRequest<ImSendMessageResponse>({
    method: 'POST',
    path: '/api/im/messages',
    data: {
      conversationId: payload.conversationId ?? null,
      targetUserId: payload.targetUserId ?? null,
      clientMsgId: payload.clientMsgId,
      messageType: 'TEXT',
      content: {
        text: payload.text
      }
    }
  })
}

export function getImMessages(conversationId: string, options?: {
  afterSeq?: number | null
  beforeSeq?: number | null
  limit?: number
}) {
  const query = new URLSearchParams()
  query.set('limit', String(options?.limit ?? 30))
  if (options?.afterSeq != null) {
    query.set('afterSeq', String(options.afterSeq))
  }
  if (options?.beforeSeq != null) {
    query.set('beforeSeq', String(options.beforeSeq))
  }
  return apiRequest<ImMessageListResponse>({
    path: `/api/im/conversations/${conversationId}/messages?${query.toString()}`
  })
}

export function markImConversationReceived(conversationId: string, receivedSeq: number) {
  return apiRequest<ImReceivedResponse>({
    method: 'POST',
    path: `/api/im/conversations/${conversationId}/received`,
    data: { receivedSeq }
  })
}

export function markImConversationRead(conversationId: string, readSeq: number) {
  return apiRequest<ImReadResponse>({
    method: 'POST',
    path: `/api/im/conversations/${conversationId}/read`,
    data: { readSeq }
  })
}

export function getImUnreadCount() {
  return apiRequest<ImUnreadCountResponse>({
    path: '/api/im/unread-count'
  })
}

export function updateImConversationSettings(conversationId: string, payload: ImConversationSettingsRequest) {
  return apiRequest<ImConversationItem>({
    method: 'PUT',
    path: `/api/im/conversations/${conversationId}/settings`,
    data: payload
  })
}

export function deleteImConversation(conversationId: string) {
  return apiRequest<{ conversationId: string; deleted: boolean }>({
    method: 'DELETE',
    path: `/api/im/conversations/${conversationId}`
  })
}
