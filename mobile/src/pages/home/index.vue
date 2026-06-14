<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { feedCardToNoteCard, getFollowingFeed } from '@/api/feed'
import { getMyNotes } from '@/api/note'
import { showApiError } from '@/api/request'
import type { NoteCard } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import NoteCardView from '@/components/NoteCard.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
type HomeChannel = 'recommend' | 'following'

const activeChannel = ref<HomeChannel>('recommend')
const notes = ref<NoteCard[]>([])
const followingNotes = ref<NoteCard[]>([])
const followingCursor = ref<string | null>(null)
const followingHasMore = ref(false)
const followingDegraded = ref(false)
const loading = ref(false)
const notesLoaded = ref(false)
const followingLoaded = ref(false)
const loadingMore = ref(false)
const errorText = ref('')

const currentNotes = computed(() => activeChannel.value === 'following' ? followingNotes.value : notes.value)
const currentLoaded = computed(() => activeChannel.value === 'following' ? followingLoaded.value : notesLoaded.value)
const leftNotes = computed(() => currentNotes.value.filter((_, index) => index % 2 === 0))
const rightNotes = computed(() => currentNotes.value.filter((_, index) => index % 2 === 1))
const emptyTitle = computed(() => activeChannel.value === 'following' ? '关注的人还没有新笔记' : '还没有公开笔记')
const emptySubtitle = computed(() =>
  activeChannel.value === 'following'
    ? '去发现喜欢的创作者，关注后这里会出现他们的公开笔记。'
    : '选一张图片，写下标题和正文，BlueNote 会把它发布到你的主页。'
)
const unauthTitle = computed(() => activeChannel.value === 'following' ? '登录后查看关注页' : '登录后查看你的笔记流')
const unauthSubtitle = computed(() =>
  activeChannel.value === 'following'
    ? '关注创作者后，可以在这里按时间看到他们的新笔记。'
    : '注册或登录后，可以上传图片、发布笔记，并在这里看到已发布内容。'
)

onShow(() => {
  if (auth.isAuthenticated) {
    void refreshActive()
  }
})

onPullDownRefresh(async () => {
  try {
    await refreshActive()
  } finally {
    uni.stopPullDownRefresh()
  }
})

onReachBottom(() => {
  if (activeChannel.value === 'following') {
    void loadMoreFollowing()
  }
})

async function refreshActive() {
  if (activeChannel.value === 'following') {
    await refreshFollowing()
  } else {
    await refreshRecommend()
  }
}

async function switchChannel(channel: HomeChannel) {
  activeChannel.value = channel
  errorText.value = ''
  if (!auth.isAuthenticated) {
    return
  }
  if (channel === 'following' && !followingLoaded.value) {
    await refreshFollowing()
  }
  if (channel === 'recommend' && !notesLoaded.value) {
    await refreshRecommend()
  }
}

async function refreshRecommend() {
  if (!auth.isAuthenticated) {
    notesLoaded.value = true
    notes.value = []
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const page = await getMyNotes('PUBLISHED', null, 30)
    notes.value = page.items
    notesLoaded.value = true
  } catch (error) {
    errorText.value = '信息流加载失败'
    showApiError(error, '信息流加载失败')
  } finally {
    loading.value = false
  }
}

async function refreshFollowing() {
  if (!auth.isAuthenticated) {
    followingLoaded.value = true
    followingNotes.value = []
    followingCursor.value = null
    followingHasMore.value = false
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const page = await getFollowingFeed(null, 20)
    followingNotes.value = page.items.map(feedCardToNoteCard)
    followingCursor.value = page.nextCursor
    followingHasMore.value = page.hasMore
    followingDegraded.value = page.degraded
    followingLoaded.value = true
  } catch (error) {
    errorText.value = '关注页加载失败'
    showApiError(error, '关注页加载失败')
  } finally {
    loading.value = false
  }
}

async function loadMoreFollowing() {
  if (loadingMore.value || loading.value || !followingHasMore.value) return
  loadingMore.value = true
  try {
    const page = await getFollowingFeed(followingCursor.value, 20)
    followingNotes.value = mergeNotes(followingNotes.value, page.items.map(feedCardToNoteCard))
    followingCursor.value = page.nextCursor
    followingHasMore.value = page.hasMore
    followingDegraded.value = page.degraded
  } catch (error) {
    showApiError(error, '加载更多失败')
  } finally {
    loadingMore.value = false
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

function openRank() {
  uni.navigateTo({ url: '/pages/rank/index' })
}

function mergeNotes(current: NoteCard[], incoming: NoteCard[]) {
  const seen = new Set(current.map((note) => note.noteId))
  const merged = [...current]
  for (const note of incoming) {
    if (seen.has(note.noteId)) {
      continue
    }
    seen.add(note.noteId)
    merged.push(note)
  }
  return merged
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
    </view>

    <view class="channel-tabs">
      <button :class="['channel', { active: activeChannel === 'recommend' }]" @tap="switchChannel('recommend')">推荐</button>
      <button :class="['channel', { active: activeChannel === 'following' }]" @tap="switchChannel('following')">关注</button>
      <button class="channel" @tap="openRank">榜单</button>
    </view>

    <view v-if="loading && !currentLoaded" class="loading-copy">正在整理笔记</view>

    <EmptyState
      v-else-if="!auth.isAuthenticated"
      :title="unauthTitle"
      :subtitle="unauthSubtitle"
    >
      <button class="primary-button empty-action" @tap="goLogin">去登录</button>
    </EmptyState>

    <EmptyState
      v-else-if="currentLoaded && !currentNotes.length && !errorText"
      :title="emptyTitle"
      :subtitle="emptySubtitle"
    >
      <button v-if="activeChannel === 'recommend'" class="primary-button empty-action" @tap="goPublish">开始发布</button>
      <button v-else class="secondary-button empty-action" @tap="openRank">看看榜单</button>
    </EmptyState>

    <EmptyState v-else-if="errorText" title="暂时读不到笔记" subtitle="稍后刷新，或检查本地网关是否正在运行。">
      <button class="secondary-button empty-action" @tap="refreshActive">重新加载</button>
    </EmptyState>

    <view v-else>
      <view v-if="activeChannel === 'following' && followingDegraded" class="degraded-pill">部分数据稍有延迟</view>
      <view class="masonry">
        <view class="column">
          <NoteCardView v-for="note in leftNotes" :key="note.noteId" :note="note" @open="openNote" />
        </view>
        <view class="column">
          <NoteCardView v-for="note in rightNotes" :key="note.noteId" :note="note" @open="openNote" />
        </view>
      </view>
      <button v-if="activeChannel === 'following' && followingHasMore" class="load-more-button" :disabled="loadingMore" @tap="loadMoreFollowing">
        {{ loadingMore ? '加载中' : '加载更多' }}
      </button>
      <view v-else-if="activeChannel === 'following' && followingLoaded" class="list-end">没有更多了</view>
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

.degraded-pill {
  margin: 0 4rpx 18rpx;
  min-height: 54rpx;
  padding: 0 18rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  color: #6f590d;
  background: #fff5cb;
  font-size: 23rpx;
}

.load-more-button {
  width: 100%;
  min-height: 76rpx;
  margin: 10rpx 0 34rpx;
  border-radius: 16rpx;
  color: var(--bn-muted);
  background: #fff;
  box-shadow: var(--bn-shadow-soft);
  font-size: 25rpx;
}

.list-end {
  padding: 24rpx 0 34rpx;
  color: var(--bn-faint);
  text-align: center;
  font-size: 23rpx;
}
</style>
