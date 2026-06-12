<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow, onReachBottom } from '@dcloudio/uni-app'
import {
  getMyYearlyCreatorRank,
  getWeeklyHotNotesRank,
  getYearlyCreatorGrowthRank
} from '@/api/rank'
import { showApiError } from '@/api/request'
import type {
  MyYearlyCreatorRankResponse,
  WeeklyHotNoteRankItem,
  YearlyCreatorRankItem
} from '@/api/types'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { formatCount, formatTime } from '@/utils/format'

type RankTab = 'notes' | 'creators'

const auth = useAuthStore()
const activeTab = ref<RankTab>('notes')
const weeklyItems = ref<WeeklyHotNoteRankItem[]>([])
const creatorItems = ref<YearlyCreatorRankItem[]>([])
const weeklyCursor = ref<string | null>(null)
const creatorCursor = ref<string | null>(null)
const weeklyHasMore = ref(false)
const creatorHasMore = ref(false)
const weeklyLoaded = ref(false)
const creatorLoaded = ref(false)
const weeklyLoading = ref(false)
const creatorLoading = ref(false)
const loadingMore = ref(false)
const errorText = ref('')
const weeklyPeriodId = ref('')
const creatorPeriodId = ref('')
const weeklyDegraded = ref(false)
const creatorDegraded = ref(false)
const myRank = ref<MyYearlyCreatorRankResponse | null>(null)
const myRankLoading = ref(false)

const currentLoading = computed(() => activeTab.value === 'notes' ? weeklyLoading.value : creatorLoading.value)
const currentLoaded = computed(() => activeTab.value === 'notes' ? weeklyLoaded.value : creatorLoaded.value)
const currentEmpty = computed(() => activeTab.value === 'notes' ? !weeklyItems.value.length : !creatorItems.value.length)
const currentHasMore = computed(() => activeTab.value === 'notes' ? weeklyHasMore.value : creatorHasMore.value)
const currentPeriodId = computed(() => activeTab.value === 'notes' ? weeklyPeriodId.value : creatorPeriodId.value)
const currentDegraded = computed(() => activeTab.value === 'notes' ? weeklyDegraded.value : creatorDegraded.value)
const summaryTitle = computed(() => activeTab.value === 'notes' ? '本周热度' : '年度成长')
const summaryValue = computed(() => activeTab.value === 'notes' ? weeklyItems.value.length : creatorItems.value.length)
const periodType = computed(() => activeTab.value === 'notes' ? '周榜' : '年榜')

onShow(() => {
  if (!weeklyLoaded.value) {
    void refreshAll()
  } else if (auth.isAuthenticated && !myRank.value) {
    void loadMyRank()
  }
})

onPullDownRefresh(async () => {
  try {
    await refreshAll()
  } finally {
    uni.stopPullDownRefresh()
  }
})

onReachBottom(() => {
  void loadMore()
})

async function refreshAll() {
  await Promise.all([loadWeekly(true), loadCreators(true), loadMyRank()])
}

async function switchTab(tab: RankTab) {
  activeTab.value = tab
  if (tab === 'notes' && !weeklyLoaded.value) {
    await loadWeekly(true)
  }
  if (tab === 'creators' && !creatorLoaded.value) {
    await loadCreators(true)
  }
}

async function loadWeekly(reset = false) {
  if (weeklyLoading.value) return
  weeklyLoading.value = true
  errorText.value = ''
  try {
    const page = await getWeeklyHotNotesRank(reset ? null : weeklyCursor.value, 20)
    weeklyPeriodId.value = page.periodId
    weeklyDegraded.value = page.degraded
    weeklyItems.value = reset ? page.items : [...weeklyItems.value, ...page.items]
    weeklyCursor.value = page.nextCursor
    weeklyHasMore.value = page.hasMore
    weeklyLoaded.value = true
  } catch (error) {
    errorText.value = '榜单加载失败'
    showApiError(error, '榜单加载失败')
  } finally {
    weeklyLoading.value = false
  }
}

async function loadCreators(reset = false) {
  if (creatorLoading.value) return
  creatorLoading.value = true
  errorText.value = ''
  try {
    const page = await getYearlyCreatorGrowthRank(reset ? null : creatorCursor.value, 20)
    creatorPeriodId.value = page.periodId
    creatorDegraded.value = page.degraded
    creatorItems.value = reset ? page.items : [...creatorItems.value, ...page.items]
    creatorCursor.value = page.nextCursor
    creatorHasMore.value = page.hasMore
    creatorLoaded.value = true
  } catch (error) {
    errorText.value = '榜单加载失败'
    showApiError(error, '榜单加载失败')
  } finally {
    creatorLoading.value = false
  }
}

