<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import {
  deleteNotification,
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead
} from '@/api/notification'
import { showApiError } from '@/api/request'
import type { NotificationCategory, NotificationItem } from '@/api/types'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { formatCount } from '@/utils/format'

interface NotificationTab {
  label: string
  category: NotificationCategory
}

interface NotificationListState {
  items: NotificationItem[]
  nextCursor: string | null
  hasMore: boolean
  loading: boolean
  loaded: boolean
  errorText: string
}

const tabs: NotificationTab[] = [
  { label: '互动', category: 'INTERACTION' },
  { label: '关注', category: 'FOLLOW' },
  { label: '系统', category: 'SYSTEM' }
]

const auth = useAuthStore()
const notifications = useNotificationStore()
const activeCategory = ref<NotificationCategory>('INTERACTION')
const readingIds = ref<string[]>([])
const deletingIds = ref<string[]>([])
const markingAll = ref(false)
const states = ref<Record<NotificationCategory, NotificationListState>>({
  INTERACTION: createListState(),
  FOLLOW: createListState(),
  SYSTEM: createListState(),
  ORDER: createListState()
})

const currentState = computed(() => states.value[activeCategory.value])
const unreadTotalText = computed(() => (notifications.totalUnread > 99 ? '99+' : String(notifications.totalUnread)))
const activeUnreadCount = computed(() => notifications.categoryUnread(activeCategory.value))
const activeUnreadText = computed(() => (activeUnreadCount.value > 99 ? '99+' : String(activeUnreadCount.value)))
const hasUnreadInCurrentTab = computed(() => activeUnreadCount.value > 0)

onLoad((options) => {
  const category = String(options?.category ?? '').toUpperCase()
  if (isNotificationCategory(category) && category !== 'ORDER') {
    activeCategory.value = category
  }
})

onShow(() => {
  if (!auth.isAuthenticated) {
    return
  }
  void notifications.refreshUnread().catch(() => undefined)
  if (!currentState.value.loaded && !currentState.value.loading) {
    void loadCurrent(true)
  }
})

onPullDownRefresh(async () => {
  try {
    await refreshCurrent()
  } finally {
    uni.stopPullDownRefresh()
  }
})

onReachBottom(() => {
  if (currentState.value.loaded && currentState.value.hasMore) {
    void loadCurrent(false)
  }
})

function createListState(): NotificationListState {
  return {
    items: [],
    nextCursor: null,
    hasMore: false,
    loading: false,
    loaded: false,
    errorText: ''
  }
}

async function switchTab(category: NotificationCategory) {
  activeCategory.value = category
  const state = states.value[category]
  if (auth.isAuthenticated && !state.loaded && !state.loading) {
    await loadCurrent(true)
  }
}

async function refreshCurrent() {
  if (!auth.isAuthenticated) {
    return
  }
  await Promise.all([loadCurrent(true), notifications.refreshUnread().catch(() => undefined)])
}

async function loadCurrent(reset = false) {
  if (!auth.isAuthenticated) {
    return
  }
  const state = currentState.value
  if (state.loading) {
    return
  }
  if (!reset && state.loaded && !state.hasMore) {
    return
  }
  state.loading = true
  state.errorText = ''
  try {
    const page = await getNotifications(activeCategory.value, reset ? null : state.nextCursor, 20)
    state.items = reset ? page.items : mergeNotifications(state.items, page.items)
    state.nextCursor = page.nextCursor
    state.hasMore = page.hasMore
    state.loaded = true
  } catch (error) {
    state.errorText = '通知加载失败'
    showApiError(error, '通知加载失败')
  } finally {
    state.loading = false
  }
}

async function openNotification(item: NotificationItem) {
  if (readingIds.value.includes(item.notificationId)) {
    return
  }
  readingIds.value = [...readingIds.value, item.notificationId]
  try {
    if (!item.read) {
      const response = await markNotificationRead(item.notificationId)
      patchNotification(item.notificationId, (notification) => {
        notification.read = response.read
      })
      await notifications.refreshUnread().catch(() => undefined)
    }
    jumpByNotification(item)
  } catch (error) {
    showApiError(error, '通知处理失败')
  } finally {
    readingIds.value = readingIds.value.filter((id) => id !== item.notificationId)
  }
}

