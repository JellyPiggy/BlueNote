import { apiRequest } from './request'
import type { UpdateProfileRequest, UpdateProfileResponse, UserHome, UserProfile, UserSummary } from './types'

export function getCurrentUser() {
  return apiRequest<UserProfile>({
    path: '/api/users/me'
  })
}

export function getPublicProfile(userId: string) {
  return apiRequest<UserSummary>({
    path: `/api/users/${userId}/public`,
    auth: false
  })
}

export function getUserHome(userId: string, withAuth = true) {
  return apiRequest<UserHome>({
    path: `/api/users/${userId}/home`,
    auth: withAuth
  })
}

export function updateCurrentUserProfile(payload: UpdateProfileRequest) {
  return apiRequest<UpdateProfileResponse>({
    method: 'PUT',
    path: '/api/users/me/profile',
    data: payload
  })
}
