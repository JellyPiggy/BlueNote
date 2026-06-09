<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getMyNotes } from '@/api/note'
import { showApiError } from '@/api/request'
import type { NoteCard } from '@/api/types'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import NoteCardView from '@/components/NoteCard.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const notes = ref<NoteCard[]>([])
const loading = ref(false)
const accountMenuOpen = ref(false)

const profile = computed(() => auth.profile)
const leftNotes = computed(() => notes.value.filter((_, index) => index % 2 === 0))
const rightNotes = computed(() => notes.value.filter((_, index) => index % 2 === 1))
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
    const page = await getMyNotes(undefined, null, 30)
    notes.value = page.items
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

function closeAccountMenu() {
  accountMenuOpen.value = false
}

async function refreshFromMenu() {
  accountMenuOpen.value = false
  await loadProfile()
}

async function logout() {
  accountMenuOpen.value = false
  await auth.logoutCurrentDevice()
  notes.value = []
  uni.navigateTo({ url: '/pages/login/index' })
}

async function switchAccount() {
  accountMenuOpen.value = false
  await auth.logoutCurrentDevice()
  notes.value = []
  uni.navigateTo({ url: '/pages/login/index' })
}
</script>

<template>
  <view class="screen top-safe">
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
        </view>

        <view class="profile-summary">
          <view class="profile-stats">
            <view class="profile-stat">
              <text class="stat-value">{{ notes.length }}</text>
              <text class="stat-label">笔记</text>
            </view>
            <view class="profile-stat">
              <text class="stat-value">0</text>
              <text class="stat-label">获赞</text>
            </view>
            <view class="profile-stat">
              <text class="stat-value">0</text>
              <text class="stat-label">关注</text>
            </view>
            <view class="profile-stat">
              <text class="stat-value">0</text>
              <text class="stat-label">粉丝</text>
            </view>
          </view>
          <view class="profile-actions">
            <button class="edit-profile-button">编辑主页</button>
          </view>
        </view>

        <view class="profile-tabs">
          <button class="profile-tab active">笔记</button>
          <button class="profile-tab">收藏</button>
          <button class="profile-tab">赞过</button>
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

.profile-card {
  overflow: hidden;
  border-radius: 18rpx;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0), #fff 78%),
    #fff;
  box-shadow: var(--bn-shadow);
}

.profile-hero {
  position: relative;
  min-height: 360rpx;
  padding: 56rpx 30rpx 42rpx;
  background:
    linear-gradient(180deg, rgba(9, 36, 54, 0.06), rgba(9, 36, 54, 0.66)),
    radial-gradient(circle at 18% 20%, rgba(255, 255, 255, 0.48), transparent 28%),
    linear-gradient(135deg, #5eb9e8 0%, #1e7ba2 48%, #15526f 100%);
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
  gap: 24rpx;
  min-height: 260rpx;
}

.avatar-wrap {
  position: relative;
  flex: 0 0 auto;
}

.account-menu-button {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
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
  font-size: 48rpx;
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
  max-width: 420rpx;
  color: rgba(255, 255, 255, 0.92);
  font-size: 25rpx;
  line-height: 1.48;
}

.profile-summary {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-height: 150rpx;
  margin-top: -20rpx;
  padding: 26rpx 26rpx 28rpx;
  border-radius: 22rpx 22rpx 0 0;
  background: #fff;
}

.profile-stats {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8rpx;
}

.profile-stat {
  min-height: 96rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}

.stat-value {
  color: var(--bn-ink);
  font-size: 36rpx;
  font-weight: 900;
}

.stat-label {
  color: var(--bn-muted);
  font-size: 23rpx;
}

.profile-actions {
  flex: 0 0 178rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.edit-profile-button {
  min-height: 72rpx;
  border-radius: 14rpx;
  background: #f3f3f4;
  color: var(--bn-ink);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 820;
}

.profile-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8rpx;
  margin: 0;
  padding: 10rpx 18rpx 12rpx;
  border-top: 1rpx solid rgba(236, 238, 240, 0.96);
  border-radius: 0 0 18rpx 18rpx;
  background: #fff;
}

.profile-tab {
  position: relative;
  height: 62rpx;
  border-radius: 14rpx;
  color: var(--bn-muted);
  font-size: 25rpx;
  font-weight: 720;
}

.profile-tab.active {
  color: var(--bn-ink);
  background: #f4f4f5;
  font-weight: 860;
}

.masonry {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  margin-top: 16rpx;
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
