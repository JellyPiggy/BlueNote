<script setup lang="ts">
import type { NoteCard } from '@/api/types'
import { formatCount, formatTime } from '@/utils/format'

defineProps<{
  note: NoteCard
}>()

const emit = defineEmits<{
  open: [noteId: string]
}>()
</script>

<template>
  <view class="note-card" @tap="emit('open', note.noteId)">
    <view class="cover-wrap">
      <image v-if="note.coverUrl" class="cover" :src="note.coverUrl" mode="aspectFill" />
      <view v-else class="cover-fallback">
        <text>BN</text>
      </view>
      <view class="date-badge">{{ formatTime(note.publishedAt) }}</view>
    </view>
    <view class="note-body">
      <view class="note-title">{{ note.title }}</view>
      <view class="author-row">
        <view class="mini-avatar">BN</view>
        <text class="author-name">BlueNote</text>
        <text class="like-count">♡ {{ formatCount(note.likeCount) }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.note-card {
  overflow: hidden;
  break-inside: avoid;
  margin-bottom: 20rpx;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: var(--bn-shadow-soft);
}

.cover-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1.26;
  overflow: hidden;
  background: #f0f1f2;
}

.cover {
  width: 100%;
  height: 100%;
}

.date-badge {
  position: absolute;
  right: 12rpx;
  bottom: 12rpx;
  min-height: 40rpx;
  padding: 0 14rpx;
  border-radius: 999rpx;
  background: rgba(17, 19, 24, 0.46);
  color: #fff;
  display: flex;
  align-items: center;
  font-size: 20rpx;
  backdrop-filter: blur(10rpx);
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(135deg, rgba(255, 95, 87, 0.9), rgba(44, 116, 214, 0.82)),
    #f3f4f5;
  color: #fff;
  font-size: 38rpx;
  font-weight: 780;
}

.note-body {
  padding: 16rpx 16rpx 18rpx;
}

.note-title {
  color: var(--bn-ink);
  font-size: 28rpx;
  font-weight: 720;
  line-height: 1.38;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.author-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 18rpx;
  color: var(--bn-muted);
  font-size: 21rpx;
}

.mini-avatar {
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--bn-coral), var(--bn-blue));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14rpx;
  font-weight: 800;
}

.author-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.like-count {
  flex: 0 0 auto;
  color: #454951;
}
</style>
