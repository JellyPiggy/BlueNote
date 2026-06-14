<script setup lang="ts">
import { onHide, onLaunch, onShow } from '@dcloudio/uni-app'
import { computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useImStore } from '@/stores/im'
import { useNotificationStore } from '@/stores/notification'
import { useRealtimeStore } from '@/stores/realtime'

const messageTabIndex = 3

const auth = useAuthStore()
const notifications = useNotificationStore()
const im = useImStore()
const messageUnreadCount = computed(() => notifications.totalUnread + im.totalUnread)

onLaunch(() => {
  auth.restoreSession()
})

onShow(() => {
  const realtime = useRealtimeStore()
  if (auth.isAuthenticated && !auth.profileLoading && !auth.profile) {
    void auth.fetchCurrentUser()
  }
  if (auth.isAuthenticated) {
    void realtime.start()
    void notifications.refreshUnread().catch(() => {
      if (!auth.isAuthenticated) {
        notifications.clearUnread()
      }
    })
    void im.refreshUnread().catch(() => {
      if (!auth.isAuthenticated) {
        im.clearUnread()
      }
    })
  } else {
    realtime.stop()
    notifications.clearUnread()
    im.clearUnread()
  }
})

onHide(() => {
  const realtime = useRealtimeStore()
  realtime.stop()
})

watch(
  messageUnreadCount,
  (count) => {
    const text = count > 99 ? '99+' : String(count)
    try {
      if (count > 0) {
        uni.setTabBarBadge({ index: messageTabIndex, text })
      } else {
        uni.removeTabBarBadge({ index: messageTabIndex })
      }
    } catch {
      // The tab bar may not be mounted yet during early app bootstrap.
    }
  },
  { immediate: true }
)
</script>

<style lang="scss">
@use './styles.scss';
</style>
