<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onReachBottom } from '@dcloudio/uni-app'
import {
  createNoteComment,
  deleteComment as deleteCommentApi,
  getCommentReplies,
  getNoteComments,
  likeComment,
  replyToComment,
  unlikeComment
} from '@/api/comment'
import {
  collectNote as collectNoteApi,
  getNoteDetail,
  likeNote as likeNoteApi,
  uncollectNote,
  unlikeNote
} from '@/api/note'
import { BlueNoteApiError, showApiError } from '@/api/request'
import type { CommentItem, NoteDetail } from '@/api/types'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import { createClientRequestId, formatCount, formatTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

interface ReplyTarget {
  commentId: string
  rootId: string
  nickname: string
}

interface ReplyState {
  items: CommentItem[]
  nextCursor: string | null
  hasMore: boolean
  loading: boolean
  loaded: boolean
  errorText: string
}

const auth = useAuthStore()
const noteId = ref('')
const note = ref<NoteDetail | null>(null)
const loading = ref(false)
const errorText = ref('')
const currentMediaIndex = ref(0)
const comments = ref<CommentItem[]>([])
const commentsLoading = ref(false)
const commentsLoaded = ref(false)
const commentsErrorText = ref('')
const commentsNextCursor = ref<string | null>(null)
const commentsHasMore = ref(false)
const commentsDegraded = ref(false)
const commentDraft = ref('')
const commentSubmitting = ref(false)
const commentBlocked = ref(false)
const commentCount = ref(0)
const inputFocus = ref(false)
const replyTarget = ref<ReplyTarget | null>(null)
const replyStates = ref<Record<string, ReplyState>>({})
const expandedRootIds = ref<string[]>([])
const noteActionSubmitting = ref({
  like: false,
  collect: false
})

const coverMedia = computed(() => note.value?.mediaFiles ?? [])
const mediaTotal = computed(() => coverMedia.value.length)
const searchKeyword = computed(() => {
  const firstTopic = note.value?.topics?.[0]
  return firstTopic ? `${firstTopic}相关笔记` : note.value?.title || '相似笔记'
})
const canWriteComment = computed(() => Boolean(note.value?.commentEnabled) && !commentBlocked.value)
const commentPlaceholder = computed(() => {
  if (!canWriteComment.value) {
    return '这篇笔记暂时不能评论'
  }
  if (!auth.isAuthenticated) {
    return '登录后参与讨论'
  }
  return replyTarget.value ? `回复 ${replyTarget.value.nickname}` : '说点什么...'
})
const canSubmitComment = computed(() => {
  return canWriteComment.value && Boolean(commentDraft.value.trim()) && !commentSubmitting.value
})

onLoad((options) => {
  noteId.value = String(options?.noteId ?? '')
  void loadDetail()
})

onReachBottom(() => {
  if (commentsLoaded.value && commentsHasMore.value) {
    void loadComments(false)
  }
})

async function loadDetail() {
  if (!noteId.value) {
    errorText.value = '笔记不存在'
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    note.value = await getNoteDetail(noteId.value, auth.isAuthenticated)
    commentCount.value = note.value.counts.commentCount
    commentBlocked.value = !note.value.commentEnabled
    currentMediaIndex.value = 0
    await loadComments(true)
  } catch (error) {
    errorText.value = '笔记暂时不可见'
    showApiError(error, '笔记暂时不可见')
  } finally {
    loading.value = false
  }
}

async function loadComments(reset = false) {
  if (!noteId.value || commentsLoading.value) {
    return
  }
  if (!reset && commentsLoaded.value && !commentsHasMore.value) {
    return
  }
  commentsLoading.value = true
  commentsErrorText.value = ''
  try {
    const page = await getNoteComments(
      noteId.value,
      reset ? null : commentsNextCursor.value,
      10,
      auth.isAuthenticated
    )
    comments.value = reset ? page.items : mergeComments(comments.value, page.items)
    commentsNextCursor.value = page.nextCursor
    commentsHasMore.value = page.hasMore
    commentsDegraded.value = page.degraded
    commentsLoaded.value = true
    syncVisibleCommentCount()
  } catch (error) {
    commentsErrorText.value = '评论加载失败'
    if (isCommentBlocked(error)) {
      commentBlocked.value = true
    } else {
      showApiError(error, '评论加载失败')
    }
  } finally {
    commentsLoading.value = false
  }
}

async function refreshComments() {
  await loadComments(true)
}

function focusCommentInput() {
  if (!canWriteComment.value) {
    uni.showToast({ title: '这篇笔记暂时不能评论', icon: 'none' })
    return
  }
  if (!ensureAuthenticated()) {
    return
  }
  inputFocus.value = false
  setTimeout(() => {
    inputFocus.value = true
  }, 40)
}

function handleComposerFocus() {
  if (!auth.isAuthenticated) {
    inputFocus.value = false
    ensureAuthenticated()
  }
}

async function submitComment() {
  if (!canWriteComment.value) {
    uni.showToast({ title: '这篇笔记暂时不能评论', icon: 'none' })
    return
  }
  if (!ensureAuthenticated()) {
    return
  }
  const content = commentDraft.value.trim()
  if (!content || commentSubmitting.value) {
    return
  }
  commentSubmitting.value = true
  const target = replyTarget.value
  const clientRequestId = createClientRequestId(target ? 'reply' : 'comment')
  try {
    if (target) {
      await replyToComment(target.commentId, { clientRequestId, content }, clientRequestId)
      commentDraft.value = ''
      replyTarget.value = null
      commentCount.value += 1
      ensureExpanded(target.rootId)
      patchComment(target.rootId, (comment) => {
        comment.replyCount += 1
      })
      await loadReplies(target.rootId, true)
    } else {
      await createNoteComment(noteId.value, { clientRequestId, content }, clientRequestId)
      commentDraft.value = ''
      commentCount.value += 1
      await loadComments(true)
    }
  } catch (error) {
    if (isCommentBlocked(error)) {
      commentBlocked.value = true
    }
    showApiError(error, '评论发送失败')
  } finally {
    commentSubmitting.value = false
  }
}

function prepareReply(comment: CommentItem) {
  if (!ensureAuthenticated()) {
    return
  }
  replyTarget.value = {
    commentId: comment.commentId,
    rootId: comment.rootId,
    nickname: comment.author.nickname || '用户'
  }
  focusCommentInput()
}

function cancelReply() {
  replyTarget.value = null
}

async function toggleReplies(comment: CommentItem) {
  const rootId = comment.rootId
  if (isRepliesExpanded(rootId)) {
    expandedRootIds.value = expandedRootIds.value.filter((item) => item !== rootId)
    return
  }
  ensureExpanded(rootId)
  if (!replyStateOf(rootId).loaded) {
    await loadReplies(rootId, true)
  }
}

async function loadReplies(rootId: string, reset = false) {
  const state = replyStateOf(rootId)
  if (state.loading || (!reset && state.loaded && !state.hasMore)) {
    return
  }
  state.loading = true
  state.errorText = ''
  try {
    const page = await getCommentReplies(rootId, reset ? null : state.nextCursor, 10, auth.isAuthenticated)
    state.items = reset ? page.items : mergeComments(state.items, page.items)
    state.nextCursor = page.nextCursor
    state.hasMore = page.hasMore
    state.loaded = true
  } catch (error) {
    state.errorText = '回复加载失败'
    showApiError(error, '回复加载失败')
  } finally {
    state.loading = false
  }
}

async function toggleCommentLike(comment: CommentItem) {
  if (!ensureAuthenticated()) {
    return
  }
  const wasLiked = comment.viewerAction.liked
  patchComment(comment.commentId, (item) => {
    item.viewerAction.liked = !wasLiked
    item.likeCount = Math.max(0, item.likeCount + (wasLiked ? -1 : 1))
  })
  try {
    const response = wasLiked ? await unlikeComment(comment.commentId) : await likeComment(comment.commentId)
    patchComment(comment.commentId, (item) => {
      item.viewerAction.liked = response.liked
    })
  } catch (error) {
    patchComment(comment.commentId, (item) => {
      item.viewerAction.liked = wasLiked
      item.likeCount = Math.max(0, item.likeCount + (wasLiked ? 1 : -1))
    })
    showApiError(error, '操作失败')
  }
}

function deleteCommentItem(comment: CommentItem) {
  if (!ensureAuthenticated()) {
    return
  }
  uni.showModal({
    title: '删除评论',
    content: '确定删除这条评论吗？',
    confirmText: '删除',
    success: (result) => {
      if (result.confirm) {
        void performDeleteComment(comment)
      }
    }
  })
}

async function performDeleteComment(comment: CommentItem) {
  try {
    await deleteCommentApi(comment.commentId)
    if (comment.level === 1) {
      comments.value = comments.value.filter((item) => item.commentId !== comment.commentId)
      delete replyStates.value[comment.rootId]
      expandedRootIds.value = expandedRootIds.value.filter((item) => item !== comment.rootId)
      commentCount.value = Math.max(0, commentCount.value - Math.max(1, comment.replyCount + 1))
    } else {
      const state = replyStates.value[comment.rootId]
      if (state) {
        state.items = state.items.filter((item) => item.commentId !== comment.commentId)
      }
      patchComment(comment.rootId, (item) => {
        item.replyCount = Math.max(0, item.replyCount - 1)
      })
      commentCount.value = Math.max(0, commentCount.value - 1)
    }
    uni.showToast({ title: '已删除', icon: 'none' })
  } catch (error) {
    showApiError(error, '删除失败')
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

function openAuthor() {
  if (!note.value) {
    return
  }
  uni.switchTab({ url: '/pages/profile/index' })
}

function handleSwiperChange(event: Event) {
  const detail = (event as unknown as { detail?: { current?: number } }).detail
  currentMediaIndex.value = Number(detail?.current ?? 0)
}

function followAuthor() {
  uni.showToast({ title: '关注功能后续接入', icon: 'none' })
}

function shareNote() {
  uni.showToast({ title: '分享功能后续接入', icon: 'none' })
}

async function toggleNoteLike() {
  if (!note.value || noteActionSubmitting.value.like || !ensureAuthenticated()) {
    return
  }
  const wasLiked = note.value.viewerAction.liked
  noteActionSubmitting.value.like = true
  patchNoteInteraction('liked', !wasLiked, wasLiked ? -1 : 1)
  try {
    const response = wasLiked ? await unlikeNote(note.value.noteId) : await likeNoteApi(note.value.noteId)
    patchNoteInteraction('liked', response.liked, 0)
  } catch (error) {
    patchNoteInteraction('liked', wasLiked, wasLiked ? 1 : -1)
    showApiError(error, '操作失败')
  } finally {
    noteActionSubmitting.value.like = false
  }
}

async function toggleNoteCollect() {
  if (!note.value || noteActionSubmitting.value.collect || !ensureAuthenticated()) {
    return
  }
  const wasCollected = note.value.viewerAction.collected
  noteActionSubmitting.value.collect = true
  patchNoteInteraction('collected', !wasCollected, wasCollected ? -1 : 1)
  try {
    const response = wasCollected ? await uncollectNote(note.value.noteId) : await collectNoteApi(note.value.noteId)
    patchNoteInteraction('collected', response.collected, 0)
  } catch (error) {
    patchNoteInteraction('collected', wasCollected, wasCollected ? 1 : -1)
    showApiError(error, '操作失败')
  } finally {
    noteActionSubmitting.value.collect = false
  }
}

function scrollToComments() {
  uni.pageScrollTo({ selector: '.comments-section', duration: 220 })
}

function isMyComment(comment: CommentItem) {
  return Boolean(auth.userId && comment.author.userId === auth.userId)
}

function isRepliesExpanded(rootId: string) {
  return expandedRootIds.value.includes(rootId)
}

function replyItems(rootId: string) {
  return replyStates.value[rootId]?.items ?? []
}

function repliesLoading(rootId: string) {
  return Boolean(replyStates.value[rootId]?.loading)
}

function repliesError(rootId: string) {
  return replyStates.value[rootId]?.errorText ?? ''
}

function repliesHasMore(rootId: string) {
  return Boolean(replyStates.value[rootId]?.hasMore)
}

function ensureExpanded(rootId: string) {
  if (!expandedRootIds.value.includes(rootId)) {
    expandedRootIds.value = [...expandedRootIds.value, rootId]
  }
}

function replyStateOf(rootId: string) {
  if (!replyStates.value[rootId]) {
    replyStates.value[rootId] = {
      items: [],
      nextCursor: null,
      hasMore: false,
      loading: false,
      loaded: false,
      errorText: ''
    }
  }
  return replyStates.value[rootId]
}

function patchComment(commentId: string, updater: (comment: CommentItem) => void) {
  for (const item of comments.value) {
    if (item.commentId === commentId) {
      updater(item)
    }
  }
  for (const state of Object.values(replyStates.value)) {
    for (const item of state.items) {
      if (item.commentId === commentId) {
        updater(item)
      }
    }
  }
}

function patchNoteInteraction(field: 'liked' | 'collected', value: boolean, countDelta: number) {
  if (!note.value) {
    return
  }
  note.value.viewerAction[field] = value
  if (field === 'liked') {
    note.value.counts.likeCount = Math.max(0, note.value.counts.likeCount + countDelta)
  } else {
    note.value.counts.collectCount = Math.max(0, note.value.counts.collectCount + countDelta)
  }
}

function mergeComments(current: CommentItem[], incoming: CommentItem[]) {
  const exists = new Set(current.map((item) => item.commentId))
  return [...current, ...incoming.filter((item) => !exists.has(item.commentId))]
}

function syncVisibleCommentCount() {
  const visibleCount = comments.value.reduce((sum, item) => sum + 1 + item.replyCount, 0)
  commentCount.value = Math.max(commentCount.value, visibleCount)
}

function ensureAuthenticated() {
  if (auth.isAuthenticated) {
    return true
  }
  uni.navigateTo({ url: '/pages/login/index' })
  return false
}

function isCommentBlocked(error: unknown) {
  return error instanceof BlueNoteApiError && (error.code === 24006 || error.reason === 'COMMENT_NOTE_NOT_ALLOWED')
}
</script>

<template>
  <view class="detail-screen">
    <view v-if="loading" class="loading-copy">正在打开笔记</view>

    <EmptyState v-else-if="errorText" title="笔记暂时不可见" subtitle="可能已删除、私密，或本地服务没有启动。">
      <button class="secondary-button retry-button" @tap="loadDetail">重新加载</button>
    </EmptyState>

    <view v-else-if="note" class="detail-content">
      <view class="detail-topbar">
        <button class="back-button" @tap="back">‹</button>
        <view class="top-author" @tap="openAuthor">
          <AvatarCircle :src="note.author.avatarUrl" :name="note.author.nickname" size="small" />
          <view class="top-name">{{ note.author.nickname }}</view>
        </view>
        <button class="follow-button" @tap="followAuthor">关注</button>
        <button class="share-button" @tap="shareNote">↗</button>
      </view>

      <view class="gallery-section">
        <view class="gallery-wrap">
          <swiper v-if="coverMedia.length" class="media-swiper" circular @change="handleSwiperChange">
            <swiper-item v-for="media in coverMedia" :key="media.fileId">
              <image class="detail-image" :src="media.accessUrl" mode="aspectFill" />
            </swiper-item>
          </swiper>
          <view v-else class="media-fallback">
            <text>BlueNote</text>
          </view>
          <view v-if="mediaTotal > 1" class="media-count">{{ currentMediaIndex + 1 }}/{{ mediaTotal }}</view>
        </view>
        <view v-if="mediaTotal > 1" class="dot-row">
          <view
            v-for="(_, index) in coverMedia"
            :key="index"
            class="dot"
            :class="{ active: currentMediaIndex === index }"
          />
        </view>
      </view>

      <view class="article-section">
        <view class="title">{{ note.title }}</view>
        <view class="content">{{ note.content }}</view>

        <view v-if="note.topics.length" class="topic-list">
          <view v-for="topic in note.topics" :key="topic" class="topic-chip">#{{ topic }}</view>
        </view>

        <view class="search-suggestion">
          <text class="search-icon">⌕</text>
          <text>猜你想搜｜{{ searchKeyword }}</text>
        </view>

        <view class="note-meta-row">
          <text>{{ formatTime(note.publishedAt) }} · {{ note.visibility === 'PUBLIC' ? '公开' : '私密' }}</text>
          <button class="dislike-button">不喜欢</button>
        </view>

        <view v-if="note.degraded" class="degraded-chip">部分信息降级展示</view>
      </view>

      <view class="comments-section">
        <view class="comments-title-row">
          <view class="comments-title">评论 {{ formatCount(commentCount) }}</view>
          <button class="comments-refresh" :disabled="commentsLoading" @tap="refreshComments">刷新</button>
        </view>

        <view v-if="commentsLoading && !commentsLoaded" class="comments-loading">正在加载评论</view>

        <view v-else-if="commentsErrorText" class="comments-error">
          <text>{{ commentsErrorText }}</text>
          <button class="secondary-button comments-retry" @tap="refreshComments">重新加载</button>
        </view>

        <view v-else-if="commentsLoaded && !comments.length" class="comments-empty">
          <view class="comments-empty-title">还没有评论</view>
          <view class="comments-empty-copy">来做第一个认真回应的人。</view>
        </view>

        <view v-else class="comment-list">
          <view v-for="comment in comments" :key="comment.commentId" class="comment-item">
            <AvatarCircle :src="comment.author.avatarUrl" :name="comment.author.nickname" size="small" />
            <view class="comment-main">
              <view class="comment-head">
                <text class="comment-name">{{ comment.author.nickname }}</text>
                <text class="comment-time">{{ formatTime(comment.createdAt) }}</text>
              </view>
              <view class="comment-copy">{{ comment.content }}</view>
              <view v-if="comment.degraded" class="comment-degraded">部分评论信息暂时不完整</view>

              <view class="comment-tools">
                <button class="comment-tool" @tap="prepareReply(comment)">回复</button>
                <button v-if="isMyComment(comment)" class="comment-tool" @tap="deleteCommentItem(comment)">删除</button>
                <button
                  class="comment-like"
                  :class="{ liked: comment.viewerAction.liked }"
                  @tap="toggleCommentLike(comment)"
                >
                  <text>{{ comment.viewerAction.liked ? '♥' : '♡' }}</text>
                  <text>{{ formatCount(comment.likeCount) }}</text>
                </button>
              </view>

              <button v-if="comment.replyCount" class="reply-toggle" @tap="toggleReplies(comment)">
                {{ isRepliesExpanded(comment.rootId) ? '收起回复' : `查看 ${formatCount(comment.replyCount)} 条回复` }}
              </button>

              <view v-if="isRepliesExpanded(comment.rootId)" class="reply-list">
                <view v-if="repliesLoading(comment.rootId) && !replyItems(comment.rootId).length" class="reply-loading">
                  正在加载回复
                </view>
                <view v-else-if="repliesError(comment.rootId)" class="reply-error">
                  <text>{{ repliesError(comment.rootId) }}</text>
                  <button class="reply-more" @tap="loadReplies(comment.rootId, true)">重试</button>
                </view>
                <view v-for="reply in replyItems(comment.rootId)" :key="reply.commentId" class="reply-item">
                  <AvatarCircle :src="reply.author.avatarUrl" :name="reply.author.nickname" size="small" />
                  <view class="reply-main">
                    <view class="comment-head">
                      <text class="comment-name">{{ reply.author.nickname }}</text>
                      <text v-if="reply.replyToUser" class="reply-to">回复 {{ reply.replyToUser.nickname }}</text>
                      <text class="comment-time">{{ formatTime(reply.createdAt) }}</text>
                    </view>
                    <view class="comment-copy">{{ reply.content }}</view>
                    <view class="comment-tools">
                      <button class="comment-tool" @tap="prepareReply(reply)">回复</button>
                      <button v-if="isMyComment(reply)" class="comment-tool" @tap="deleteCommentItem(reply)">删除</button>
                      <button
                        class="comment-like"
                        :class="{ liked: reply.viewerAction.liked }"
                        @tap="toggleCommentLike(reply)"
                      >
                        <text>{{ reply.viewerAction.liked ? '♥' : '♡' }}</text>
                        <text>{{ formatCount(reply.likeCount) }}</text>
                      </button>
                    </view>
                  </view>
                </view>
                <button v-if="repliesHasMore(comment.rootId)" class="reply-more" @tap="loadReplies(comment.rootId)">
                  加载更多回复
                </button>
              </view>
            </view>
          </view>

          <button v-if="commentsHasMore" class="comments-more" :disabled="commentsLoading" @tap="loadComments(false)">
            {{ commentsLoading ? '加载中' : '加载更多评论' }}
          </button>
        </view>

        <view v-if="commentsDegraded" class="degraded-chip comments-degraded">评论信息暂时不完整，可稍后刷新</view>
      </view>

      <view class="detail-action-bar" :class="{ composing: commentDraft.trim() || replyTarget }">
        <view v-if="replyTarget" class="reply-target-row">
          <text>正在回复 {{ replyTarget.nickname }}</text>
          <button class="cancel-reply" @tap="cancelReply">取消</button>
        </view>
        <view class="action-main-row">
          <view class="comment-entry" @tap="focusCommentInput">
          <text class="comment-pencil">✎</text>
            <input
              v-model="commentDraft"
              class="comment-input"
              :focus="inputFocus"
              :disabled="!canWriteComment || commentSubmitting"
              :placeholder="commentPlaceholder"
              confirm-type="send"
              @focus="handleComposerFocus"
              @confirm="submitComment"
            />
          </view>
          <button
            v-if="commentDraft.trim() || replyTarget"
            class="send-comment"
            :disabled="!canSubmitComment"
            @tap="submitComment"
          >
            {{ commentSubmitting ? '发送中' : '发送' }}
          </button>
          <view v-else class="action-group">
            <button
              class="action-item"
              :class="{ active: note.viewerAction.liked }"
              :disabled="noteActionSubmitting.like"
              @tap="toggleNoteLike"
            >
              <text class="action-icon">{{ note.viewerAction.liked ? '♥' : '♡' }}</text>
              <text>{{ formatCount(note.counts.likeCount) }}</text>
            </button>
            <button
              class="action-item"
              :class="{ active: note.viewerAction.collected }"
              :disabled="noteActionSubmitting.collect"
              @tap="toggleNoteCollect"
            >
              <text class="action-icon">{{ note.viewerAction.collected ? '★' : '☆' }}</text>
              <text>{{ formatCount(note.counts.collectCount) }}</text>
            </button>
            <button class="action-item" @tap="scrollToComments">
            <text class="action-icon">◌</text>
              <text>{{ formatCount(commentCount) }}</text>
          </button>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.detail-screen {
  min-height: 100vh;
  background: #fff;
}

.detail-content {
  min-height: 100vh;
  padding-bottom: 150rpx;
  background: #fff;
}

.detail-topbar {
  position: sticky;
  top: 0;
  z-index: 9;
  min-height: 104rpx;
  padding: env(safe-area-inset-top) 24rpx 14rpx;
  background: rgba(255, 255, 255, 0.98);
  border-bottom: 1rpx solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.back-button,
.share-button {
  flex: 0 0 auto;
  width: 62rpx;
  height: 62rpx;
  border-radius: 50%;
  color: #1d2026;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
}

.share-button {
  font-size: 38rpx;
}

.top-author {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.top-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #1d2026;
  font-size: 30rpx;
  font-weight: 760;
}

.follow-button {
  flex: 0 0 auto;
  width: 124rpx;
  height: 60rpx;
  border-radius: 999rpx;
  border: 1rpx solid rgba(255, 95, 87, 0.52);
  color: var(--bn-coral);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 760;
  line-height: 1;
  white-space: nowrap;
}

.gallery-section {
  background: #fff;
  padding: 22rpx 24rpx 0;
}

.gallery-wrap {
  position: relative;
  width: 100%;
  overflow: hidden;
  border-radius: 8rpx;
  background: #fff;
}

.media-swiper,
.media-fallback {
  width: 100%;
  height: 660rpx;
  background: #fff;
}

.detail-image {
  width: 100%;
  height: 100%;
}

.media-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 50rpx;
  font-weight: 800;
  background: linear-gradient(135deg, var(--bn-coral), var(--bn-blue), var(--bn-teal));
}

.media-count {
  position: absolute;
  top: 20rpx;
  right: 20rpx;
  min-width: 72rpx;
  height: 58rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: rgba(17, 19, 24, 0.48);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 760;
  backdrop-filter: blur(10rpx);
}

.dot-row {
  height: 58rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
}

.dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #d7d7d7;
}

.dot.active {
  background: var(--bn-coral);
}

.article-section {
  padding: 30rpx 32rpx 36rpx;
  background: #fff;
}

.degraded-chip {
  display: inline-flex;
  margin-top: 22rpx;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(246, 200, 76, 0.18);
  color: #9a6b00;
  font-size: 22rpx;
}

.title {
  margin-top: 0;
  color: var(--bn-ink);
  font-size: 34rpx;
  font-weight: 780;
  line-height: 1.42;
}

.content {
  margin-top: 18rpx;
  color: #2b2f36;
  font-size: 31rpx;
  line-height: 1.72;
  white-space: pre-wrap;
}

.topic-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 24rpx;
}

.topic-chip {
  min-height: 42rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 95, 87, 0.08);
  color: #d94e47;
  display: flex;
  align-items: center;
  font-size: 23rpx;
}

