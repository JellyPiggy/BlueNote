import { useAuthStore } from '@/stores/auth'
import type { ApiResponse } from './types'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

export class BlueNoteApiError extends Error {
  code: number
  reason: string
  traceId?: string
  data: unknown

  constructor(response: ApiResponse<unknown>) {
    super(response.message || '请求失败')
    this.name = 'BlueNoteApiError'
    this.code = response.code
    this.reason = reasonOf(response.data)
    this.traceId = response.traceId
    this.data = response.data
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  path: string
  data?: unknown
  headers?: Record<string, string>
  auth?: boolean
  skipRefresh?: boolean
}

let refreshPromise: Promise<void> | null = null

export async function apiRequest<T>(options: RequestOptions): Promise<T> {
  const response = await rawRequest<T>(options)
  if (response.code === 0) {
    return response.data
  }

  if (response.code === 20001 && options.auth !== false && !options.skipRefresh) {
    const auth = useAuthStore()
    refreshPromise ??= auth.refreshTokens().finally(() => {
      refreshPromise = null
    })
    await refreshPromise
    const retried = await rawRequest<T>({ ...options, skipRefresh: true })
    if (retried.code === 0) {
      return retried.data
    }
    throw new BlueNoteApiError(retried as ApiResponse<unknown>)
  }

  throw new BlueNoteApiError(response as ApiResponse<unknown>)
}

async function rawRequest<T>(options: RequestOptions): Promise<ApiResponse<T>> {
  const auth = useAuthStore()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Request-Id': requestId(),
    ...(options.headers ?? {})
  }
  if (options.auth !== false && auth.accessToken) {
    headers.Authorization = `Bearer ${auth.accessToken}`
  }

  const result = await uni.request({
    url: `${API_BASE_URL}${options.path}`,
    method: options.method ?? 'GET',
    header: headers,
    data: options.data as string | ArrayBuffer | Record<string, unknown> | undefined
  })

  return result.data as ApiResponse<T>
}

export function showApiError(error: unknown, fallback = '操作失败，请稍后再试') {
  const message = error instanceof BlueNoteApiError ? error.message : fallback
  uni.showToast({
    title: message,
    icon: 'none'
  })
}

function reasonOf(data: unknown): string {
  if (data && typeof data === 'object' && 'reason' in data) {
    return String((data as { reason?: unknown }).reason ?? '')
  }
  return ''
}

function requestId() {
  return `mobile-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
