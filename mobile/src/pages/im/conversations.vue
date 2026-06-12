<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { deleteImConversation, getImConversations } from '@/api/im'
import { showApiError } from '@/api/request'
import type { ImConversationItem } from '@/api/types'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { useImStore } from '@/stores/im'

const auth = useAuthStore()
const im = useImStore()
const conversations = ref<ImConversationItem[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const loading = ref(false)
const loaded = ref(false)
const errorText = ref('')
const deletingIds = ref<string[]>([])

const unreadText = computed(() => im.badgeText)

onShow(() => {
  if (!auth.isAuthenticated) {
    return
  }
  void im.refreshUnread().catch(() => undefined)
  if (!loaded.value && !loading.value) {
    void loadConversations(true)
  }
})

onPullDownRefresh(async () => {
  try {
    await refresh()
  } finally {
    uni.stopPullDownRefresh()
  }
})

onReachBottom(() => {
  if (loaded.value && hasMore.value) {
    void loadConversations(false)
  }
})

async function refresh() {
  if (!auth.isAuthenticated) {
    return
  }
  await Promise.all([loadConversations(true), im.refreshUnread().catch(() => undefined)])
}

async function loadConversations(reset = false) {
  if (!auth.isAuthenticated || loading.value) {
    return
  }
  if (!reset && loaded.value && !hasMore.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const page = await getImConversations(reset ? null : nextCursor.value, 20)
    conversations.value = reset ? page.items : mergeConversations(conversations.value, page.items)
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
    loaded.value = true
  } catch (error) {
    errorText.value = '私信加载失败'
    showApiError(error, '私信加载失败')
  } finally {
    loading.value = false
  }
}

function openConversation(item: ImConversationItem) {
  const title = item.peerUser?.nickname ? encodeURIComponent(item.peerUser.nickname) : ''
  uni.navigateTo({
    url: `/pages/im/chat?conversationId=${item.conversationId}&title=${title}`
  })
}

function confirmDelete(item: ImConversationItem) {
  if (deletingIds.value.includes(item.conversationId)) {
    return
  }
  uni.showModal({
    title: '删除会话',
    content: '确定从列表里删除这个会话吗？',
    confirmText: '删除',
    success: (result) => {
      if (result.confirm) {
        void performDelete(item)
      }
    }
  })
}

async function performDelete(item: ImConversationItem) {
  deletingIds.value = [...deletingIds.value, item.conversationId]
  try {
    await deleteImConversation(item.conversationId)
    conversations.value = conversations.value.filter((conversation) => conversation.conversationId !== item.conversationId)
    await im.refreshUnread().catch(() => undefined)
    uni.showToast({ title: '已删除', icon: 'none' })
  } catch (error) {
    showApiError(error, '删除失败')
  } finally {
    deletingIds.value = deletingIds.value.filter((id) => id !== item.conversationId)
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

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function mergeConversations(current: ImConversationItem[], incoming: ImConversationItem[]) {
  const map = new Map(current.map((item) => [item.conversationId, item]))
  for (const item of incoming) {
    map.set(item.conversationId, item)
  }
  return Array.from(map.values())
}

function messageSummary(item: ImConversationItem) {
  return item.lastMessage?.summary || '还没有消息'
}

function formatTime(value: string) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const diff = Math.max(0, Date.now() - date.getTime())
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < hour) {
    return `${Math.max(1, Math.floor(diff / minute))}分钟前`
  }
  if (diff < day) {
    return `${Math.floor(diff / hour)}小时前`
  }
  return `${date.getMonth() + 1}月${date.getDate()}日`
}
</script>

<template>
  <view class="screen im-screen top-safe">
    <view class="im-appbar">
      <button class="back-button" @tap="back">‹</button>
      <view class="appbar-title">
        <view class="title-row">
          <text>私信</text>
          <text v-if="unreadText" class="total-badge">{{ unreadText }}</text>
        </view>
        <view class="appbar-subtitle">和朋友继续聊</view>
      </view>
      <button class="refresh-button" :disabled="loading || !auth.isAuthenticated" @tap="refresh">刷新</button>
    </view>

    <EmptyState v-if="!auth.isAuthenticated" title="登录后查看私信" subtitle="收到的新私信会出现在这里。">
      <button class="primary-button empty-action" @tap="goLogin">去登录</button>
    </EmptyState>

    <view v-else-if="loading && !loaded" class="loading-copy">正在读取私信</view>

    <EmptyState v-else-if="errorText" title="私信加载失败" subtitle="稍后刷新，或检查本地服务是否正在运行。">
      <button class="secondary-button empty-action" @tap="refresh">重新加载</button>
    </EmptyState>

    <EmptyState v-else-if="loaded && !conversations.length" title="暂时没有私信" subtitle="从用户主页发起聊天后，会话会出现在这里。" />

    <view v-else class="conversation-list">
      <view
        v-for="item in conversations"
        :key="item.conversationId"
        class="conversation-item"
        :class="{ unread: item.unreadCount > 0 }"
        @tap="openConversation(item)"
      >
        <AvatarCircle :src="item.peerUser?.avatarUrl" :name="item.peerUser?.nickname || '用户'" size="large" />
        <view class="conversation-main">
          <view class="conversation-head">
            <view class="peer-name">{{ item.peerUser?.nickname || '用户' }}</view>
            <text class="conversation-time">{{ formatTime(item.updatedAt) }}</text>
          </view>
          <view class="message-summary">{{ messageSummary(item) }}</view>
        </view>
        <view class="conversation-side">
          <text v-if="item.unreadCount" class="unread-badge">{{ item.unreadCount > 99 ? '99+' : item.unreadCount }}</text>
          <button class="delete-button" :disabled="deletingIds.includes(item.conversationId)" @tap.stop="confirmDelete(item)">×</button>
        </view>
      </view>

      <button v-if="hasMore" class="load-more-button" :disabled="loading" @tap="loadConversations(false)">
        {{ loading ? '加载中' : '加载更多' }}
      </button>
      <view v-else-if="loaded && conversations.length" class="list-end">没有更多私信</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.im-screen {
  padding-left: 18rpx;
  padding-right: 18rpx;
}

.im-appbar {
  display: flex;
  align-items: center;
  gap: 18rpx;
  min-height: 88rpx;
}

.back-button {
  flex: 0 0 auto;
  width: 62rpx;
  height: 62rpx;
  border-radius: 50%;
  background: #fff;
  color: var(--bn-ink);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  box-shadow: var(--bn-shadow-soft);
}

.appbar-title {
  flex: 1;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  color: var(--bn-ink);
  font-size: 36rpx;
  font-weight: 900;
}

.total-badge,
.unread-badge {
  min-width: 34rpx;
  height: 34rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: var(--bn-coral);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  font-weight: 820;
}

.appbar-subtitle {
  margin-top: 4rpx;
  color: var(--bn-muted);
  font-size: 23rpx;
}

.refresh-button {
  flex: 0 0 auto;
  width: 104rpx;
  height: 58rpx;
  border-radius: 999rpx;
  color: var(--bn-coral);
  background: rgba(255, 95, 87, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 780;
}

.refresh-button[disabled] {
  color: var(--bn-faint);
  background: #edf0f2;
}

.empty-action {
  width: 250rpx;
  margin-top: 10rpx;
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 20rpx;
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}

.conversation-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  min-height: 124rpx;
  padding: 18rpx;
  border: 1rpx solid rgba(236, 238, 240, 0.95);
  border-radius: 8rpx;
  background: #fff;
  box-shadow: var(--bn-shadow-soft);
}

.conversation-item.unread {
  border-color: rgba(255, 95, 87, 0.22);
  background: linear-gradient(90deg, rgba(255, 95, 87, 0.055), #fff 34%);
}

.conversation-main {
  flex: 1;
  min-width: 0;
}

.conversation-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.peer-name {
  flex: 1;
  min-width: 0;
  color: var(--bn-ink);
  font-size: 29rpx;
  font-weight: 850;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-time {
  flex: 0 0 auto;
  color: var(--bn-faint);
  font-size: 22rpx;
}

.message-summary {
  margin-top: 8rpx;
  color: #6b717b;
  font-size: 24rpx;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-side {
  flex: 0 0 auto;
  width: 64rpx;
  min-height: 86rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  gap: 8rpx;
}

.delete-button {
  width: 42rpx;
  height: 42rpx;
  border-radius: 50%;
  background: #f0f2f4;
  color: #828893;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
}

.delete-button[disabled] {
  opacity: 0.45;
}

.load-more-button {
  width: 260rpx;
  height: 64rpx;
  margin: 12rpx auto 0;
  border-radius: 999rpx;
  background: #fff;
  color: #5f6670;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 760;
  box-shadow: var(--bn-shadow-soft);
}

.list-end {
  padding: 22rpx 0 12rpx;
  color: var(--bn-faint);
  text-align: center;
  font-size: 23rpx;
}
</style>