async function markCurrentRead() {
  if (markingAll.value || !hasUnreadInCurrentTab.value) {
    return
  }
  markingAll.value = true
  try {
    const response = await markAllNotificationsRead(activeCategory.value)
    notifications.applyUnread({
      totalUnread: response.totalUnread,
      categories: response.categories
    })
    states.value[activeCategory.value].items = states.value[activeCategory.value].items.map((item) => ({
      ...item,
      read: true
    }))
    uni.showToast({ title: response.updatedCount ? '已全部已读' : '没有新的未读', icon: 'none' })
  } catch (error) {
    showApiError(error, '操作失败')
  } finally {
    markingAll.value = false
  }
}

function confirmDelete(item: NotificationItem) {
  if (deletingIds.value.includes(item.notificationId)) {
    return
  }
  uni.showModal({
    title: '删除通知',
    content: '确定删除这条通知吗？',
    confirmText: '删除',
    success: (result) => {
      if (result.confirm) {
        void performDelete(item)
      }
    }
  })
}

async function performDelete(item: NotificationItem) {
  deletingIds.value = [...deletingIds.value, item.notificationId]
  try {
    await deleteNotification(item.notificationId)
    for (const state of Object.values(states.value)) {
      state.items = state.items.filter((notification) => notification.notificationId !== item.notificationId)
    }
    if (!item.read) {
      await notifications.refreshUnread().catch(() => undefined)
    }
    uni.showToast({ title: '已删除', icon: 'none' })
  } catch (error) {
    showApiError(error, '删除失败')
  } finally {
    deletingIds.value = deletingIds.value.filter((id) => id !== item.notificationId)
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

function jumpByNotification(item: NotificationItem) {
  const page = String(item.jump?.page ?? '')
  if (page === 'NOTE_DETAIL' && item.jump?.noteId) {
    const query = item.jump.commentId
      ? `?noteId=${item.jump.noteId}&commentId=${item.jump.commentId}`
      : `?noteId=${item.jump.noteId}`
    uni.navigateTo({ url: `/pages/note/detail${query}` })
    return
  }
  if (page === 'USER_PROFILE') {
    uni.showToast({ title: '用户主页后续接入', icon: 'none' })
    return
  }
  if (page === 'SYSTEM_NOTICE') {
    uni.showToast({ title: item.content || item.title, icon: 'none' })
    return
  }
  uni.showToast({ title: '暂时无法打开目标内容', icon: 'none' })
}

function patchNotification(notificationId: string, updater: (notification: NotificationItem) => void) {
  for (const state of Object.values(states.value)) {
    for (const item of state.items) {
      if (item.notificationId === notificationId) {
        updater(item)
      }
    }
  }
}

function mergeNotifications(current: NotificationItem[], incoming: NotificationItem[]) {
  const exists = new Set(current.map((item) => item.notificationId))
  return [...current, ...incoming.filter((item) => !exists.has(item.notificationId))]
}

function isNotificationCategory(value: string): value is NotificationCategory {
  return value === 'INTERACTION' || value === 'FOLLOW' || value === 'SYSTEM' || value === 'ORDER'
}

function notificationIcon(type: string) {
  if (type.includes('LIKED')) {
    return '♥'
  }
  if (type.includes('COLLECTED')) {
    return '★'
  }
  if (type.includes('COMMENT') || type.includes('REPLIED')) {
    return '◌'
  }
  if (type.includes('FOLLOW')) {
    return '+'
  }
  return 'BN'
}

function targetTitle(item: NotificationItem) {
  return item.target?.title || item.content
}

function formatNotificationTime(value: string) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  const now = Date.now()
  const diff = Math.max(0, now - date.getTime())
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < hour) {
    return `${Math.max(1, Math.floor(diff / minute))}分钟前`
  }
  if (diff < day) {
    return `${Math.floor(diff / hour)}小时前`
  }
  if (diff < 7 * day) {
    return `${Math.floor(diff / day)}天前`
  }
  return `${date.getMonth() + 1}月${date.getDate()}日`
}
</script>