.search-suggestion {
  max-width: 100%;
  min-height: 66rpx;
  margin-top: 28rpx;
  padding: 0 22rpx;
  border: 1rpx solid #ececec;
  border-radius: 10rpx;
  background: #fff;
  color: #3a3f46;
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  font-size: 27rpx;
  box-shadow: 0 4rpx 14rpx rgba(18, 22, 28, 0.04);
}

.search-icon {
  color: #1d2026;
  font-size: 36rpx;
  font-weight: 800;
}

.note-meta-row {
  margin-top: 28rpx;
  color: var(--bn-muted);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  font-size: 25rpx;
}

.dislike-button {
  flex: 0 0 auto;
  height: 52rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  border: 1rpx solid #e8e8e8;
  color: #4c5158;
  background: #fff;
  font-size: 24rpx;
}

.comments-section {
  padding: 10rpx 32rpx 170rpx;
  background: #fff;
  border-top: 14rpx solid #f7f7f8;
}

.comments-title-row {
  min-height: 78rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.comments-title {
  color: var(--bn-ink);
  font-size: 31rpx;
  font-weight: 860;
}

.comments-refresh {
  flex: 0 0 auto;
  height: 52rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #f5f6f7;
  color: #636872;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 23rpx;
}

.comments-loading,
.comments-empty,
.comments-error {
  min-height: 200rpx;
  color: var(--bn-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  font-size: 25rpx;
}

.comments-empty-title {
  color: #272b31;
  font-size: 28rpx;
  font-weight: 800;
}

.comments-empty-copy {
  color: var(--bn-muted);
  font-size: 24rpx;
}

.comments-retry {
  width: 210rpx;
  height: 58rpx;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 30rpx;
}

.comment-item,
.reply-item {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
}

.comment-main,
.reply-main {
  flex: 1;
  min-width: 0;
}

.comment-head {
  min-height: 40rpx;
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.comment-name {
  min-width: 0;
  max-width: 300rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #5b616a;
  font-size: 24rpx;
  font-weight: 720;
}

.comment-time,
.reply-to {
  flex: 0 0 auto;
  color: #9a9fa7;
  font-size: 22rpx;
}

.comment-copy {
  margin-top: 8rpx;
  color: #22262c;
  font-size: 29rpx;
  line-height: 1.56;
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-degraded {
  display: inline-flex;
  margin-top: 12rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(246, 200, 76, 0.16);
  color: #9a6b00;
  font-size: 21rpx;
}

.comment-tools {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.comment-tool,
.comment-like {
  flex: 0 0 auto;
  height: 42rpx;
  color: #8a9099;
  display: flex;
  align-items: center;
  gap: 6rpx;
  font-size: 22rpx;
}

.comment-like {
  margin-left: auto;
  min-width: 72rpx;
  justify-content: flex-end;
}

.comment-like.liked {
  color: var(--bn-coral);
}

.reply-toggle {
  height: 48rpx;
  margin-top: 12rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #f5f6f7;
  color: #5d6470;
  display: inline-flex;
  align-items: center;
  font-size: 22rpx;
  font-weight: 720;
}

.reply-list {
  margin-top: 18rpx;
  padding: 18rpx;
  border-radius: 8rpx;
  background: #f8f9fa;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.reply-loading,
.reply-error {
  min-height: 68rpx;
  color: var(--bn-muted);
  display: flex;
  align-items: center;
  gap: 16rpx;
  font-size: 23rpx;
}

.reply-more,
.comments-more {
  height: 58rpx;
  border-radius: 999rpx;
  background: #f4f5f6;
  color: #5e646d;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 23rpx;
  font-weight: 720;
}

.comments-more {
  margin: 8rpx auto 0;
  width: 260rpx;
}

.comments-degraded {
  margin-top: 28rpx;
}

.detail-action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 10;
  min-height: 112rpx;
  padding: 14rpx 24rpx calc(14rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1rpx solid var(--bn-line);
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 10rpx;
  backdrop-filter: blur(18rpx);
}

.action-main-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.reply-target-row {
  min-height: 42rpx;
  color: #737982;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  font-size: 23rpx;
}

.cancel-reply {
  flex: 0 0 auto;
  height: 40rpx;
  color: var(--bn-coral);
  display: flex;
  align-items: center;
  font-size: 23rpx;
}

.comment-entry {
  flex: 1;
  min-width: 0;
  height: 70rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: #f3f4f5;
  color: var(--bn-muted);
  display: flex;
  align-items: center;
  gap: 12rpx;
  font-size: 24rpx;
}

.comment-input {
  flex: 1;
  min-width: 0;
  height: 70rpx;
  color: #262a31;
  font-size: 25rpx;
}

.comment-pencil {
  flex: 0 0 auto;
  font-size: 30rpx;
}

.send-comment {
  flex: 0 0 auto;
  width: 100rpx;
  height: 66rpx;
  border-radius: 999rpx;
  background: var(--bn-coral);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 800;
}

.send-comment[disabled] {
  background: #d8dbe0;
  color: #fff;
}

.action-group {
  flex: 0 0 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8rpx;
}

.action-item {
  flex: 0 0 auto;
  height: 70rpx;
  min-width: 70rpx;
  border-radius: 999rpx;
  color: var(--bn-ink);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 24rpx;
  font-weight: 760;
}

.action-item.active {
  color: var(--bn-coral);
}

.action-item[disabled] {
  opacity: 0.58;
}

.action-icon {
  font-size: 44rpx;
  line-height: 1;
}

.retry-button {
  width: 240rpx;
  margin-top: 12rpx;
}
</style>
