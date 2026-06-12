<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { onLoad, onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import {
  createSingleConversation,
  getImMessages,
  markImConversationRead,
  markImConversationReceived,
  sendImMessage
} from '@/api/im'
import { showApiError } from '@/api/request'
import type { ImConversationItem, ImMessageItem } from '@/api/types'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { useImStore } from '@/stores/im'

const auth = useAuthStore()
const im = useImStore()
const conversationId = ref('')
const targetUserId = ref('')
const title = ref('私信')
const messages = ref<ImMessageItem[]>([])
const beforeSeq = ref<number | null>(null)
const hasMoreHistory = ref(false)
const loading = ref(false)
const sending = ref(false)
const errorText = ref('')
const draft = ref('')
const scrollIntoView = ref('')
const peer = ref<ImConversationItem['peerUser']>(null)

const canSend = computed(() => draft.value.trim().length > 0 && !sending.value && auth.isAuthenticated)

onLoad((options) => {
  conversationId.value = String(options?.conversationId ?? '')
  targetUserId.value = String(options?.targetUserId ?? '')
  const incomingTitle = String(options?.title ?? '')
  if (incomingTitle) {
    title.value = decodeURIComponent(incomingTitle)
  }
})

onShow(() => {
  if (!auth.isAuthenticated) {
    return
  }
  if (!conversationId.value && targetUserId.value) {
    void ensureConversation()
    return
  }
  if (conversationId.value && !messages.value.length && !loading.value) {
    void loadLatest()
  }
})

onPullDownRefresh(async () => {
  try {
    if (hasMoreHistory.value) {
      await loadHistory()
    } else {
      await loadLatest()
    }
  } finally {
    uni.stopPullDownRefresh()
  }
})

async function ensureConversation() {
  loading.value = true
  errorText.value = ''
  try {
    const conversation = await createSingleConversation(targetUserId.value)
    conversationId.value = conversation.conversationId
    peer.value = conversation.peerUser
    if (conversation.peerUser?.nickname) {
      title.value = conversation.peerUser.nickname
    }
    await loadLatest()
  } catch (error) {
    errorText.value = '会话加载失败'
    showApiError(error, '会话加载失败')
  } finally {
    loading.value = false
  }
}

async function loadLatest() {
  if (!conversationId.value || loading.value) {
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const page = await getImMessages(conversationId.value, { limit: 30 })
    messages.value = page.items
    beforeSeq.value = page.nextBeforeSeq
    hasMoreHistory.value = page.hasMore
    await markSeen()
    await scrollToBottom()
  } catch (error) {
    errorText.value = '消息加载失败'
    showApiError(error, '消息加载失败')
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  if (!conversationId.value || loading.value || beforeSeq.value == null) {
    return
  }
  loading.value = true
  try {
    const page = await getImMessages(conversationId.value, { beforeSeq: beforeSeq.value, limit: 30 })
    messages.value = mergeMessages(page.items, messages.value)
    beforeSeq.value = page.nextBeforeSeq
    hasMoreHistory.value = page.hasMore
  } catch (error) {
    showApiError(error, '历史消息加载失败')
  } finally {
    loading.value = false
  }
}

async function sendMessage() {
  const text = draft.value.trim()
  if (!canSend.value || !text) {
    return
  }
  sending.value = true
  try {
    const response = await sendImMessage({
      conversationId: conversationId.value || null,
      targetUserId: targetUserId.value || null,
      clientMsgId: clientMsgId(),
      text
    })
    conversationId.value = response.message.conversationId
    peer.value = response.conversation.peerUser
    draft.value = ''
    messages.value = mergeMessages(messages.value, [response.message])
    await markSeen()
    await scrollToBottom()
  } catch (error) {
    showApiError(error, '发送失败')
  } finally {
    sending.value = false
  }
}

async function markSeen() {
  if (!conversationId.value || !messages.value.length) {
    return
  }
  const latestSeq = messages.value[messages.value.length - 1]?.conversationSeq ?? 0
  if (latestSeq <= 0) {
    return
  }
  await markImConversationReceived(conversationId.value, latestSeq).catch(() => undefined)
  const response = await markImConversationRead(conversationId.value, latestSeq).catch(() => null)
  if (response) {
    im.applyUnread(response.totalUnread)
  } else {
    await im.refreshUnread().catch(() => undefined)
  }
}

async function scrollToBottom() {
  await nextTick()
  const last = messages.value[messages.value.length - 1]
  if (last) {
    scrollIntoView.value = `msg-${last.messageId}`
  }
}

function mergeMessages(left: ImMessageItem[], right: ImMessageItem[]) {
  const map = new Map<string, ImMessageItem>()
  for (const item of [...left, ...right]) {
    map.set(item.messageId, item)
  }
  return Array.from(map.values()).sort((a, b) => a.conversationSeq - b.conversationSeq)
}

function back() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.navigateTo({ url: '/pages/im/conversations' })
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function messageText(item: ImMessageItem) {
  const text = item.content?.text
  return typeof text === 'string' ? text : item.summary
}

function formatMessageTime(value: string) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function clientMsgId() {
  return `h5-${Date.now()}-${Math.random().toString(16).slice(2)}`
}
</script>

<template>
  <view class="chat-screen top-safe">
    <view class="chat-appbar">
      <button class="back-button" @tap="back">‹</button>
      <view class="chat-title">
        <text>{{ title }}</text>
        <text class="chat-subtitle">私信</text>
      </view>
      <view class="appbar-spacer"></view>
    </view>

    <EmptyState v-if="!auth.isAuthenticated" title="登录后发送私信" subtitle="和朋友继续聊。">
      <button class="primary-button empty-action" @tap="goLogin">去登录</button>
    </EmptyState>

    <EmptyState v-else-if="errorText" title="消息加载失败" subtitle="稍后刷新，或检查本地服务是否正在运行。">
      <button class="secondary-button empty-action" @tap="loadLatest">重新加载</button>
    </EmptyState>

    <scroll-view
      v-else
      class="message-scroll"
      scroll-y
      :scroll-into-view="scrollIntoView"
      scroll-with-animation
    >
      <button v-if="hasMoreHistory" class="history-button" :disabled="loading" @tap="loadHistory">
        {{ loading ? '加载中' : '查看更早消息' }}
      </button>
      <view v-if="loading && !messages.length" class="loading-copy">正在读取消息</view>
      <view v-else-if="!messages.length" class="empty-chat-copy">还没有消息</view>
      <view
        v-for="item in messages"
        :id="`msg-${item.messageId}`"
        :key="item.messageId"
        class="message-row"
        :class="{ mine: item.mine }"
      >
        <AvatarCircle v-if="!item.mine" :src="peer?.avatarUrl" :name="peer?.nickname || title" size="small" />
        <view class="message-bubble">
          <view class="message-text">{{ messageText(item) }}</view>
          <view class="message-time">{{ formatMessageTime(item.sentAt) }}</view>
        </view>
      </view>
      <view class="scroll-bottom-space"></view>
    </scroll-view>

    <view v-if="auth.isAuthenticated" class="composer safe-bottom">
      <textarea
        v-model="draft"
        class="composer-input"
        auto-height
        maxlength="1000"
        placeholder="发一条私信"
        confirm-type="send"
      />
      <button class="send-button" :disabled="!canSend" @tap="sendMessage">{{ sending ? '发送中' : '发送' }}</button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.chat-screen {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f6f7f8;
}

.chat-appbar {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 18rpx;
  min-height: 88rpx;
  padding: 0 18rpx 12rpx;
}

.back-button {
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

.chat-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  color: var(--bn-ink);
  font-size: 31rpx;
  font-weight: 880;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-subtitle {
  margin-top: 4rpx;
  color: var(--bn-muted);
  font-size: 22rpx;
  font-weight: 600;
}

.appbar-spacer {
  width: 62rpx;
}

.message-scroll {
  flex: 1;
  min-height: 0;
  padding: 10rpx 18rpx 0;
  box-sizing: border-box;
}

.history-button {
  width: 250rpx;
  height: 56rpx;
  margin: 8rpx auto 22rpx;
  border-radius: 999rpx;
  background: #fff;
  color: #6d737d;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 23rpx;
  box-shadow: var(--bn-shadow-soft);
}

.empty-chat-copy {
  margin-top: 160rpx;
  color: var(--bn-faint);
  text-align: center;
  font-size: 26rpx;
}

.message-row {
  display: flex;
  align-items: flex-end;
  gap: 12rpx;
  margin-bottom: 18rpx;
}

.message-row.mine {
  justify-content: flex-end;
}

.message-bubble {
  max-width: 520rpx;
  padding: 18rpx 20rpx 14rpx;
  border-radius: 8rpx;
  background: #fff;
  box-shadow: var(--bn-shadow-soft);
}

.message-row.mine .message-bubble {
  background: var(--bn-coral);
  color: #fff;
}

.message-text {
  color: inherit;
  font-size: 28rpx;
  line-height: 1.45;
  word-break: break-word;
}

.message-time {
  margin-top: 8rpx;
  color: rgba(92, 98, 108, 0.72);
  font-size: 20rpx;
  text-align: right;
}

.message-row.mine .message-time {
  color: rgba(255, 255, 255, 0.75);
}

.scroll-bottom-space {
  height: 28rpx;
}

.composer {
  flex: 0 0 auto;
  display: flex;
  align-items: flex-end;
  gap: 14rpx;
  padding: 16rpx 18rpx;
  background: rgba(255, 255, 255, 0.96);
  border-top: 1rpx solid rgba(230, 232, 235, 0.95);
}

.composer-input {
  flex: 1;
  min-height: 48rpx;
  max-height: 160rpx;
  padding: 16rpx 18rpx;
  border-radius: 8rpx;
  background: #f2f4f5;
  color: var(--bn-ink);
  font-size: 27rpx;
  line-height: 1.4;
  box-sizing: border-box;
}

.send-button {
  flex: 0 0 auto;
  width: 110rpx;
  height: 64rpx;
  border-radius: 999rpx;
  background: var(--bn-coral);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 25rpx;
  font-weight: 800;
}

.send-button[disabled] {
  background: #d8dde1;
  color: #8d949c;
}

.empty-action {
  width: 250rpx;
  margin-top: 10rpx;
}
</style>
