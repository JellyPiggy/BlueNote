<script setup lang="ts">
import { onHide, onLaunch, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useRealtimeStore } from '@/stores/realtime'

onLaunch(() => {
  const auth = useAuthStore()
  auth.restoreSession()
})

onShow(() => {
  const auth = useAuthStore()
  const notifications = useNotificationStore()
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
  } else {
    realtime.stop()
    notifications.clearUnread()
  }
})

onHide(() => {
  const realtime = useRealtimeStore()
  realtime.stop()
})
</script>

<style lang="scss">
@use './styles.scss';
</style>
