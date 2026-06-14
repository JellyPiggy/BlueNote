<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onReachBottom, onShow } from '@dcloudio/uni-app'
import { getMyCollections, getMyLikedNotes, getMyNotes } from '@/api/note'
import { showApiError } from '@/api/request'
import type { NoteCard, UserHome } from '@/api/types'
import { getUserHome } from '@/api/user'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import NoteCardView from '@/components/NoteCard.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const profileHome = ref<UserHome | null>(null)
const loading = ref(false)
const accountMenuOpen = ref(false)
const activeTab = ref<ProfileTabKey>('NOTES')
const listStates = ref<Record<ProfileTabKey, ProfileListState>>(createListStates())

const profile = computed(() => auth.profile)
const activeState = computed(() => listStates.value[activeTab.value])
const activeItems = computed(() => activeState.value.items)
const activeConfig = computed(() => profileTabs.find((tab) => tab.key === activeTab.value) ?? profileTabs[0])
const leftNotes = computed(() => activeItems.value.filter((_, index) => index % 2 === 0))
const rightNotes = computed(() => activeItems.value.filter((_, index) => index % 2 === 1))
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

type ProfileTabKey = 'NOTES' | 'COLLECTIONS' | 'LIKES'

interface ProfileTabConfig {
  key: ProfileTabKey
  label: string
  emptyTitle: string
  emptySubtitle: string
}

interface ProfileListState {
  items: NoteCard[]
  nextCursor: string | null
  hasMore: boolean
  loading: boolean
  loaded: boolean
  errorText: string
}

const profileTabs: ProfileTabConfig[] = [
  {
    key: 'NOTES',
    label: '笔记',
    emptyTitle: '还没有笔记',
    emptySubtitle: '从一张图片开始，发布你的第一条 BlueNote。'
  },
  {
    key: 'COLLECTIONS',
    label: '收藏',
    emptyTitle: '还没有收藏',
    emptySubtitle: '遇到想反复看的笔记，可以先收藏起来。'
  },
  {
    key: 'LIKES',
    label: '赞过',
    emptyTitle: '还没有赞过',
    emptySubtitle: '喜欢的内容会在这里留下痕迹。'
  }
]

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

onReachBottom(() => {
  if (activeState.value.loaded && activeState.value.hasMore) {
    void loadActiveList(false)
  }
})

async function loadProfile() {
  loading.value = true
  try {
    await auth.fetchCurrentUser()
    const userId = auth.profile?.userId ?? auth.userId
    const home = userId ? await getUserHome(userId).catch(() => null) : null
    profileHome.value = home
    await loadActiveList(true)
  } catch (error) {
    showApiError(error, '个人页加载失败')
  } finally {
    loading.value = false
  }
}

