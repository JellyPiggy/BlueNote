import { defineStore } from 'pinia'
import { getImUnreadCount } from '@/api/im'

interface ImState {
  totalUnread: number
  loading: boolean
  loaded: boolean
}

export const useImStore = defineStore('im', {
  state: (): ImState => ({
    totalUnread: 0,
    loading: false,
    loaded: false
  }),
  getters: {
    badgeText: (state) => {
      if (state.totalUnread <= 0) {
        return ''
      }
      return state.totalUnread > 99 ? '99+' : String(state.totalUnread)
    }
  },
  actions: {
    async refreshUnread() {
      if (this.loading) {
        return
      }
      this.loading = true
      try {
        const response = await getImUnreadCount()
        this.totalUnread = Number(response.totalUnread ?? 0)
        this.loaded = true
      } finally {
        this.loading = false
      }
    },
    applyUnread(totalUnread: number) {
      this.totalUnread = Math.max(0, Number(totalUnread || 0))
      this.loaded = true
    },
    clearUnread() {
      this.totalUnread = 0
      this.loaded = false
      this.loading = false
    }
  }
})
