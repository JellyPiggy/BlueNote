<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { getAuthorNotes } from '@/api/note'
import { followUser, getFollowStatus, unfollowUser } from '@/api/relation'
import { showApiError } from '@/api/request'
import type { FollowStatus, NoteCard, UserHome } from '@/api/types'
import { getUserHome } from '@/api/user'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import NoteCardView from '@/components/NoteCard.vue'
import { useAuthStore } from '@/stores/auth'
import { formatCount } from '@/utils/format'

const auth = useAuthStore()
const userId = ref('')
const profileHome = ref<UserHome | null>(null)
const notes = ref<NoteCard[]>([])
const nextCursor = ref<string | null>(null)
const hasMore = ref(false)
const loading = ref(false)
const notesLoading = ref(false)
const loaded = ref(false)
const errorText = ref('')
const followSubmitting = ref(false)

const user = computed(() => profileHome.value?.user)
const isSelf = computed(() => Boolean(userId.value && auth.userId === userId.value))
const isFollowing = computed(() => profileHome.value?.relation.followStatus === 'FOLLOWING')
const followText = computed(() => {
  if (followSubmitting.value) {
    return '...'
  }
  return isFollowing.value ? '已关注' : '关注'
})
const heroStats = computed(() => {
  const counts = profileHome.value?.counts
  return [
    { label: '笔记', value: formatCount(counts?.noteCount) },
    { label: '关注', value: formatCount(counts?.followingCount) },
    { label: '粉丝', value: formatCount(counts?.followerCount) },
    { label: '获赞', value: formatCount(counts?.likedCount) }
  ]
})
const leftNotes = computed(() => notes.value.filter((_, index) => index % 2 === 0))
const rightNotes = computed(() => notes.value.filter((_, index) => index % 2 === 1))

onLoad((options) => {
  userId.value = String(options?.userId ?? '')
  if (!userId.value) {
    errorText.value = '用户不存在'
    return
  }
  if (isSelf.value) {
    uni.switchTab({ url: '/pages/profile/index' })
    return
  }
  void refresh()
})

onPullDownRefresh(async () => {
  try {
    await refresh()
  } finally {
    uni.stopPullDownRefresh()
  }
})

onReachBottom(() => {
  if (hasMore.value) {
    void loadNotes(false)
  }
})

async function refresh() {
  if (!userId.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const home = await loadUserHome()
    profileHome.value = home
    await Promise.all([syncFollowStatus(), loadNotes(true)])
    loaded.value = true
  } catch (error) {
    errorText.value = '主页加载失败'
    showApiError(error, '主页加载失败')
  } finally {
    loading.value = false
  }
}

async function loadUserHome() {
  const withAuth = auth.isAuthenticated
  try {
    return await getUserHome(userId.value, withAuth)
  } catch (error) {
    if (withAuth && !auth.isAuthenticated) {
      return getUserHome(userId.value, false)
    }
    throw error
  }
}

async function syncFollowStatus() {
  if (!auth.isAuthenticated || isSelf.value || !profileHome.value) {
    return
  }
  if (profileHome.value.relation.followStatus !== 'UNKNOWN') {
    return
  }
  try {
    const response = await getFollowStatus(userId.value)
    setFollowStatus(normalizeFollowStatus(response.followStatus))
  } catch {
    // 主页仍可浏览，关注状态失败时保留首页接口返回的状态。
  }
}

