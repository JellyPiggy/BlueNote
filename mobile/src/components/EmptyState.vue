<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <view class="empty-state panel">
    <view class="empty-mark">BN</view>
    <view class="empty-title">{{ title }}</view>
    <view v-if="subtitle" class="empty-subtitle">{{ subtitle }}</view>
    <slot />
  </view>
</template>

<style scoped lang="scss">
.empty-title {
  color: var(--bn-ink);
  font-size: 31rpx;
  font-weight: 800;
}

.empty-subtitle {
  max-width: 520rpx;
  color: var(--bn-muted);
  font-size: 24rpx;
  line-height: 1.5;
}
</style>
