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
const currentMediaIndex = ref(0)

const coverMedia = computed(() => note.value?.mediaFiles ?? [])
const mediaTotal = computed(() => coverMedia.value.length)
const searchKeyword = computed(() => {
  const firstTopic = note.value?.topics?.[0]
  return firstTopic ? `${firstTopic}相关笔记` : note.value?.title || '相似笔记'
})

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
    currentMediaIndex.value = 0
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

function handleSwiperChange(event: Event) {
  const detail = (event as unknown as { detail?: { current?: number } }).detail
  currentMediaIndex.value = Number(detail?.current ?? 0)
}

function followAuthor() {
  uni.showToast({ title: '关注功能后续接入', icon: 'none' })
}

function shareNote() {
  uni.showToast({ title: '分享功能后续接入', icon: 'none' })
}
</script>

<template>
  <view class="detail-screen">
    <view v-if="loading" class="loading-copy">正在打开笔记</view>

    <EmptyState v-else-if="errorText" title="笔记暂时不可见" subtitle="可能已删除、私密，或本地服务没有启动。">
      <button class="secondary-button retry-button" @tap="loadDetail">重新加载</button>
    </EmptyState>

    <view v-else-if="note" class="detail-content">
      <view class="detail-topbar">
        <button class="back-button" @tap="back">‹</button>
        <view class="top-author" @tap="openAuthor">
          <AvatarCircle :src="note.author.avatarUrl" :name="note.author.nickname" size="small" />
          <view class="top-name">{{ note.author.nickname }}</view>
        </view>
        <button class="follow-button" @tap="followAuthor">关注</button>
        <button class="share-button" @tap="shareNote">↗</button>
      </view>

      <view class="gallery-section">
        <view class="gallery-wrap">
          <swiper v-if="coverMedia.length" class="media-swiper" circular @change="handleSwiperChange">
            <swiper-item v-for="media in coverMedia" :key="media.fileId">
              <image class="detail-image" :src="media.accessUrl" mode="aspectFill" />
            </swiper-item>
          </swiper>
          <view v-else class="media-fallback">
            <text>BlueNote</text>
          </view>
          <view v-if="mediaTotal > 1" class="media-count">{{ currentMediaIndex + 1 }}/{{ mediaTotal }}</view>
        </view>
        <view v-if="mediaTotal > 1" class="dot-row">
          <view
            v-for="(_, index) in coverMedia"
            :key="index"
            class="dot"
            :class="{ active: currentMediaIndex === index }"
          />
        </view>
      </view>

      <view class="article-section">
        <view class="title">{{ note.title }}</view>
        <view class="content">{{ note.content }}</view>

        <view v-if="note.topics.length" class="topic-list">
          <view v-for="topic in note.topics" :key="topic" class="topic-chip">#{{ topic }}</view>
        </view>

        <view class="search-suggestion">
          <text class="search-icon">⌕</text>
          <text>猜你想搜｜{{ searchKeyword }}</text>
        </view>

        <view class="note-meta-row">
          <text>{{ formatTime(note.publishedAt) }} · {{ note.visibility === 'PUBLIC' ? '公开' : '私密' }}</text>
          <button class="dislike-button">不喜欢</button>
        </view>

        <view v-if="note.degraded" class="degraded-chip">部分信息降级展示</view>
      </view>

      <view class="detail-action-bar">
        <view class="comment-entry">
          <text class="comment-pencil">✎</text>
          <text>说点什么...</text>
        </view>
        <view class="action-group">
          <button class="action-item">
            <text class="action-icon">♡</text>
            <text>{{ formatCount(note.counts.likeCount) }}</text>
          </button>
          <button class="action-item">
            <text class="action-icon">☆</text>
            <text>{{ formatCount(note.counts.collectCount) }}</text>
          </button>
          <button class="action-item">
            <text class="action-icon">◌</text>
            <text>{{ formatCount(note.counts.commentCount) }}</text>
          </button>
          </view>
        </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.detail-screen {
  min-height: 100vh;
  background: #fff;
}

