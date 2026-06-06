<script setup lang="ts">
import { computed } from 'vue'
import { fallbackAvatar } from '@/utils/format'

const props = defineProps<{
  src?: string | null
  name?: string | null
  size?: 'small' | 'medium' | 'large'
}>()

const className = computed(() => `avatar avatar-${props.size ?? 'medium'}`)
</script>

<template>
  <view :class="className">
    <image v-if="src" class="avatar-image" :src="src" mode="aspectFill" />
    <text v-else class="avatar-text">{{ fallbackAvatar(name) }}</text>
  </view>
</template>

<style scoped lang="scss">
.avatar {
  flex: 0 0 auto;
  overflow: hidden;
  border-radius: 50%;
  background: linear-gradient(135deg, #2f80ed, #2eb7a6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 760;
}

.avatar-small {
  width: 52rpx;
  height: 52rpx;
  font-size: 20rpx;
}

.avatar-medium {
  width: 76rpx;
  height: 76rpx;
  font-size: 26rpx;
}

.avatar-large {
  width: 132rpx;
  height: 132rpx;
  font-size: 40rpx;
}

.avatar-image {
  width: 100%;
  height: 100%;
}

.avatar-text {
  color: #fff;
}
</style>

