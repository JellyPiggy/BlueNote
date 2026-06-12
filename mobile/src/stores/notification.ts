import { defineStore } from 'pinia'
import { getNotificationUnreadCount } from '@/api/notification'
import type { NotificationCategory, NotificationUnreadCountResponse } from '@/api/types'

function emptyCategories(): Record<NotificationCategory, number> {
  return {
    INTERACTION: 0,
    FOLLOW: 0,
    SYSTEM: 0,
    ORDER: 0
  }
}

interface NotificationState {
  totalUnread: number
  categories: Record<NotificationCategory, number>
  loading: boolean
  loaded: boolean
}

export const useNotificationStore = defineStore('notification', {
  state: (): NotificationState => ({
    totalUnread: 0,
    categories: emptyCategories(),
    loading: false,
    loaded: false
  }),
  getters: {
    badgeText: (state) => {
      if (state.totalUnread <= 0) {
        return ''
      }
      return state.totalUnread > 99 ? '99+' : String(state.totalUnread)
    },
    categoryUnread: (state) => (category: NotificationCategory) => {
      return state.categories[category] ?? 0
    }
  },
  actions: {
    async refreshUnread() {
      if (this.loading) {
        return
      }
      this.loading = true
      try {
        this.applyUnread(await getNotificationUnreadCount())
      } finally {
        this.loading = false
      }
    },
    applyUnread(payload: NotificationUnreadCountResponse) {
      this.totalUnread = Number(payload.totalUnread ?? 0)
      this.categories = {
        ...emptyCategories(),
        ...(payload.categories ?? {})
      }
      this.loaded = true
    },
    clearUnread() {
      this.totalUnread = 0
      this.categories = emptyCategories()
      this.loaded = false
      this.loading = false
    }
  }
})