.detail-content {
  min-height: 100vh;
  padding-bottom: 150rpx;
  background: #fff;
}

.detail-topbar {
  position: sticky;
  top: 0;
  z-index: 9;
  min-height: 104rpx;
  padding: env(safe-area-inset-top) 24rpx 14rpx;
  background: rgba(255, 255, 255, 0.98);
  border-bottom: 1rpx solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.back-button,
.share-button {
  flex: 0 0 auto;
  width: 62rpx;
  height: 62rpx;
  border-radius: 50%;
  color: #1d2026;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
}

.share-button {
  font-size: 38rpx;
}

.top-author {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.top-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1d2026;
  font-size: 30rpx;
  font-weight: 760;
}

.follow-button {
  flex: 0 0 auto;
  width: 124rpx;
  height: 60rpx;
  border-radius: 999rpx;
  border: 1rpx solid rgba(255, 95, 87, 0.52);
  color: var(--bn-coral);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 760;
  line-height: 1;
  white-space: nowrap;
}

.gallery-section {
  background: #fff;
  padding: 22rpx 24rpx 0;
}

.gallery-wrap {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 8rpx;
  background: #fff;
}

.media-swiper,
.media-fallback {
  width: 100%;
  height: 660rpx;
  background: #fff;
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
  background: linear-gradient(135deg, var(--bn-coral), var(--bn-blue), var(--bn-teal));
}

.media-count {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  min-width: 72rpx;
  height: 58rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: rgba(17, 19, 24, 0.48);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 760;
  backdrop-filter: blur(10rpx);
}

.dot-row {
  height: 58rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
}

.dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #d7d7d7;
}

.dot.active {
  background: var(--bn-coral);
}

.article-section {
  padding: 30rpx 32rpx 36rpx;
  background: #fff;
}

.degraded-chip {
  display: inline-flex;
  margin-top: 22rpx;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(246, 200, 76, 0.18);
  color: #9a6b00;
  font-size: 22rpx;
}

.title {
  margin-top: 0;
  color: var(--bn-ink);
  font-size: 34rpx;
  font-weight: 780;
  line-height: 1.42;
}

.content {
  margin-top: 18rpx;
  color: #2b2f36;
  font-size: 31rpx;
  line-height: 1.72;
  white-space: pre-wrap;
}

.topic-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 24rpx;
}

.topic-chip {
  min-height: 42rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 95, 87, 0.08);
  color: #d94e47;
  display: flex;
  align-items: center;
  font-size: 23rpx;
}

.search-suggestion {
  max-width: 100%;
  min-height: 66rpx;
  margin-top: 28rpx;
  padding: 0 22rpx;
  border: 1rpx solid #ececec;
  border-radius: 10rpx;
  background: #fff;
  color: #3a3f46;
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  font-size: 27rpx;
  box-shadow: 0 4rpx 14rpx rgba(18, 22, 28, 0.04);
}

.search-icon {
  color: #1d2026;
  font-size: 36rpx;
  font-weight: 800;
}

.note-meta-row {
  margin-top: 28rpx;
  color: var(--bn-muted);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  font-size: 25rpx;
}

.dislike-button {
  flex: 0 0 auto;
  height: 52rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  border: 1rpx solid #e8e8e8;
  color: #4c5158;
  background: #fff;
  font-size: 24rpx;
}

.detail-action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  min-height: 112rpx;
  padding: 14rpx 24rpx calc(14rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1rpx solid var(--bn-line);
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.comment-entry {
  flex: 0 1 320rpx;
  min-width: 0;
  height: 70rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #f3f4f5;
  color: var(--bn-muted);
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 24rpx;
}

.comment-pencil {
  font-size: 30rpx;
}

.action-group {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10rpx;
}

.action-item {
  flex: 0 0 auto;
  height: 70rpx;
  min-width: 72rpx;
  border-radius: 999rpx;
  color: var(--bn-ink);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 25rpx;
  font-weight: 760;
}

.action-icon {
  font-size: 44rpx;
  line-height: 1;
}

.retry-button {
  width: 240rpx;
  margin-top: 12rpx;
}
</style>
