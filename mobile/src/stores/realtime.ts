import { defineStore } from 'pinia'
import { registerPushDevice } from '@/api/push'
import type { RealtimePushMessage } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { getPushDeviceRegistration } from '@/utils/device'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

type RealtimeStatus = 'idle' | 'registering' | 'connecting' | 'connected' | 'closed'

interface RealtimeState {
  status: RealtimeStatus
  deviceId: string | null
  websocketUrl: string | null
  lastMessageAt: string | null
  lastError: string | null
}

let socketTask: UniApp.SocketTask | null = null
let pingTimer: ReturnType<typeof setInterval> | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let manualClose = false

export const useRealtimeStore = defineStore('realtime', {
  state: (): RealtimeState => ({
    status: 'idle',
    deviceId: null,
    websocketUrl: null,
    lastMessageAt: null,
    lastError: null
  }),
  getters: {
    connected: (state) => state.status === 'connected'
  },
  actions: {
    async start() {
      const auth = useAuthStore()
      if (!auth.accessToken) {
        this.stop()
        return
      }
      if (this.status === 'registering' || this.status === 'connecting' || this.status === 'connected') {
        return
      }
      this.status = 'registering'
      this.lastError = null
      try {
        const registration = getPushDeviceRegistration()
        const response = await registerPushDevice(registration)
        this.deviceId = response.deviceId
        this.websocketUrl = response.websocketUrl
        if (!response.realtimeEnabled || !response.websocketUrl) {
          this.status = 'closed'
          return
        }
        this.connect(response.websocketUrl, response.deviceId)
      } catch (error) {
        this.status = 'closed'
        this.lastError = error instanceof Error ? error.message : 'register failed'
        this.scheduleReconnect()
      }
    },
    stop() {
      manualClose = true
      clearTimers()
      const socket = socketTask
      socketTask = null
      this.status = 'idle'
      if (socket) {
        socket.close({ code: 1000, reason: 'client stop' })
      }
    },
    connect(websocketUrl: string, deviceId: string) {
      const auth = useAuthStore()
      if (!auth.accessToken) {
        this.stop()
        return
      }
      manualClose = false
      clearTimers()
      this.status = 'connecting'
      const url = realtimeUrl(websocketUrl, deviceId, auth.accessToken)
      socketTask = uni.connectSocket({
        url,
        success: () => undefined,
        fail: (error) => {
          this.status = 'closed'
          this.lastError = String(error.errMsg || 'connect failed')
          this.scheduleReconnect()
        }
      })
      socketTask.onOpen(() => {
        this.status = 'connected'
        this.lastError = null
        pingTimer = setInterval(() => {
          this.send({ type: 'PING', clientTime: new Date().toISOString() })
        }, 30000)
      })
      socketTask.onMessage((event) => {
        this.handleMessage(event.data)
      })
      socketTask.onError((error) => {
        this.status = 'closed'
        this.lastError = String(error.errMsg || 'socket error')
      })
      socketTask.onClose(() => {
        clearTimers()
        socketTask = null
        if (!manualClose) {
          this.status = 'closed'
          this.scheduleReconnect()
        }
      })
    },
    send(payload: Record<string, unknown>) {
      if (!socketTask || this.status !== 'connected') {
        return
      }
      socketTask.send({
        data: JSON.stringify(payload)
      })
    },
    handleMessage(raw: string | ArrayBuffer) {
      if (typeof raw !== 'string') {
        return
      }
      let payload: Record<string, unknown>
      try {
        payload = JSON.parse(raw) as Record<string, unknown>
      } catch {
        return
      }
      if (payload.type === 'PUSH_MESSAGE') {
        const message = payload as unknown as RealtimePushMessage
        this.lastMessageAt = message.sentAt
        this.send({ type: 'ACK', requestId: message.requestId, receivedAt: new Date().toISOString() })
        const notifications = useNotificationStore()
        void notifications.refreshUnread()
      }
    },
    scheduleReconnect() {
      const auth = useAuthStore()
      if (manualClose || !auth.accessToken) {
        return
      }
      if (reconnectTimer) {
        return
      }
      reconnectTimer = setTimeout(() => {
        reconnectTimer = null
        this.status = 'idle'
        void this.start()
      }, 5000)
    }
  }
})

function realtimeUrl(websocketUrl: string, deviceId: string, accessToken: string) {
  const base = websocketUrl.startsWith('ws://') || websocketUrl.startsWith('wss://')
    ? websocketUrl
    : `${wsBase()}${websocketUrl.startsWith('/') ? websocketUrl : `/${websocketUrl}`}`
  const separator = base.includes('?') ? '&' : '?'
  return `${base}${separator}deviceId=${encodeURIComponent(deviceId)}&accessToken=${encodeURIComponent(accessToken)}`
}

function wsBase() {
  if (API_BASE_URL.startsWith('https://')) {
    return `wss://${API_BASE_URL.slice('https://'.length)}`
  }
  if (API_BASE_URL.startsWith('http://')) {
    return `ws://${API_BASE_URL.slice('http://'.length)}`
  }
  if (typeof location !== 'undefined') {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${protocol}//${location.host}`
  }
  return ''
}

function clearTimers() {
  if (pingTimer) {
    clearInterval(pingTimer)
    pingTimer = null
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}
