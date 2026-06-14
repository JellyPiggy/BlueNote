import { apiRequest } from './request'
import type { FollowActionResponse, FollowStatusResponse } from './types'

export function followUser(followeeId: string) {
  return apiRequest<FollowActionResponse>({
    method: 'POST',
    path: `/api/relations/following/${followeeId}`
  })
}

export function unfollowUser(followeeId: string) {
  return apiRequest<FollowActionResponse>({
    method: 'DELETE',
    path: `/api/relations/following/${followeeId}`
  })
}

export function getFollowStatus(targetUserId: string) {
  return apiRequest<FollowStatusResponse>({
    path: `/api/relations/following/${targetUserId}/status`
  })
}
