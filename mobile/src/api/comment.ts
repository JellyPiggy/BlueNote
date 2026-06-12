import { apiRequest } from './request'
import type {
  CommentCursorPage,
  CommentItem,
  CommentLikeResponse,
  CreateCommentRequest,
  CreateCommentResponse,
  DeleteCommentResponse
} from './types'

export function getNoteComments(noteId: string, cursor?: string | null, size = 20, auth = true) {
  const query = new URLSearchParams()
  query.set('sort', 'HOT')
  query.set('size', String(size))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<CommentCursorPage<CommentItem>>({
    path: `/api/comments/notes/${noteId}?${query.toString()}`,
    auth
  })
}

export function getCommentReplies(rootCommentId: string, cursor?: string | null, size = 20, auth = true) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<CommentCursorPage<CommentItem>>({
    path: `/api/comments/${rootCommentId}/replies?${query.toString()}`,
    auth
  })
}

export function createNoteComment(noteId: string, payload: CreateCommentRequest, idempotencyKey: string) {
  return apiRequest<CreateCommentResponse>({
    method: 'POST',
    path: `/api/comments/notes/${noteId}`,
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    data: payload
  })
}

export function replyToComment(commentId: string, payload: CreateCommentRequest, idempotencyKey: string) {
  return apiRequest<CreateCommentResponse>({
    method: 'POST',
    path: `/api/comments/${commentId}/replies`,
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    data: payload
  })
}

export function deleteComment(commentId: string) {
  return apiRequest<DeleteCommentResponse>({
    method: 'DELETE',
    path: `/api/comments/${commentId}`
  })
}

export function likeComment(commentId: string) {
  return apiRequest<CommentLikeResponse>({
    method: 'POST',
    path: `/api/comments/${commentId}/like`
  })
}

export function unlikeComment(commentId: string) {
  return apiRequest<CommentLikeResponse>({
    method: 'DELETE',
    path: `/api/comments/${commentId}/like`
  })
}
