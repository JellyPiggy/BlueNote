<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { useAuthStore } from '@/stores/auth'

onLaunch(() => {
  const auth = useAuthStore()
  auth.restoreSession()
})

onShow(() => {
  const auth = useAuthStore()
  if (auth.isAuthenticated && !auth.profileLoading && !auth.profile) {
    void auth.fetchCurrentUser()
  }
})
</script>

<style lang="scss">
@use './styles.scss';
</style>
