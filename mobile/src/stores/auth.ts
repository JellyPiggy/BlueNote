import { defineStore } from 'pinia'
import { login, logout, refreshToken, register, type AuthPayload } from '@/api/auth'
import { getCurrentUser } from '@/api/user'
import type { TokenPair, UserProfile } from '@/api/types'
import { getDeviceInfo } from '@/utils/device'

const SESSION_KEY = 'bluenote.session'

interface StoredSession {
  userId: string
  accessToken: string
  refreshToken: string
  accessTokenExpireAt: number
  refreshTokenExpireAt: number
}

interface AuthState {
  userId: string | null
  accessToken: string | null
  refreshTokenValue: string | null
  accessTokenExpireAt: number | null
  refreshTokenExpireAt: number | null
  profile: UserProfile | null
  profileLoading: boolean
  submitting: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    userId: null,
    accessToken: null,
    refreshTokenValue: null,
    accessTokenExpireAt: null,
    refreshTokenExpireAt: null,
    profile: null,
    profileLoading: false,
    submitting: false
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken && state.refreshTokenValue),
    isProfileReady: (state) => Boolean(state.profile && state.profile.userStatus === 'NORMAL')
  },
  actions: {
    restoreSession() {
      const raw = uni.getStorageSync(SESSION_KEY)
      if (!raw) {
        return
      }
      try {
        const session = JSON.parse(String(raw)) as StoredSession
        this.userId = session.userId
        this.accessToken = session.accessToken
        this.refreshTokenValue = session.refreshToken
        this.accessTokenExpireAt = session.accessTokenExpireAt
        this.refreshTokenExpireAt = session.refreshTokenExpireAt
      } catch {
        this.clearSession()
      }
    },
    async loginWithPassword(username: string, password: string) {
      this.submitting = true
      try {
        const tokenPair = await login({
          username,
          password,
          ...getDeviceInfo()
        } satisfies AuthPayload)
        this.applyTokenPair(tokenPair)
        await this.fetchCurrentUser()
      } finally {
        this.submitting = false
      }
    },
    async registerWithPassword(username: string, password: string) {
      this.submitting = true
      try {
        const tokenPair = await register({
          username,
          password,
          ...getDeviceInfo()
        } satisfies AuthPayload)
        this.applyTokenPair(tokenPair)
        await this.fetchCurrentUser()
      } finally {
        this.submitting = false
      }
    },
    async refreshTokens() {
      if (!this.refreshTokenValue) {
        this.clearSession()
        throw new Error('missing refresh token')
      }
      try {
        const tokenPair = await refreshToken(this.refreshTokenValue, getDeviceInfo().deviceId)
        this.applyTokenPair(tokenPair)
      } catch (error) {
        this.clearSession()
        throw error
      }
    },
    async fetchCurrentUser() {
      if (!this.accessToken) {
        return
      }
      this.profileLoading = true
      try {
        this.profile = await getCurrentUser()
      } finally {
        this.profileLoading = false
      }
    },
    async logoutCurrentDevice() {
      const refresh = this.refreshTokenValue
      this.clearSession()
      if (!refresh) {
        return
      }
      try {
        await logout(refresh)
      } catch {
        // 本地退出优先，服务端失败不阻止用户离开登录态。
      }
    },
    applyTokenPair(tokenPair: TokenPair) {
      const now = Date.now()
      this.userId = tokenPair.userId
      this.accessToken = tokenPair.accessToken
      this.refreshTokenValue = tokenPair.refreshToken
      this.accessTokenExpireAt = now + tokenPair.accessTokenExpiresIn * 1000
      this.refreshTokenExpireAt = now + tokenPair.refreshTokenExpiresIn * 1000
      this.persistSession()
    },
    clearSession() {
      this.userId = null
      this.accessToken = null
      this.refreshTokenValue = null
      this.accessTokenExpireAt = null
      this.refreshTokenExpireAt = null
      this.profile = null
      uni.removeStorageSync(SESSION_KEY)
    },
    persistSession() {
      if (!this.userId || !this.accessToken || !this.refreshTokenValue) {
        return
      }
      const session: StoredSession = {
        userId: this.userId,
        accessToken: this.accessToken,
        refreshToken: this.refreshTokenValue,
        accessTokenExpireAt: this.accessTokenExpireAt ?? 0,
        refreshTokenExpireAt: this.refreshTokenExpireAt ?? 0
      }
      uni.setStorageSync(SESSION_KEY, JSON.stringify(session))
    }
  }
})

