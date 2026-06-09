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

const profile = computed(() => auth.profile)
const leftNotes = computed(() => notes.value.filter((_, index) => index % 2 === 0))
const rightNotes = computed(() => notes.value.filter((_, index) => index % 2 === 1))

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

async function logout() {
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
      <view class="profile-card panel">
        <view class="cover-band">
          <view class="cover-label">个人主页</view>
          <view class="cover-title">{{ profile?.nickname || 'BlueNote 用户' }}</view>
        </view>
        <view class="profile-main">
          <AvatarCircle :src="profile?.avatarUrl" :name="profile?.nickname" size="large" />
          <view class="profile-copy">
            <view class="nickname">{{ profile?.nickname || 'BlueNote 用户' }}</view>
            <view class="bn-no">{{ profile?.bluenoteNo || 'BN' }}</view>
            <view class="bio">{{ profile?.bio || '记录生活，慢慢变成自己的地图。' }}</view>
          </view>
        </view>
        <view class="profile-stats">
          <view class="profile-stat">
            <text class="stat-value">{{ notes.length }}</text>
            <text class="stat-label">笔记</text>
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
        <view class="status-row">
          <view class="status-pill">{{ profile?.userStatus === 'NORMAL' ? '状态正常' : profile?.userStatus || '未知状态' }}</view>
          <button class="ghost-button refresh-button" :disabled="loading" @tap="loadProfile">刷新</button>
          <button class="secondary-button logout-button" @tap="logout">退出</button>
        </view>
      </view>

      <view class="section-header">
        <view>
          <view class="section-title">我的笔记</view>
          <view class="section-subtitle">已发布、草稿和私密内容都会在这里出现</view>
        </view>
        <button class="new-note-button" @tap="goPublish">+</button>
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
  box-shadow: var(--bn-shadow);
}

.cover-band {
  height: 218rpx;
  padding: 30rpx 30rpx 0;
  background:
    linear-gradient(135deg, rgba(39, 118, 223, 0.94), rgba(36, 181, 159, 0.84) 68%, rgba(244, 191, 69, 0.72)),
    var(--bn-blue);
}

.cover-label {
  color: rgba(255, 255, 255, 0.78);
  font-size: 22rpx;
  font-weight: 760;
}

.cover-title {
  margin-top: 12rpx;
  max-width: 480rpx;
  color: #fff;
  font-size: 42rpx;
  font-weight: 860;
  line-height: 1.2;
}

.profile-main {
  display: flex;
  align-items: flex-end;
  gap: 24rpx;
  padding: 0 28rpx 22rpx;
  margin-top: -68rpx;
}

.profile-copy {
  min-width: 0;
  padding-bottom: 8rpx;
}

.nickname {
  font-size: 38rpx;
  font-weight: 860;
}

.bn-no {
  margin-top: 8rpx;
  color: var(--bn-muted);
  font-size: 23rpx;
}

.bio {
  margin-top: 12rpx;
  color: #3a4650;
  font-size: 25rpx;
  line-height: 1.5;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
  padding: 0 28rpx 24rpx;
}

.profile-stat {
  min-height: 86rpx;
  border-radius: 16rpx;
  background: #f7fbf9;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}

.stat-value {
  color: var(--bn-ink);
  font-size: 30rpx;
  font-weight: 820;
}

.stat-label {
  color: var(--bn-muted);
  font-size: 21rpx;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx 28rpx;
}

.status-pill {
  flex: 1;
  min-height: 62rpx;
  padding: 0 18rpx;
  border-radius: 16rpx;
  background: rgba(36, 181, 159, 0.12);
  color: #16786d;
  display: flex;
  align-items: center;
  font-size: 23rpx;
}

.refresh-button,
.logout-button {
  width: 116rpx;
  min-height: 62rpx;
  font-size: 23rpx;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 36rpx 0 22rpx;
}

.section-title {
  font-size: 36rpx;
  font-weight: 860;
}

.section-subtitle {
  margin-top: 6rpx;
  color: var(--bn-muted);
  font-size: 22rpx;
}

.new-note-button {
  width: 70rpx;
  height: 70rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--bn-coral), #ff9b62);
  color: #fff;
  font-size: 42rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 16rpx 30rpx rgba(243, 107, 90, 0.2);
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
