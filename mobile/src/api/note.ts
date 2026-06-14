import { apiRequest } from './request'
import type {
  CursorPage,
  DraftNoteResponse,
  NoteCard,
  NoteCollectResponse,
  NoteDetail,
  NoteLikeResponse,
  PublishNoteResponse,
  UpsertNoteRequest
} from './types'

export function publishNote(payload: UpsertNoteRequest, idempotencyKey: string) {
  return apiRequest<PublishNoteResponse>({
    method: 'POST',
    path: '/api/notes',
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    data: payload
  })
}

export function saveDraft(payload: UpsertNoteRequest, idempotencyKey: string) {
  return apiRequest<DraftNoteResponse>({
    method: 'POST',
    path: '/api/notes/drafts',
    headers: {
      'Idempotency-Key': idempotencyKey
    },
    data: payload
  })
}

export function getNoteDetail(noteId: string, auth = true) {
  return apiRequest<NoteDetail>({
    path: `/api/notes/${noteId}`,
    auth
  })
}

export function likeNote(noteId: string) {
  return apiRequest<NoteLikeResponse>({
    method: 'POST',
    path: `/api/notes/${noteId}/like`
  })
}

export function unlikeNote(noteId: string) {
  return apiRequest<NoteLikeResponse>({
    method: 'DELETE',
    path: `/api/notes/${noteId}/like`
  })
}

export function collectNote(noteId: string) {
  return apiRequest<NoteCollectResponse>({
    method: 'POST',
    path: `/api/notes/${noteId}/collect`
  })
}

export function uncollectNote(noteId: string) {
  return apiRequest<NoteCollectResponse>({
    method: 'DELETE',
    path: `/api/notes/${noteId}/collect`
  })
}

export function getAuthorNotes(userId: string, cursor?: string | null, size = 20) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<CursorPage<NoteCard>>({
    path: `/api/notes/users/${userId}?${query.toString()}`,
    auth: false
  })
}

export function getPublicTimeline(cursor?: string | null, size = 20) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<CursorPage<NoteCard>>({
    path: `/api/notes?${query.toString()}`,
    auth: false
  })
}

export function getMyNotes(status?: string, cursor?: string | null, size = 20) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (status) {
    query.set('status', status)
  }
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<CursorPage<NoteCard>>({
    path: `/api/notes/me?${query.toString()}`
  })
}

export function getMyCollections(cursor?: string | null, size = 20) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<CursorPage<NoteCard>>({
    path: `/api/notes/me/collections?${query.toString()}`
  })
}

export function getMyLikedNotes(cursor?: string | null, size = 20) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<CursorPage<NoteCard>>({
    path: `/api/notes/me/likes?${query.toString()}`
  })
}
