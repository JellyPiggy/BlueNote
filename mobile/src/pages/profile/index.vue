<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getMyNotes } from '@/api/note'
import { showApiError } from '@/api/request'
import type { NoteCard, UserHome } from '@/api/types'
import { getUserHome } from '@/api/user'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import NoteCardView from '@/components/NoteCard.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'

const auth = useAuthStore()
const notifications = useNotificationStore()
const notes = ref<NoteCard[]>([])
const profileHome = ref<UserHome | null>(null)
const loading = ref(false)
const accountMenuOpen = ref(false)

const profile = computed(() => auth.profile)
const leftNotes = computed(() => notes.value.filter((_, index) => index % 2 === 0))
const rightNotes = computed(() => notes.value.filter((_, index) => index % 2 === 1))
const heroStats = computed(() => {
  const counts = profileHome.value?.counts
  return [
    {
      label: '关注',
      value: formatCount(counts?.followingCount ?? 0)
    },
    {
      label: '粉丝',
      value: formatCount(counts?.followerCount ?? 0)
    },
    {
      label: '获赞',
      value: formatCount(counts?.likedCount ?? 0)
    }
  ]
})
const coverStyle = computed(() => {
  const coverUrl = profile.value?.homeCoverUrl
  if (!coverUrl) {
    return {}
  }
  return {
    backgroundImage: `linear-gradient(180deg, rgba(10, 28, 45, 0.1), rgba(10, 28, 45, 0.62)), url("${coverUrl}")`
  }
})

onShow(() => {
  if (auth.isAuthenticated) {
    void loadProfile()
  }
})

onPullDownRefresh(async () => {
  try {
    await loadProfile()
  } finally {
    uni.stopPullDownRefresh()
  }
})

