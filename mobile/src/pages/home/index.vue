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
        <view class="headline">你的生活笔记流</view>
      </view>
      <button class="new-button" @tap="goPublish">+</button>
    </view>

    <view class="spotlight panel">
      <view class="spotlight-copy">
        <text class="spotlight-title">发布、查看、回到你的个人主页</text>
        <text class="spotlight-subtitle">第一条主链路已接入真实网关、MySQL 和 MinIO。</text>
      </view>
      <button v-if="!auth.isAuthenticated" class="ghost-button compact" @tap="goLogin">登录</button>
      <button v-else class="secondary-button compact" @tap="goPublish">写笔记</button>
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
  gap: 24rpx;
}

.eyebrow {
  color: var(--bn-teal);
  font-size: 24rpx;
  font-weight: 760;
}

.headline {
  margin-top: 6rpx;
  font-size: 42rpx;
  font-weight: 820;
}

.new-button {
  width: 78rpx;
  height: 78rpx;
  border-radius: 50%;
  color: #fff;
  background: var(--bn-coral);
  font-size: 44rpx;
  font-weight: 520;
  display: flex;
  align-items: center;
  justify-content: center;
}

.spotlight {
  margin: 30rpx 0 24rpx;
  padding: 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  background:
    linear-gradient(120deg, rgba(246, 200, 76, 0.16), rgba(46, 183, 166, 0.08)),
    #fff;
}

.spotlight-copy {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.spotlight-title {
  font-size: 29rpx;
  font-weight: 760;
}

.spotlight-subtitle {
  color: var(--bn-muted);
  font-size: 23rpx;
  line-height: 1.45;
}

.compact {
  width: 150rpx;
  min-height: 66rpx;
  flex: 0 0 auto;
  font-size: 24rpx;
}

.empty-action {
  width: 260rpx;
  margin-top: 10rpx;
}

.masonry {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
}

.column {
  flex: 1;
  min-width: 0;
}
</style>