async function loadNotes(reset = false) {
  if (!userId.value || notesLoading.value) {
    return
  }
  if (!reset && loaded.value && !hasMore.value) {
    return
  }
  notesLoading.value = true
  try {
    const page = await getAuthorNotes(userId.value, reset ? null : nextCursor.value, 20)
    notes.value = reset ? page.items : mergeNotes(notes.value, page.items)
    nextCursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (error) {
    showApiError(error, '笔记加载失败')
  } finally {
    notesLoading.value = false
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

function openNote(noteId: string) {
  uni.navigateTo({ url: `/pages/note/detail?noteId=${noteId}` })
}

function openChat() {
  if (!userId.value || isSelf.value) {
    return
  }
  if (!auth.isAuthenticated) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  const title = encodeURIComponent(user.value?.nickname || '私信')
  uni.navigateTo({ url: `/pages/im/chat?targetUserId=${encodeURIComponent(userId.value)}&title=${title}` })
}

async function toggleFollow() {
  if (!profileHome.value || followSubmitting.value) {
    return
  }
  if (!auth.isAuthenticated) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  if (isSelf.value) {
    uni.switchTab({ url: '/pages/profile/index' })
    return
  }

  const previousStatus = profileHome.value.relation.followStatus
  const previousFollowerCount = profileHome.value.counts.followerCount
  const shouldUnfollow = previousStatus === 'FOLLOWING'
  patchFollowStatus(shouldUnfollow ? 'NOT_FOLLOWING' : 'FOLLOWING')
  followSubmitting.value = true
  try {
    const response = shouldUnfollow ? await unfollowUser(userId.value) : await followUser(userId.value)
    patchFollowStatus(normalizeFollowStatus(response.followStatus))
    uni.showToast({ title: isFollowing.value ? '已关注' : '已取消关注', icon: 'none' })
  } catch (error) {
    patchFollowStatus(previousStatus, previousFollowerCount)
    showApiError(error, '操作失败')
  } finally {
    followSubmitting.value = false
  }
}

function setFollowStatus(status: FollowStatus) {
  if (!profileHome.value) {
    return
  }
  profileHome.value.relation.followStatus = status
}

function patchFollowStatus(status: FollowStatus, followerCount?: number) {
  if (!profileHome.value) {
    return
  }
  const currentStatus = profileHome.value.relation.followStatus
  profileHome.value.relation.followStatus = status
  if (typeof followerCount === 'number') {
    profileHome.value.counts.followerCount = followerCount
    return
  }
  if (currentStatus === status) {
    return
  }
  const delta = status === 'FOLLOWING' ? 1 : currentStatus === 'FOLLOWING' ? -1 : 0
  profileHome.value.counts.followerCount = Math.max(0, profileHome.value.counts.followerCount + delta)
}

function normalizeFollowStatus(status: string): FollowStatus {
  if (status === 'FOLLOWING' || status === 'NOT_FOLLOWING') {
    return status
  }
  return 'UNKNOWN'
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
  <view class="screen user-profile-screen top-safe">
    <view class="profile-topbar">
      <button class="back-button" @tap="back">‹</button>
      <view class="top-title">{{ user?.nickname || '主页' }}</view>
      <button
        v-if="profileHome && !isSelf"
        class="follow-button"
        :class="{ followed: isFollowing }"
        :disabled="followSubmitting"
        @tap="toggleFollow"
      >
        {{ followText }}
      </button>
      <button v-if="profileHome && !isSelf" class="message-button" @tap="openChat">私信</button>
      <view v-else class="top-spacer"></view>
    </view>

    <view v-if="loading && !profileHome" class="loading-copy">正在打开主页</view>

    <EmptyState v-else-if="errorText" title="暂时打不开主页" subtitle="稍后刷新，或检查本地网关是否正在运行。">
      <button class="secondary-button retry-button" @tap="refresh">重新加载</button>
    </EmptyState>

    <view v-else-if="profileHome" class="profile-body">
      <view class="user-hero">
        <AvatarCircle :src="user?.avatarUrl" :name="user?.nickname" size="large" />
        <view class="user-copy">
          <view class="nickname">{{ user?.nickname || 'BlueNote 用户' }}</view>
          <view class="bn-no">BlueNote号 · {{ user?.bluenoteNo || 'BN' }}</view>
          <view class="bio">{{ user?.bio || '还没有填写简介' }}</view>
        </view>
      </view>

      <view class="hero-stats">
        <view v-for="stat in heroStats" :key="stat.label" class="hero-stat">
          <text class="hero-stat-value">{{ stat.value }}</text>
          <text class="hero-stat-label">{{ stat.label }}</text>
        </view>
      </view>

      <view v-if="notesLoading && !notes.length" class="loading-copy">正在读取笔记</view>

      <EmptyState v-else-if="loaded && !notes.length" title="还没有公开笔记" subtitle="这个作者暂时没有可看的公开内容。" />

      <view v-else class="masonry">
        <view class="column">
          <NoteCardView v-for="note in leftNotes" :key="note.noteId" :note="note" @open="openNote" />
        </view>
        <view class="column">
          <NoteCardView v-for="note in rightNotes" :key="note.noteId" :note="note" @open="openNote" />
        </view>
      </view>

      <button v-if="hasMore" class="load-more-button" :disabled="notesLoading" @tap="loadNotes(false)">
        {{ notesLoading ? '加载中' : '加载更多' }}
      </button>

      <view v-else-if="loaded && notes.length" class="list-end">没有更多内容</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.user-profile-screen {
  padding: 0 0 calc(28rpx + env(safe-area-inset-bottom));
}

.profile-topbar {
  min-height: 116rpx;
  padding: 24rpx 28rpx 16rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
  background: #fff;
  border-bottom: 1rpx solid var(--bn-line);
}

.back-button,
.top-spacer {
  flex: 0 0 auto;
  width: 72rpx;
  height: 72rpx;
}

.back-button {
  border-radius: 50%;
  background: #f5f6f7;
  color: var(--bn-ink);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52rpx;
  font-weight: 520;
}

.top-title {
  flex: 1;
  min-width: 0;
  color: var(--bn-ink);
  font-size: 34rpx;
  font-weight: 900;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.follow-button,
.message-button {
  flex: 0 0 auto;
  min-width: 112rpx;
  height: 64rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  border: 2rpx solid var(--bn-coral);
  color: #fff;
  background: var(--bn-coral);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 860;
  white-space: nowrap;
}

.follow-button.followed {
  color: var(--bn-coral);
  background: #fff;
}

.follow-button[disabled] {
  opacity: 0.62;
}

.message-button {
  border-color: var(--bn-line);
  color: var(--bn-ink);
  background: #fff;
}

.profile-body {
  background: var(--bn-bg);
}

.user-hero {
  display: flex;
  align-items: center;
  gap: 28rpx;
  padding: 40rpx 32rpx 24rpx;
  background: #fff;
}

.user-hero :deep(.avatar-large) {
  width: 136rpx;
  height: 136rpx;
  font-size: 40rpx;
}

.user-copy {
  flex: 1;
  min-width: 0;
}

.nickname {
  color: var(--bn-ink);
  font-size: 42rpx;
  font-weight: 900;
  line-height: 1.15;
}

.bn-no {
  margin-top: 12rpx;
  color: var(--bn-muted);
  font-size: 24rpx;
  line-height: 1.4;
}

.bio {
  margin-top: 12rpx;
  color: #4f5660;
  font-size: 25rpx;
  line-height: 1.48;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  padding: 16rpx 18rpx 28rpx;
  background: #fff;
  border-bottom: 1rpx solid var(--bn-line);
}

.hero-stat {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.hero-stat-value {
  color: var(--bn-ink);
  font-size: 30rpx;
  font-weight: 860;
  line-height: 1.1;
}

.hero-stat-label {
  color: var(--bn-muted);
  font-size: 23rpx;
  line-height: 1.1;
}

.masonry {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 16rpx 20rpx 0;
}

.column {
  flex: 1;
  min-width: 0;
}

.retry-button {
  width: 250rpx;
  margin-top: 12rpx;
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
</style>