async function loadProfile() {
  loading.value = true
  try {
    await auth.fetchCurrentUser()
    const userId = auth.profile?.userId ?? auth.userId
    const [page, home] = await Promise.all([
      getMyNotes(undefined, null, 30),
      userId ? getUserHome(userId).catch(() => null) : Promise.resolve(null)
    ])
    notes.value = page.items
    profileHome.value = home
  } catch (error) {
    showApiError(error, '个人页加载失败')
  } finally {
    loading.value = false
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function goPublish() {
  uni.switchTab({ url: '/pages/publish/index' })
}

function openNote(noteId: string) {
  uni.navigateTo({ url: `/pages/note/detail?noteId=${noteId}` })
}

function openAccountMenu() {
  accountMenuOpen.value = true
}

function openNotifications() {
  uni.navigateTo({ url: '/pages/notifications/index' })
}

function closeAccountMenu() {
  accountMenuOpen.value = false
}

function editProfileFromMenu() {
  accountMenuOpen.value = false
  uni.showToast({
    title: '编辑主页功能接入中',
    icon: 'none'
  })
}

async function refreshFromMenu() {
  accountMenuOpen.value = false
  await loadProfile()
}

async function logout() {
  accountMenuOpen.value = false
  await auth.logoutCurrentDevice()
  notes.value = []
  profileHome.value = null
  uni.navigateTo({ url: '/pages/login/index' })
}

async function switchAccount() {
  accountMenuOpen.value = false
  await auth.logoutCurrentDevice()
  notes.value = []
  profileHome.value = null
  uni.navigateTo({ url: '/pages/login/index' })
}

function formatCount(value: number) {
  if (value >= 10000) {
    const formatted = (value / 10000).toFixed(value >= 100000 ? 0 : 1)
    return `${formatted.replace(/\.0$/, '')}万`
  }
  return String(value)
}
</script>

<template>
  <view class="screen profile-screen">
    <EmptyState
      v-if="!auth.isAuthenticated"
      title="还没有登录"
      subtitle="登录后可以查看资料、发布笔记，并管理自己的内容。"
    >
      <button class="primary-button login-action" @tap="goLogin">去登录</button>
    </EmptyState>

    <view v-else>
      <view class="profile-card">
        <view class="profile-hero" :style="coverStyle">
          <view class="hero-overlay"></view>
          <button class="profile-message-button" @tap="openNotifications">
            <text class="profile-message-icon">◌</text>
            <text v-if="notifications.badgeText" class="profile-message-badge">{{ notifications.badgeText }}</text>
          </button>
          <button class="account-menu-button" @tap="openAccountMenu">
            <view class="menu-line"></view>
            <view class="menu-line"></view>
            <view class="menu-line"></view>
          </button>
          <view class="hero-content">
            <view class="avatar-wrap">
              <AvatarCircle :src="profile?.avatarUrl" :name="profile?.nickname" size="large" />
            </view>
            <view class="profile-copy">
              <view class="nickname">{{ profile?.nickname || 'BlueNote 用户' }}</view>
              <view class="bn-no">BlueNote号 · {{ profile?.bluenoteNo || 'BN' }}</view>
              <view class="bio">{{ profile?.bio || '记录生活，慢慢变成自己的地图。' }}</view>
            </view>
          </view>
          <view class="hero-stats">
            <view v-for="stat in heroStats" :key="stat.label" class="hero-stat">
              <text class="hero-stat-value">{{ stat.value }}</text>
              <text class="hero-stat-label">{{ stat.label }}</text>
            </view>
          </view>
        </view>

        <view class="profile-tabs">
          <view class="profile-tab active">笔记</view>
          <view class="profile-tab">收藏</view>
          <view class="profile-tab">赞过</view>
        </view>
      </view>

      <view v-if="loading && !notes.length" class="loading-copy">正在读取个人资料</view>

      <EmptyState v-else-if="!notes.length" title="还没有笔记" subtitle="从一张图片开始，发布你的第一条 BlueNote。">
        <button class="primary-button login-action" @tap="goPublish">发布笔记</button>
      </EmptyState>

      <view v-else class="masonry">
        <view class="column">
          <NoteCardView v-for="note in leftNotes" :key="note.noteId" :note="note" @open="openNote" />
        </view>
        <view class="column">
          <NoteCardView v-for="note in rightNotes" :key="note.noteId" :note="note" @open="openNote" />
        </view>
      </view>

      <view v-if="accountMenuOpen" class="account-menu-mask" @tap="closeAccountMenu">
        <view class="account-drawer" @tap.stop>
          <view class="drawer-handle"></view>
          <view class="drawer-title">账号管理</view>
          <button class="drawer-item" @tap="editProfileFromMenu">
            <text>编辑主页</text>
            <text class="drawer-arrow">›</text>
          </button>
          <button class="drawer-item" @tap="refreshFromMenu">
            <text>刷新资料</text>
            <text class="drawer-arrow">›</text>
          </button>
          <button class="drawer-item" @tap="switchAccount">
            <text>切换账号</text>
            <text class="drawer-arrow">›</text>
          </button>
          <button class="drawer-item danger" @tap="logout">
            <text>退出当前账号</text>
            <text class="drawer-arrow">›</text>
          </button>
          <button class="drawer-cancel" @tap="closeAccountMenu">取消</button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.login-action {
  width: 250rpx;
  margin-top: 12rpx;
}

.profile-screen {
  padding: 0 0 calc(24rpx + env(safe-area-inset-bottom));
  background: var(--bn-bg);
}

.profile-card {
  overflow: hidden;
  border-radius: 0;
  background: #fff;
  box-shadow: none;
}

.profile-hero {
  position: relative;
  min-height: 430rpx;
  padding: 58rpx 48rpx 36rpx;
  background:
    linear-gradient(180deg, rgba(7, 27, 39, 0.1), rgba(7, 27, 39, 0.7)),
    radial-gradient(circle at 18% 14%, rgba(255, 255, 255, 0.28), transparent 30%),
    linear-gradient(135deg, #5eb9e8 0%, #1b779b 48%, #0f3f58 100%);
  background-size: cover;
  background-position: center;
}

.hero-overlay {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(5, 24, 38, 0.02), rgba(5, 24, 38, 0.72)),
    linear-gradient(90deg, rgba(7, 32, 49, 0.55), rgba(7, 32, 49, 0.08));
  pointer-events: none;
}

.hero-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 28rpx;
  min-height: 178rpx;
  padding-right: 72rpx;
}

.avatar-wrap {
  position: relative;
  flex: 0 0 auto;
}

.avatar-wrap :deep(.avatar-large) {
  width: 146rpx;
  height: 146rpx;
  font-size: 42rpx;
}

.account-menu-button {
  position: absolute;
  top: 28rpx;
  right: 28rpx;
  z-index: 2;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  border: 1rpx solid rgba(255, 255, 255, 0.28);
  backdrop-filter: blur(12rpx);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7rpx;
}

.profile-message-button {
  position: absolute;
  top: 28rpx;
  left: 28rpx;
  z-index: 2;
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.16);
  border: 1rpx solid rgba(255, 255, 255, 0.28);
  color: #fff;
  backdrop-filter: blur(12rpx);
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-message-icon {
  color: #fff;
  font-size: 38rpx;
  line-height: 1;
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.2);
}