async function loadMyRank() {
  if (!auth.isAuthenticated) {
    myRank.value = null
    return
  }
  if (myRankLoading.value) return
  myRankLoading.value = true
  try {
    myRank.value = await getMyYearlyCreatorRank()
  } catch {
    myRank.value = null
  } finally {
    myRankLoading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !currentHasMore.value) return
  loadingMore.value = true
  try {
    if (activeTab.value === 'notes') {
      await loadWeekly(false)
    } else {
      await loadCreators(false)
    }
  } finally {
    loadingMore.value = false
  }
}

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function openNote(noteId: string) {
  uni.navigateTo({ url: `/pages/note/detail?noteId=${noteId}` })
}

function rankClass(rankNo: number) {
  if (rankNo <= 3) {
    return `rank-no rank-top-${rankNo}`
  }
  return 'rank-no'
}

function myRankText() {
  if (myRankLoading.value) return '刷新中'
  if (!auth.isAuthenticated) return '登录查看'
  if (!myRank.value || myRank.value.notRanked) return '未上榜'
  return `第 ${myRank.value.rankNo ?? '-'} 名`
}

function statText(value: number | null | undefined) {
  return formatCount(value ?? 0)
}
</script>

<template>
  <view class="screen rank-screen top-safe">
    <view class="rank-appbar">
      <button class="back-button" @tap="goHome">‹</button>
      <view class="page-title">排行榜</view>
      <button class="refresh-button" @tap="refreshAll">↻</button>
    </view>

    <view class="rank-summary">
      <view class="summary-main">
        <view class="summary-title">{{ summaryTitle }}</view>
        <view class="summary-value">{{ summaryValue }}</view>
      </view>
      <view class="summary-side">
        <view class="summary-label">{{ periodType }} {{ currentPeriodId || '-' }}</view>
        <view class="summary-label">{{ currentDegraded ? '降级数据' : '实时更新' }}</view>
      </view>
    </view>

    <view class="rank-tabs">
      <button :class="['rank-tab', { active: activeTab === 'notes' }]" @tap="switchTab('notes')">周热笔记</button>
      <button :class="['rank-tab', { active: activeTab === 'creators' }]" @tap="switchTab('creators')">创作者</button>
    </view>

    <view class="my-rank-card panel">
      <view>
        <view class="my-rank-title">我的年度成长排名</view>
        <view class="my-rank-subtitle">{{ auth.isAuthenticated ? `成长分 ${myRank?.score ?? 0}` : '登录后同步你的创作者位置' }}</view>
      </view>
      <button class="my-rank-pill" @tap="auth.isAuthenticated ? loadMyRank() : goLogin()">{{ myRankText() }}</button>
    </view>

    <view v-if="currentLoading && !currentLoaded" class="loading-copy">正在读取榜单</view>

    <EmptyState v-else-if="errorText" title="暂时读不到榜单" subtitle="稍后刷新，或检查本地网关是否正在运行。">
      <button class="secondary-button empty-action" @tap="refreshAll">重新加载</button>
    </EmptyState>

    <EmptyState v-else-if="currentLoaded && currentEmpty" title="榜单还在升温" subtitle="新的公开互动会把笔记和创作者推上来。" />

    <view v-else class="rank-list">
      <template v-if="activeTab === 'notes'">
        <view v-for="item in weeklyItems" :key="item.noteId" class="note-rank-card panel" @tap="openNote(item.noteId)">
          <view :class="rankClass(item.rankNo)">{{ item.rankNo }}</view>
          <image v-if="item.coverUrl" class="note-cover" :src="item.coverUrl" mode="aspectFill" />
          <view v-else class="note-cover fallback-cover">BN</view>
          <view class="note-info">
            <view class="note-title">{{ item.title || '未命名笔记' }}</view>
            <view class="note-author">{{ item.authorNickname || 'BlueNote 用户' }} · {{ formatTime(item.publishedAt) }}</view>
            <view class="note-stats">
              <text>热度 {{ statText(item.score) }}</text>
              <text>赞 {{ statText(item.counts.likeCount) }}</text>
              <text>评 {{ statText(item.counts.commentCount) }}</text>
              <text>藏 {{ statText(item.counts.collectCount) }}</text>
            </view>
          </view>
        </view>
      </template>

      <template v-else>
        <view v-for="item in creatorItems" :key="item.creatorId" class="creator-rank-card panel">
          <view :class="rankClass(item.rankNo)">{{ item.rankNo }}</view>
          <AvatarCircle :src="item.avatarUrl" :name="item.nickname" size="medium" />
          <view class="creator-info">
            <view class="creator-name">{{ item.nickname || 'BlueNote 用户' }}</view>
            <view class="creator-bio">{{ item.bio || '持续发布公开笔记的创作者' }}</view>
            <view class="creator-stats">
              <text>成长 {{ statText(item.score) }}</text>
              <text>作品 {{ statText(item.stats.publicNoteCount) }}</text>
              <text>粉丝 {{ statText(item.stats.followerCount) }}</text>
              <text>获赞 {{ statText(item.stats.likedCount) }}</text>
            </view>
          </view>
        </view>
      </template>

      <button v-if="currentHasMore" class="load-more-button" :disabled="loadingMore" @tap="loadMore">
        {{ loadingMore ? '加载中' : '加载更多' }}
      </button>
      <view v-else-if="currentLoaded" class="list-end">没有更多了</view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.rank-screen {
  padding-left: 22rpx;
  padding-right: 22rpx;
}

