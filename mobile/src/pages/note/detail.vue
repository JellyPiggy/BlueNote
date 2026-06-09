<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getNoteDetail } from '@/api/note'
import { showApiError } from '@/api/request'
import type { NoteDetail } from '@/api/types'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatCount, formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const noteId = ref('')
const note = ref<NoteDetail | null>(null)
const loading = ref(false)
const errorText = ref('')

const coverMedia = computed(() => note.value?.mediaFiles ?? [])

onLoad((options) => {
  noteId.value = String(options?.noteId ?? '')
  void loadDetail()
})

async function loadDetail() {
  if (!noteId.value) {
    errorText.value = '笔记不存在'
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    note.value = await getNoteDetail(noteId.value, auth.isAuthenticated)
  } catch (error) {
    errorText.value = '笔记暂时不可见'
    showApiError(error, '笔记暂时不可见')
  } finally {
    loading.value = false
  }
}

function back() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.switchTab({ url: '/pages/home/index' })
  }
}

function openAuthor() {
  if (!note.value) {
    return
  }
  uni.switchTab({ url: '/pages/profile/index' })
}
</script>

<template>
  <view class="detail-screen">
    <view class="floating-nav">
      <button class="round-button" @tap="back">‹</button>
      <button class="round-button" @tap="loadDetail">↻</button>
    </view>

    <view v-if="loading" class="loading-copy">正在打开笔记</view>

    <EmptyState v-else-if="errorText" title="笔记暂时不可见" subtitle="可能已删除、私密，或本地服务没有启动。">
      <button class="secondary-button retry-button" @tap="loadDetail">重新加载</button>
    </EmptyState>

    <view v-else-if="note" class="detail-content">
      <swiper v-if="coverMedia.length" class="media-swiper" circular indicator-dots indicator-color="rgba(255,255,255,.55)" indicator-active-color="#ffffff">
        <swiper-item v-for="media in coverMedia" :key="media.fileId">
          <image class="detail-image" :src="media.accessUrl" mode="aspectFill" />
        </swiper-item>
      </swiper>
      <view v-else class="media-fallback">
        <text>BlueNote</text>
      </view>

      <view class="detail-card">
        <view class="card-handle"></view>
        <view class="author-row" @tap="openAuthor">
          <AvatarCircle :src="note.author.avatarUrl" :name="note.author.nickname" size="medium" />
          <view class="author-copy">
            <view class="author-name">{{ note.author.nickname }}</view>
            <view class="author-meta">{{ formatTime(note.publishedAt) }} · {{ note.visibility === 'PUBLIC' ? '公开' : '私密' }}</view>
          </view>
          <view v-if="note.degraded" class="degraded-chip">部分降级</view>
        </view>

        <view class="title">{{ note.title }}</view>
        <view class="content">{{ note.content }}</view>

        <view v-if="note.topics.length" class="topic-list">
          <view v-for="topic in note.topics" :key="topic" class="chip">#{{ topic }}</view>
        </view>

        <view class="stats-row">
          <view class="stat-item">
            <text class="stat-value">{{ formatCount(note.counts.likeCount) }}</text>
            <text class="stat-label">喜欢</text>
          </view>
          <view class="stat-item">
            <text class="stat-value">{{ formatCount(note.counts.collectCount) }}</text>
            <text class="stat-label">收藏</text>
          </view>
          <view class="stat-item">
            <text class="stat-value">{{ formatCount(note.counts.commentCount) }}</text>
            <text class="stat-label">评论</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.detail-screen {
  min-height: 100vh;
  background: #111820;
}

.floating-nav {
  position: fixed;
  top: 34rpx;
  left: 24rpx;
  right: 24rpx;
  z-index: 8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  pointer-events: none;
}

.round-button {
  width: 78rpx;
  height: 78rpx;
  border-radius: 50%;
  background: rgba(17, 24, 32, 0.58);
  backdrop-filter: blur(12rpx);
  color: #fff;
  font-size: 42rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: auto;
}

.detail-content {
  min-height: 100vh;
  background:
    linear-gradient(180deg, #111820 0, #111820 520rpx, var(--bn-bg) 521rpx),
    var(--bn-bg);
}

.media-swiper,
.media-fallback {
  width: 100%;
  height: 720rpx;
  background: #dfe8e5;
}

.detail-image {
  width: 100%;
  height: 100%;
}

.media-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 50rpx;
  font-weight: 800;
  background: linear-gradient(135deg, var(--bn-blue), var(--bn-teal), var(--bn-lemon));
}

.detail-card {
  position: relative;
  margin-top: -34rpx;
  padding: 18rpx 28rpx 34rpx;
  border-radius: 16rpx 16rpx 0 0;
  background: var(--bn-bg);
  box-shadow: 0 -12rpx 34rpx rgba(17, 24, 32, 0.08);
}

.card-handle {
  width: 72rpx;
  height: 8rpx;
  margin: 0 auto 22rpx;
  border-radius: 999rpx;
  background: rgba(154, 164, 172, 0.36);
}

.author-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.author-copy {
  flex: 1;
  min-width: 0;
}

.author-name {
  font-size: 28rpx;
  font-weight: 800;
}

.author-meta {
  margin-top: 6rpx;
  color: var(--bn-muted);
  font-size: 22rpx;
}

.degraded-chip {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(246, 200, 76, 0.18);
  color: #9a6b00;
  font-size: 21rpx;
}

.title {
  margin-top: 34rpx;
  color: var(--bn-ink);
  font-size: 42rpx;
  font-weight: 860;
  line-height: 1.28;
}

.content {
  margin-top: 22rpx;
  color: #27323a;
  font-size: 30rpx;
  line-height: 1.78;
  white-space: pre-wrap;
}

.topic-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 26rpx;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14rpx;
  margin-top: 34rpx;
}

.stat-item {
  min-height: 100rpx;
  border-radius: 16rpx;
  background: #fff;
  border: 1rpx solid rgba(229, 236, 232, 0.74);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
}

.stat-value {
  font-size: 30rpx;
  font-weight: 780;
}

.stat-label {
  color: var(--bn-muted);
  font-size: 21rpx;
}

.retry-button {
  width: 240rpx;
  margin-top: 12rpx;
}
</style>
