import { apiRequest } from './request'
import type { FeedCard, FollowingFeedResponse, NoteCard } from './types'

export function getFollowingFeed(cursor?: string | null, size = 20) {
  const query = new URLSearchParams()
  query.set('size', String(size))
  if (cursor) {
    query.set('cursor', cursor)
  }
  return apiRequest<FollowingFeedResponse>({
    path: `/api/feed/following?${query.toString()}`
  })
}

export function feedCardToNoteCard(item: FeedCard): NoteCard {
  return {
    noteId: item.noteId,
    title: item.title,
    coverUrl: item.coverUrl,
    authorId: item.author.userId,
    authorNickname: item.author.nickname,
    authorAvatarUrl: item.author.avatarUrl,
    likeCount: item.counts.likeCount,
    collectCount: item.counts.collectCount,
    publishedAt: item.publishedAt
  }
}
