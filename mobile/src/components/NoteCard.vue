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
        <text>BlueNote</text>
      </view>
    </view>
    <view class="note-body">
      <view class="note-title">{{ note.title }}</view>
      <view class="note-meta">
        <text>{{ formatTime(note.publishedAt) }}</text>
        <text>{{ formatCount(note.likeCount) }} 喜欢</text>
        <text>{{ formatCount(note.collectCount) }} 收藏</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.note-card {
  overflow: hidden;
  break-inside: avoid;
  margin-bottom: 18rpx;
}

.cover-wrap {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1.18;
  background: #e8f2f0;
}

.cover {
  width: 100%;
  height: 100%;
}

.cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(135deg, rgba(47, 128, 237, 0.92), rgba(46, 183, 166, 0.82)),
    #dcefeb;
  color: #fff;
  font-size: 30rpx;
  font-weight: 780;
}

.note-body {
  padding: 18rpx;
}

.note-title {
  color: var(--bn-ink);
  font-size: 27rpx;
  font-weight: 680;
  line-height: 1.35;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.note-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx 14rpx;
  margin-top: 14rpx;
  color: var(--bn-muted);
  font-size: 21rpx;
}
</style>