.profile-message-badge {
  position: absolute;
  right: -8rpx;
  top: -8rpx;
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: var(--bn-coral);
  color: #fff;
  border: 3rpx solid rgba(255, 255, 255, 0.96);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  font-weight: 820;
  line-height: 1;
}

.menu-line {
  width: 28rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: #fff;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.18);
}

.profile-copy {
  min-width: 0;
  flex: 1;
  color: #fff;
}

.nickname {
  color: #fff;
  font-size: 50rpx;
  font-weight: 900;
  line-height: 1.1;
  text-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.25);
}

.bn-no {
  margin-top: 16rpx;
  color: rgba(255, 255, 255, 0.76);
  font-size: 25rpx;
  line-height: 1.35;
}

.bio {
  margin-top: 14rpx;
  max-width: 460rpx;
  color: rgba(255, 255, 255, 0.92);
  font-size: 25rpx;
  line-height: 1.48;
}

.hero-stats {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 26rpx 48rpx;
  margin-top: 72rpx;
  color: #fff;
}

.hero-stat {
  display: flex;
  align-items: baseline;
  gap: 10rpx;
  min-width: 0;
  text-shadow: 0 4rpx 14rpx rgba(0, 0, 0, 0.28);
}

.hero-stat-value {
  color: #fff;
  font-size: 32rpx;
  font-weight: 680;
  line-height: 1.1;
}

.hero-stat-label {
  color: rgba(255, 255, 255, 0.88);
  font-size: 32rpx;
  font-weight: 620;
  line-height: 1.1;
}

.profile-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 0;
  padding: 0 40rpx;
  border-bottom: 1rpx solid rgba(236, 238, 240, 0.96);
  background: #fff;
}

.profile-tab {
  position: relative;
  min-height: 92rpx;
  color: var(--bn-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 760;
}

.profile-tab.active {
  color: var(--bn-ink);
  font-weight: 900;
}

.profile-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 0;
  width: 52rpx;
  height: 6rpx;
  border-radius: 999rpx;
  background: var(--bn-coral);
  transform: translateX(-50%);
}

.masonry {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-top: 16rpx;
  padding: 0 20rpx;
}

.column {
  flex: 1;
  min-width: 0;
}

.account-menu-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  background: rgba(17, 19, 24, 0.28);
  display: flex;
  justify-content: flex-end;
}

.account-drawer {
  width: 520rpx;
  min-height: 100vh;
  padding: 34rpx 26rpx calc(34rpx + env(safe-area-inset-bottom));
  background: #fff;
  box-shadow: -18rpx 0 40rpx rgba(18, 22, 28, 0.14);
}

.drawer-handle {
  width: 72rpx;
  height: 8rpx;
  margin: 0 0 28rpx auto;
  border-radius: 999rpx;
  background: #d9dde1;
}

.drawer-title {
  color: var(--bn-ink);
  font-size: 36rpx;
  font-weight: 900;
  margin-bottom: 24rpx;
}

.drawer-item {
  width: 100%;
  min-height: 88rpx;
  border-bottom: 1rpx solid var(--bn-line);
  color: #20242a;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 29rpx;
  font-weight: 720;
}

.drawer-item.danger {
  color: var(--bn-coral);
}

.drawer-arrow {
  color: var(--bn-faint);
  font-size: 42rpx;
  font-weight: 500;
}

.drawer-cancel {
  width: 100%;
  min-height: 78rpx;
  margin-top: 28rpx;
  border-radius: 14rpx;
  background: #f3f4f5;
  color: var(--bn-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 27rpx;
  font-weight: 760;
}
</style>