async function loadActiveList(reset = false) {
  if (!auth.isAuthenticated) {
    return
  }
  const state = activeState.value
  if (state.loading) {
    return
  }
  if (!reset && state.loaded && !state.hasMore) {
    return
  }
  state.loading = true
  state.errorText = ''
  try {
    const page = await fetchProfileList(activeTab.value, reset ? null : state.nextCursor, 20)
    state.items = reset ? page.items : mergeNotes(state.items, page.items)
    state.nextCursor = page.nextCursor
    state.hasMore = page.hasMore
    state.loaded = true
  } catch (error) {
    state.errorText = '内容加载失败'
    showApiError(error, '内容加载失败')
  } finally {
    state.loading = false
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function goPublish() {
  uni.switchTab({ url: '/pages/publish/index' })
}

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}

function openNote(noteId: string) {
  uni.navigateTo({ url: `/pages/note/detail?noteId=${noteId}` })
}

function openAccountMenu() {
  accountMenuOpen.value = true
}

async function switchProfileTab(tab: ProfileTabKey) {
  activeTab.value = tab
  if (!activeState.value.loaded && !activeState.value.loading) {
    await loadActiveList(true)
  }
}

function closeAccountMenu() {
  accountMenuOpen.value = false
}

function editProfileFromMenu() {
  accountMenuOpen.value = false
  uni.navigateTo({ url: '/pages/profile/edit' })
}

async function refreshFromMenu() {
  accountMenuOpen.value = false
  await loadProfile()
}

async function logout() {
  accountMenuOpen.value = false
  await auth.logoutCurrentDevice()
  listStates.value = createListStates()
  profileHome.value = null
  uni.navigateTo({ url: '/pages/login/index' })
}

async function switchAccount() {
  accountMenuOpen.value = false
  await auth.logoutCurrentDevice()
  listStates.value = createListStates()
  profileHome.value = null
  uni.navigateTo({ url: '/pages/login/index' })
}

function fetchProfileList(tab: ProfileTabKey, cursor?: string | null, size = 20) {
  if (tab === 'COLLECTIONS') {
    return getMyCollections(cursor, size)
  }
  if (tab === 'LIKES') {
    return getMyLikedNotes(cursor, size)
  }
  return getMyNotes(undefined, cursor, size)
}

function mergeNotes(current: NoteCard[], incoming: NoteCard[]) {
  const exists = new Set(current.map((item) => item.noteId))
  return [...current, ...incoming.filter((item) => !exists.has(item.noteId))]
}

function createListStates(): Record<ProfileTabKey, ProfileListState> {
  return {
    NOTES: createListState(),
    COLLECTIONS: createListState(),
    LIKES: createListState()
  }
}

function createListState(): ProfileListState {
  return {
    items: [],
    nextCursor: null,
    hasMore: false,
    loading: false,
    loaded: false,
    errorText: ''
  }
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
          <button
            v-for="tab in profileTabs"
            :key="tab.key"
            class="profile-tab"
            :class="{ active: activeTab === tab.key }"
            @tap="switchProfileTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </view>
      </view>

      <view v-if="activeState.loading && !activeState.loaded" class="loading-copy">正在读取内容</view>

      <EmptyState v-else-if="activeState.errorText" title="暂时读不到内容" subtitle="稍后刷新，或检查本地网关是否正在运行。">
        <button class="secondary-button login-action" @tap="loadActiveList(true)">重新加载</button>
      </EmptyState>

      <EmptyState
        v-else-if="activeState.loaded && !activeItems.length"
        :title="activeConfig.emptyTitle"
        :subtitle="activeConfig.emptySubtitle"
      >
        <button v-if="activeTab === 'NOTES'" class="primary-button login-action" @tap="goPublish">发布笔记</button>
        <button v-else class="secondary-button login-action" @tap="goHome">去首页</button>
      </EmptyState>

      <view v-else class="masonry">
        <view class="column">
          <NoteCardView v-for="note in leftNotes" :key="note.noteId" :note="note" @open="openNote" />
        </view>
        <view class="column">
          <NoteCardView v-for="note in rightNotes" :key="note.noteId" :note="note" @open="openNote" />
        </view>
      </view>

      <button v-if="activeState.hasMore" class="load-more-button" :disabled="activeState.loading" @tap="loadActiveList(false)">
        {{ activeState.loading ? '加载中' : '加载更多' }}
      </button>

      <view v-else-if="activeState.loaded && activeItems.length" class="list-end">没有更多内容</view>

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
  padding: 0;
  border-bottom: 1rpx solid var(--bn-line);
  background: #fff;
}

.profile-tab {
  position: relative;
  min-height: 92rpx;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
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
  width: 58rpx;
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

.load-more-button {
  width: 260rpx;
  height: 64rpx;
  margin: 18rpx auto 0;
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

.load-more-button[disabled] {
  opacity: 0.58;
}

.list-end {
  padding: 24rpx 0 8rpx;
  color: var(--bn-faint);
  text-align: center;
  font-size: 23rpx;
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
