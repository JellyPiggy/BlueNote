<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'

onLaunch(() => {
  const auth = useAuthStore()
  auth.restoreSession()
})

onShow(() => {
  const auth = useAuthStore()
  const notifications = useNotificationStore()
  if (auth.isAuthenticated && !auth.profileLoading && !auth.profile) {
    void auth.fetchCurrentUser()
  }
  if (auth.isAuthenticated) {
    void notifications.refreshUnread().catch(() => {
      if (!auth.isAuthenticated) {
        notifications.clearUnread()
      }
    })
  } else {
    notifications.clearUnread()
  }
})
</script>

<style lang="scss">
@use './styles.scss';
</style>
