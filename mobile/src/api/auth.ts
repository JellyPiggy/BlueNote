import { apiRequest } from './request'
import type { DeviceInfo, TokenPair } from './types'

export interface AuthPayload extends DeviceInfo {
  username: string
  password: string
}

export function register(payload: AuthPayload) {
  return apiRequest<TokenPair>({
    method: 'POST',
    path: '/api/auth/register',
    data: payload,
    auth: false
  })
}

export function login(payload: AuthPayload) {
  return apiRequest<TokenPair>({
    method: 'POST',
    path: '/api/auth/login',
    data: payload,
    auth: false
  })
}

export function refreshToken(refreshToken: string, deviceId: string) {
  return apiRequest<TokenPair>({
    method: 'POST',
    path: '/api/auth/token/refresh',
    data: {
      refreshToken,
      deviceId
    },
    auth: false,
    skipRefresh: true
  })
}

export function logout(refreshToken: string) {
  return apiRequest<{ success: boolean }>({
    method: 'POST',
    path: '/api/auth/logout',
    data: {
      refreshToken
    },
    skipRefresh: true
  })
}

