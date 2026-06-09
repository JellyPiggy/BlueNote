<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getMyNotes } from '@/api/note'
import { showApiError } from '@/api/request'
import type { NoteCard } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import NoteCardView from '@/components/NoteCard.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const notes = ref<NoteCard[]>([])
const loading = ref(false)
const loaded = ref(false)
const errorText = ref('')

const leftNotes = computed(() => notes.value.filter((_, index) => index % 2 === 0))
const rightNotes = computed(() => notes.value.filter((_, index) => index % 2 === 1))

onShow(() => {
  if (auth.isAuthenticated) {
    void refresh()
  }
})

onPullDownRefresh(async () => {
  try {
    await refresh()
  } finally {
    uni.stopPullDownRefresh()
  }
})

async function refresh() {
  if (!auth.isAuthenticated) {
    loaded.value = true
    notes.value = []
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const page = await getMyNotes('PUBLISHED', null, 30)
    notes.value = page.items
    loaded.value = true
  } catch (error) {
    errorText.value = '信息流加载失败'
    showApiError(error, '信息流加载失败')
  } finally {
    loading.value = false
  }
}

function openNote(noteId: string) {
  uni.navigateTo({ url: `/pages/note/detail?noteId=${noteId}` })
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function goPublish() {
  uni.switchTab({ url: '/pages/publish/index' })
}
</script>

<template>
  <view class="screen top-safe">
    <view class="home-header">
      <view>
        <view class="eyebrow">BlueNote</view>
        <view class="headline">今天想记录什么？</view>
      </view>
      <button class="new-button" @tap="goPublish">
        <text>+</text>
      </button>
    </view>

    <view class="spotlight">
      <view class="spotlight-main">
        <view class="spotlight-badge">轻笔记</view>
        <view class="spotlight-title">把生活里闪过的小片段，收进自己的主页。</view>
        <view class="spotlight-subtitle">照片、标题、正文和话题会一起留在这里。</view>
      </view>
      <button v-if="!auth.isAuthenticated" class="spotlight-action" @tap="goLogin">登录</button>
      <button v-else class="spotlight-action" @tap="goPublish">写笔记</button>
    </view>

    <view v-if="loading && !loaded" class="loading-copy">正在整理笔记</view>

    <EmptyState
      v-else-if="!auth.isAuthenticated"
      title="登录后查看你的笔记流"
      subtitle="注册或登录后，可以上传图片、发布笔记，并在这里看到已发布内容。"
    >
      <button class="primary-button empty-action" @tap="goLogin">去登录</button>
    </EmptyState>

    <EmptyState
      v-else-if="loaded && !notes.length && !errorText"
      title="还没有公开笔记"
      subtitle="选一张图片，写下标题和正文，BlueNote 会把它发布到你的主页。"
    >
      <button class="primary-button empty-action" @tap="goPublish">开始发布</button>
    </EmptyState>

    <EmptyState v-else-if="errorText" title="暂时读不到笔记" subtitle="稍后刷新，或检查本地网关是否正在运行。">
      <button class="secondary-button empty-action" @tap="refresh">重新加载</button>
    </EmptyState>

    <view v-else class="masonry">
      <view class="column">
        <NoteCardView v-for="note in leftNotes" :key="note.noteId" :note="note" @open="openNote" />
      </view>
      <view class="column">
        <NoteCardView v-for="note in rightNotes" :key="note.noteId" :note="note" @open="openNote" />
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28rpx;
}

.eyebrow {
  color: var(--bn-teal);
  font-size: 23rpx;
  font-weight: 820;
}

.headline {
  margin-top: 8rpx;
  font-size: 44rpx;
  font-weight: 860;
  line-height: 1.18;
}

.new-button {
  width: 82rpx;
  height: 82rpx;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--bn-coral), #ff9b62);
  box-shadow: 0 18rpx 34rpx rgba(243, 107, 90, 0.22);
  font-size: 46rpx;
  font-weight: 520;
  display: flex;
  align-items: center;
  justify-content: center;
}

.spotlight {
  position: relative;
  overflow: hidden;
  margin: 30rpx 0 26rpx;
  padding: 28rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24rpx;
  border-radius: 16rpx;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(245, 251, 248, 0.88)),
    linear-gradient(120deg, rgba(244, 191, 69, 0.22), rgba(36, 181, 159, 0.1));
  border: 1rpx solid rgba(255, 255, 255, 0.82);
  box-shadow: var(--bn-shadow);
}

.spotlight-main {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  min-width: 0;
}

.spotlight-badge {
  align-self: flex-start;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(244, 191, 69, 0.22);
  color: #9b6b00;
  font-size: 21rpx;
  font-weight: 760;
}

.spotlight-title {
  max-width: 430rpx;
  color: var(--bn-ink);
  font-size: 31rpx;
  font-weight: 800;
  line-height: 1.4;
}

.spotlight-subtitle {
  color: var(--bn-muted);
  font-size: 23rpx;
  line-height: 1.5;
}

.spotlight-action {
  width: 142rpx;
  min-height: 70rpx;
  flex: 0 0 auto;
  border-radius: 16rpx;
  background: #fff;
  color: var(--bn-blue);
  flex: 0 0 auto;
  font-size: 24rpx;
  font-weight: 760;
  box-shadow: 0 10rpx 22rpx rgba(39, 118, 223, 0.1);
}

.empty-action {
  width: 260rpx;
  margin-top: 10rpx;
}

.masonry {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
}

.column {
  flex: 1;
  min-width: 0;
}
</style>
