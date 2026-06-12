<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getMyNotes } from '@/api/note'
import { showApiError } from '@/api/request'
import type { NoteCard } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import NoteCardView from '@/components/NoteCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'

const auth = useAuthStore()
const notifications = useNotificationStore()
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

function openNotifications() {
  if (!auth.isAuthenticated) {
    goLogin()
    return
  }
  uni.navigateTo({ url: '/pages/notifications/index' })
}
</script>

<template>
  <view class="screen feed-screen top-safe">
    <view class="feed-appbar">
      <view class="brand-word">BlueNote</view>
      <button class="search-pill" @tap="goPublish">
        <text class="search-mark">⌕</text>
        <text class="search-copy">搜索笔记、话题</text>
      </button>
      <button class="message-button" @tap="openNotifications">
        <text class="message-icon">◌</text>
        <text v-if="notifications.badgeText" class="message-badge">{{ notifications.badgeText }}</text>
      </button>
      <button class="new-button" @tap="goPublish">+</button>
    </view>

    <view class="channel-tabs">
      <button class="channel active">推荐</button>
      <button class="channel">关注</button>
      <button class="channel">最新</button>
      <button class="channel">我的</button>
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
.feed-screen {
  padding-left: 18rpx;
  padding-right: 18rpx;
}

.feed-appbar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-height: 78rpx;
}

.brand-word {
  flex: 0 0 auto;
  color: var(--bn-ink);
  font-size: 31rpx;
  font-weight: 900;
}

.search-pill {
  flex: 1;
  min-width: 0;
  height: 64rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #fff;
  color: var(--bn-muted);
  display: flex;
  align-items: center;
  gap: 10rpx;
  font-size: 24rpx;
  box-shadow: 0 6rpx 18rpx rgba(18, 22, 28, 0.05);
}

.search-mark {
  color: var(--bn-coral);
  font-size: 28rpx;
  font-weight: 800;
}

.search-copy {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-button {
  flex: 0 0 auto;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  color: #fff;
  background: linear-gradient(135deg, var(--bn-coral), #ff9b62);
  box-shadow: 0 12rpx 24rpx rgba(255, 95, 87, 0.22);
  font-size: 38rpx;
  font-weight: 520;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-button {
  position: relative;
  flex: 0 0 auto;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  color: var(--bn-ink);
  background: #fff;
  box-shadow: 0 6rpx 18rpx rgba(18, 22, 28, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-icon {
  font-size: 38rpx;
  line-height: 1;
}

.message-badge {
  position: absolute;
  right: -8rpx;
  top: -8rpx;
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: var(--bn-coral);
  color: #fff;
  border: 3rpx solid #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  font-weight: 820;
  line-height: 1;
}

.channel-tabs {
  display: flex;
  align-items: center;
  gap: 34rpx;
  margin: 18rpx 2rpx 22rpx;
  padding: 0 8rpx;
}

.channel {
  position: relative;
  height: 54rpx;
  color: #6f747b;
  font-size: 27rpx;
  font-weight: 720;
}

.channel.active {
  color: var(--bn-ink);
  font-size: 31rpx;
  font-weight: 900;
}

.channel.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 34rpx;
  height: 6rpx;
  border-radius: 999rpx;
  background: var(--bn-coral);
  transform: translateX(-50%);
}

.empty-action {
  width: 260rpx;
  margin-top: 10rpx;
}

.masonry {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}

.column {
  flex: 1;
  min-width: 0;
}
</style>