.rank-appbar {
  display: grid;
  grid-template-columns: 68rpx 1fr 96rpx;
  align-items: center;
  min-height: 76rpx;
}

.back-button,
.refresh-button {
  min-height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--bn-ink);
  background: #fff;
  border-radius: 16rpx;
  box-shadow: var(--bn-shadow-soft);
}

.back-button {
  font-size: 46rpx;
}

.refresh-button {
  font-size: 24rpx;
  color: var(--bn-muted);
  padding: 0;
  line-height: 1;
  white-space: nowrap;
}

.page-title {
  text-align: center;
  font-size: 32rpx;
  font-weight: 860;
}

.rank-summary {
  margin-top: 22rpx;
  min-height: 168rpx;
  padding: 26rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20rpx;
  background:
    linear-gradient(135deg, rgba(255, 95, 87, 0.92), rgba(24, 170, 152, 0.82)),
    #fff;
  color: #fff;
  box-shadow: 0 16rpx 34rpx rgba(24, 100, 120, 0.16);
}

.summary-title {
  font-size: 26rpx;
  font-weight: 760;
  opacity: 0.88;
}

.summary-value {
  margin-top: 8rpx;
  font-size: 58rpx;
  font-weight: 900;
  line-height: 1;
}

.summary-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10rpx;
}

.summary-label {
  min-height: 42rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.18);
  font-size: 22rpx;
  white-space: nowrap;
}

.rank-tabs {
  margin-top: 20rpx;
  padding: 8rpx;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8rpx;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: var(--bn-shadow-soft);
}

.rank-tab {
  min-height: 68rpx;
  border-radius: 12rpx;
  color: var(--bn-muted);
  font-size: 26rpx;
  font-weight: 760;
}

.rank-tab.active {
  color: var(--bn-ink);
  background: #f3f5f6;
}

.my-rank-card {
  margin-top: 18rpx;
  padding: 22rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  box-shadow: var(--bn-shadow-soft);
}

.my-rank-title {
  color: var(--bn-ink);
  font-size: 27rpx;
  font-weight: 820;
}

.my-rank-subtitle {
  margin-top: 8rpx;
  color: var(--bn-muted);
  font-size: 22rpx;
}

.my-rank-pill {
  flex: 0 0 auto;
  min-width: 140rpx;
  min-height: 56rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  color: #d54b43;
  background: rgba(255, 95, 87, 0.1);
  font-size: 23rpx;
  font-weight: 780;
}

.rank-list {
  margin-top: 18rpx;
  padding-bottom: 44rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.note-rank-card,
.creator-rank-card {
  min-height: 152rpx;
  padding: 18rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
  box-shadow: var(--bn-shadow-soft);
}

.rank-no {
  flex: 0 0 54rpx;
  width: 54rpx;
  height: 54rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #68707a;
  background: #f2f4f5;
  font-size: 24rpx;
  font-weight: 860;
}

.rank-top-1 {
  color: #9f5b00;
  background: #fff0bf;
}

.rank-top-2 {
  color: #526071;
  background: #edf1f5;
}

.rank-top-3 {
  color: #8b5436;
  background: #ffe5d5;
}

.note-cover {
  flex: 0 0 118rpx;
  width: 118rpx;
  height: 118rpx;
  border-radius: 14rpx;
  overflow: hidden;
  background: #eef1f2;
}

.fallback-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: linear-gradient(135deg, var(--bn-coral), var(--bn-blue));
  font-size: 30rpx;
  font-weight: 860;
}

.note-info,
.creator-info {
  flex: 1;
  min-width: 0;
}

.note-title,
.creator-name {
  color: var(--bn-ink);
  font-size: 28rpx;
  font-weight: 820;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-author,
.creator-bio {
  margin-top: 8rpx;
  color: var(--bn-muted);
  font-size: 22rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-stats,
.creator-stats {
  margin-top: 14rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.note-stats text,
.creator-stats text {
  min-height: 38rpx;
  padding: 0 12rpx;
  border-radius: 999rpx;
  display: flex;
  align-items: center;
  color: #5c626a;
  background: #f4f6f7;
  font-size: 20rpx;
}

.load-more-button {
  min-height: 76rpx;
  border-radius: 16rpx;
  color: var(--bn-muted);
  background: #fff;
  box-shadow: var(--bn-shadow-soft);
  font-size: 25rpx;
}

.list-end {
  padding: 24rpx 0 6rpx;
  color: var(--bn-faint);
  text-align: center;
  font-size: 23rpx;
}

.empty-action {
  min-width: 220rpx;
  margin-top: 8rpx;
}
</style>