<template>
  <view class="screen notification-screen top-safe">
    <view class="notification-appbar">
      <button class="back-button" @tap="back">‹</button>
      <view class="appbar-title">
        <view class="title-row">
          <text>消息</text>
          <text v-if="notifications.totalUnread" class="total-badge">{{ unreadTotalText }}</text>
        </view>
        <view class="appbar-subtitle">最近通知</view>
      </view>
      <button
        class="read-all-button"
        :disabled="!auth.isAuthenticated || !hasUnreadInCurrentTab || markingAll"
        @tap="markCurrentRead"
      >
        已读
      </button>
    </view>

    <view class="notification-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.category"
        class="notification-tab"
        :class="{ active: activeCategory === tab.category }"
        @tap="switchTab(tab.category)"
      >
        <text>{{ tab.label }}</text>
        <text v-if="notifications.categoryUnread(tab.category)" class="tab-badge">
          {{ formatCount(notifications.categoryUnread(tab.category)) }}
        </text>
      </button>
    </view>

    <EmptyState
      v-if="!auth.isAuthenticated"
      title="登录后查看消息"
      subtitle="点赞、评论、关注和系统通知会出现在这里。"
    >
      <button class="primary-button empty-action" @tap="goLogin">去登录</button>
    </EmptyState>

    <view v-else-if="currentState.loading && !currentState.loaded" class="loading-copy">正在读取消息</view>

    <EmptyState v-else-if="currentState.errorText" title="消息加载失败" subtitle="稍后刷新，或检查本地网关是否正在运行。">
      <button class="secondary-button empty-action" @tap="refreshCurrent">重新加载</button>
    </EmptyState>

    <EmptyState v-else-if="currentState.loaded && !currentState.items.length" title="暂时没有消息" subtitle="新的互动会在这里出现。" />

    <view v-else class="notification-list">
      <view
        v-for="item in currentState.items"
        :key="item.notificationId"
        class="notification-item"
        :class="{ unread: !item.read }"
        @tap="openNotification(item)"
      >
        <view class="actor-stack">
          <AvatarCircle
            v-if="item.actors?.[0]"
            :src="item.actors[0].avatarUrl"
            :name="item.actors[0].nickname"
            size="medium"
          />
          <view v-else class="system-avatar">{{ notificationIcon(item.notificationType) }}</view>
          <view class="type-mark">{{ notificationIcon(item.notificationType) }}</view>
        </view>

        <view class="notification-main">
          <view class="notification-head">
            <view class="notification-title">{{ item.title }}</view>
            <text class="notification-time">{{ formatNotificationTime(item.lastEventAt) }}</text>
          </view>
          <view class="notification-copy">{{ item.content }}</view>
          <view class="notification-meta">
            <text v-if="item.aggregate && item.actorCount > 1">{{ formatCount(item.actorCount) }} 人参与</text>
            <text v-if="targetTitle(item)" class="target-copy">{{ targetTitle(item) }}</text>
          </view>
        </view>

        <image v-if="item.target?.coverUrl" class="target-cover" :src="item.target.coverUrl" mode="aspectFill" />
        <button class="delete-button" :disabled="deletingIds.includes(item.notificationId)" @tap.stop="confirmDelete(item)">×</button>
        <view v-if="!item.read" class="unread-dot"></view>
      </view>

      <button v-if="currentState.hasMore" class="load-more-button" :disabled="currentState.loading" @tap="loadCurrent(false)">
        {{ currentState.loading ? '加载中' : '加载更多' }}
      </button>

      <view v-else-if="currentState.loaded && currentState.items.length" class="list-end">没有更多消息</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.notification-screen {
  padding-left: 18rpx;
  padding-right: 18rpx;
}

