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
  <view class="note-card panel" @tap="emit('open', note.noteId)">
    <view class="cover-wrap">
      <image v-if="note.coverUrl" class="cover" :src="note.coverUrl" mode="aspectFill" />
      <view v-else class="cover-fallback">
        <text>BN</text>
      </view>
      <view class="cover-shadow"></view>
    </view>
    <view class="note-body">
      <view class="note-title">{{ note.title }}</view>
      <view class="note-meta">
        <text class="time">{{ formatTime(note.publishedAt) }}</text>
        <view class="meta-actions">
          <text>♡ {{ formatCount(note.likeCount) }}</text>
          <text>☆ {{ formatCount(note.collectCount) }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.note-card {
  overflow: hidden;
  break-inside: avoid;
  margin-bottom: 20rpx;
  border-color: rgba(229, 236, 232, 0.72);
  box-shadow: var(--bn-shadow-soft);
}

.cover-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1.16;
  overflow: hidden;
  background: #eaf2ee;
}

.cover {
  width: 100%;
  height: 100%;
}

.cover-shadow {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 84rpx;
  background: linear-gradient(180deg, rgba(20, 27, 33, 0), rgba(20, 27, 33, 0.08));
  pointer-events: none;
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(135deg, rgba(39, 118, 223, 0.9), rgba(36, 181, 159, 0.82)),
    #dcefeb;
  color: #fff;
  font-size: 38rpx;
  font-weight: 780;
}

.note-body {
  padding: 18rpx 18rpx 20rpx;
}

.note-title {
  color: var(--bn-ink);
  font-size: 28rpx;
  font-weight: 760;
  line-height: 1.38;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.note-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-top: 16rpx;
  color: var(--bn-muted);
  font-size: 21rpx;
}

.time {
  min-width: 0;
  color: var(--bn-faint);
}

.meta-actions {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex: 0 0 auto;
  color: #56616b;
}
</style>