.notification-appbar {
  display: flex;
  align-items: center;
  gap: 18rpx;
  min-height: 86rpx;
}

.back-button {
  flex: 0 0 auto;
  width: 62rpx;
  height: 62rpx;
  border-radius: 50%;
  color: var(--bn-ink);
  background: #fff;
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
  line-height: 1.2;
}

.total-badge,
.tab-badge {
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
  line-height: 1;
}

.appbar-subtitle {
  margin-top: 4rpx;
  color: var(--bn-muted);
  font-size: 23rpx;
}

.read-all-button {
  flex: 0 0 auto;
  width: 96rpx;
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

.read-all-button[disabled] {
  color: var(--bn-faint);
  background: #edf0f2;
}

.notification-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14rpx;
  margin: 22rpx 0;
}

.notification-tab {
  position: relative;
  min-height: 70rpx;
  border-radius: 8rpx;
  background: #fff;
  color: #676d76;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  font-size: 27rpx;
  font-weight: 760;
  box-shadow: var(--bn-shadow-soft);
}

.notification-tab.active {
  color: var(--bn-ink);
  background: #fff7f6;
}

.notification-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 8rpx;
  width: 34rpx;
  height: 5rpx;
  border-radius: 999rpx;
  background: var(--bn-coral);
  transform: translateX(-50%);
}

.tab-badge {
  height: 30rpx;
  min-width: 30rpx;
  font-size: 18rpx;
}

.empty-action {
  width: 250rpx;
  margin-top: 10rpx;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  padding-bottom: calc(28rpx + env(safe-area-inset-bottom));
}

.notification-item {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
  min-height: 144rpx;
  padding: 22rpx 80rpx 22rpx 20rpx;
  border: 1rpx solid rgba(236, 238, 240, 0.95);
  border-radius: 8rpx;
  background: #fff;
  box-shadow: var(--bn-shadow-soft);
}

.notification-item.unread {
  border-color: rgba(255, 95, 87, 0.22);
  background: linear-gradient(90deg, rgba(255, 95, 87, 0.055), #fff 34%);
}

.actor-stack {
  position: relative;
  flex: 0 0 auto;
  width: 82rpx;
  height: 82rpx;
}

.system-avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--bn-blue), var(--bn-teal));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 840;
  border: 4rpx solid rgba(255, 255, 255, 0.88);
  box-shadow: 0 10rpx 22rpx rgba(27, 43, 56, 0.12);
}

.type-mark {
  position: absolute;
  right: -2rpx;
  bottom: -2rpx;
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: var(--bn-coral);
  color: #fff;
  border: 3rpx solid #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  font-weight: 820;
}

.notification-main {
  flex: 1;
  min-width: 0;
}

.notification-head {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
}

.notification-title {
  flex: 1;
  min-width: 0;
  color: var(--bn-ink);
  font-size: 28rpx;
  font-weight: 820;
  line-height: 1.38;
  word-break: break-word;
}

.notification-time {
  flex: 0 0 auto;
  color: var(--bn-faint);
  font-size: 22rpx;
  line-height: 1.5;
}

.notification-copy {
  margin-top: 8rpx;
  color: #525862;
  font-size: 24rpx;
  line-height: 1.45;
  word-break: break-word;
}

.notification-meta {
  margin-top: 12rpx;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10rpx;
  color: #8a9099;
  font-size: 22rpx;
}

.target-copy {
  max-width: 390rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #5f6671;
}

.target-cover {
  flex: 0 0 auto;
  width: 88rpx;
  height: 88rpx;
  border-radius: 8rpx;
  background: #eef1f3;
}

.delete-button {
  position: absolute;
  right: 18rpx;
  bottom: 18rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #f0f2f4;
  color: #828893;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  line-height: 1;
}

.delete-button[disabled] {
  opacity: 0.45;
}

.unread-dot {
  position: absolute;
  right: 28rpx;
  top: 24rpx;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: var(--bn-coral);
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
