<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import {
  getCurrentOrderActivity,
  getMyCoupons,
  getSeckillResult,
  getSeckillToken,
  mockPayOrder,
  submitSeckillOrder
} from '@/api/order'
import { showApiError } from '@/api/request'
import type { OrderActivityItem, OrderSeckillResultResponse, UserCouponItem } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const activity = ref<OrderActivityItem | null>(null)
const coupons = ref<UserCouponItem[]>([])
const result = ref<OrderSeckillResultResponse | null>(null)
const loading = ref(false)
const actionLoading = ref(false)
const paying = ref(false)
const loaded = ref(false)
const errorText = ref('')
let pollTimer: ReturnType<typeof setTimeout> | null = null

const activityStatusText = computed(() => {
  const status = activity.value?.status
  if (status === 'ONLINE') return '进行中'
  if (status === 'PREHEATED' || status === 'NOT_STARTED') return '待开始'
  if (status === 'SOLD_OUT') return '已抢光'
  if (status === 'PAUSED') return '暂停'
  if (status === 'ENDED') return '已结束'
  return status || ''
})

const canSeckill = computed(() => Boolean(activity.value && activity.value.status === 'ONLINE' && !activity.value.userJoined && !actionLoading.value))

const actionText = computed(() => {
  if (actionLoading.value) return '处理中'
  if (!activity.value) return '暂无活动'
  if (activity.value.userJoined) return '已参与'
  if (activity.value.status === 'ONLINE') return activity.value.payAmount > 0 ? '抢购神券' : '抢神券'
  if (activity.value.status === 'SOLD_OUT') return '已抢光'
  if (activity.value.status === 'PREHEATED' || activity.value.status === 'NOT_STARTED') return '还未开始'
  return '暂不可抢'
})

onShow(() => {
  if (auth.isAuthenticated) {
    void refresh()
  } else {
    loaded.value = true
  }
})

onPullDownRefresh(async () => {
  try {
    await refresh()
  } finally {
    uni.stopPullDownRefresh()
  }
})

onBeforeUnmount(() => {
  stopPolling()
})

async function refresh() {
  if (!auth.isAuthenticated) {
    loaded.value = true
    activity.value = null
    coupons.value = []
    return
  }
  loading.value = true
  errorText.value = ''
  try {
    const [activityResponse, couponResponse] = await Promise.all([
      getCurrentOrderActivity(),
      getMyCoupons('UNUSED', null, 20)
    ])
    activity.value = activityResponse.activity
    coupons.value = couponResponse.items
    loaded.value = true
  } catch (error) {
    errorText.value = '神券数据加载失败'
    showApiError(error, '神券数据加载失败')
  } finally {
    loading.value = false
  }
}

async function startSeckill() {
  if (!activity.value || !canSeckill.value) return
  actionLoading.value = true
  result.value = null
  stopPolling()
  try {
    const token = await getSeckillToken(activity.value.activityId)
    const submitted = await submitSeckillOrder({
      activityId: activity.value.activityId,
      clientRequestId: clientRequestId(),
      seckillToken: token.seckillToken
    })
    result.value = {
      requestId: submitted.requestId,
      status: submitted.status,
      orderId: null,
      userCouponId: null,
      payRequired: false,
      payAmount: 0,
      expireAt: null,
      message: submitted.message
    }
    if (submitted.status === 'PROCESSING') {
      pollResult(submitted.requestId, 0)
    } else {
      await refresh()
    }
  } catch (error) {
    showApiError(error, '抢券失败')
  } finally {
    actionLoading.value = false
  }
}

function pollResult(requestId: string, attempt: number) {
  pollTimer = setTimeout(async () => {
    try {
      const latest = await getSeckillResult(requestId)
      result.value = latest
      if (latest.status === 'PROCESSING' && attempt < 9) {
        pollResult(requestId, attempt + 1)
        return
      }
      await refresh()
      if (latest.status === 'SUCCESS') {
        uni.showToast({ title: '神券已到账', icon: 'none' })
      }
    } catch (error) {
      showApiError(error, '结果查询失败')
    }
  }, Math.min(1800, 700 + attempt * 220))
}

async function payCurrentOrder() {
  const orderId = result.value?.orderId
  if (!orderId || paying.value) return
  paying.value = true
  try {
    const paid = await mockPayOrder(orderId)
    result.value = {
      requestId: result.value?.requestId ?? '',
      status: paid.orderStatus === 'SUCCESS' ? 'SUCCESS' : paid.orderStatus,
      orderId: paid.orderId,
      userCouponId: paid.userCouponId,
      payRequired: false,
      payAmount: result.value?.payAmount ?? 0,
      expireAt: null,
      message: '神券已到账'
    }
    await refresh()
    uni.showToast({ title: '支付完成', icon: 'none' })
  } catch (error) {
    showApiError(error, '支付失败')
  } finally {
    paying.value = false
  }
}

function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/login/index' })
}

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}

function clientRequestId() {
  return `coupon-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function moneyText(value: number) {
  return `¥${(value / 100).toFixed(value % 100 === 0 ? 0 : 2)}`
}

function dateText(value: string | null) {
  if (!value) return ''
  return value.replace('T', ' ').replace('+08:00', '')
}
</script>

<template>
  <view class="screen order-screen top-safe">
    <view class="order-appbar">
      <button class="back-button" @tap="goHome">‹</button>
      <view class="page-title">神券</view>
      <button class="refresh-button" @tap="refresh">刷新</button>
    </view>

    <view v-if="loading && !loaded" class="loading-copy">正在读取神券</view>

    <EmptyState
      v-else-if="!auth.isAuthenticated"
      title="登录后抢神券"
      subtitle="登录后可参与限时活动，并查看你的神券卡包。"
    >
      <button class="primary-button empty-action" @tap="goLogin">去登录</button>
    </EmptyState>

    <EmptyState v-else-if="errorText" title="暂时读不到神券" subtitle="稍后刷新，或检查本地网关是否正在运行。">
      <button class="secondary-button empty-action" @tap="refresh">重新加载</button>
    </EmptyState>

    <template v-else>
      <view v-if="activity" class="activity-panel panel">
        <view class="activity-head">
          <view>
            <view class="activity-name">{{ activity.activityName }}</view>
            <view class="activity-template">{{ activity.templateName }}</view>
          </view>
          <view class="status-pill">{{ activityStatusText }}</view>
        </view>
        <view class="coupon-face">
          <view class="face-value">{{ moneyText(activity.faceValue) }}</view>
          <view class="face-meta">{{ activity.thresholdAmount > 0 ? `满 ${moneyText(activity.thresholdAmount)} 可用` : '无门槛' }}</view>
        </view>
        <view class="activity-meta">
          <view class="meta-row">
            <text>开始</text>
            <text>{{ dateText(activity.startAt) }}</text>
          </view>
          <view class="meta-row">
            <text>结束</text>
            <text>{{ dateText(activity.endAt) }}</text>
          </view>
          <view class="meta-row">
            <text>支付</text>
            <text>{{ activity.payAmount > 0 ? moneyText(activity.payAmount) : '免费' }}</text>
          </view>
        </view>
        <view v-if="activity.description" class="activity-desc">{{ activity.description }}</view>
        <button class="primary-button seckill-button" :disabled="!canSeckill" @tap="startSeckill">{{ actionText }}</button>
      </view>

      <EmptyState v-else title="暂无神券活动" subtitle="新的限时活动会出现在这里。" />

      <view v-if="result" class="result-panel panel">
        <view class="section-title">抢券结果</view>
        <view class="result-status">{{ result.message || result.status }}</view>
        <view v-if="result.orderId" class="result-line">订单号 {{ result.orderId }}</view>
        <view v-if="result.payRequired" class="pay-box">
          <view>
            <view class="pay-title">待支付 {{ moneyText(result.payAmount) }}</view>
            <view class="pay-subtitle">截止 {{ dateText(result.expireAt) }}</view>
          </view>
          <button class="secondary-button pay-button" :disabled="paying" @tap="payCurrentOrder">{{ paying ? '支付中' : 'MOCK 支付' }}</button>
        </view>
      </view>

      <view class="coupon-section">
        <view class="section-title">我的神券</view>
        <view v-if="coupons.length" class="coupon-list">
          <view v-for="coupon in coupons" :key="coupon.userCouponId" class="coupon-card panel">
            <view class="coupon-main">
              <view class="coupon-value">{{ moneyText(coupon.faceValue) }}</view>
              <view>
                <view class="coupon-name">{{ coupon.templateName }}</view>
                <view class="coupon-rule">{{ coupon.thresholdAmount > 0 ? `满 ${moneyText(coupon.thresholdAmount)} 可用` : '无门槛' }}</view>
              </view>
            </view>
            <view class="coupon-time">{{ dateText(coupon.validEndAt) }} 到期</view>
          </view>
        </view>
        <EmptyState v-else title="卡包还是空的" subtitle="抢到的神券会放在这里。" />
      </view>
    </template>
  </view>
</template>

<style scoped lang="scss">
.order-screen {
  padding-left: 22rpx;
  padding-right: 22rpx;
}

.order-appbar {
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
}

.page-title {
  text-align: center;
  font-size: 32rpx;
  font-weight: 860;
}

.activity-panel,
.result-panel,
.coupon-section {
  margin-top: 24rpx;
}

.activity-panel {
  padding: 28rpx;
}

.activity-head {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
}

.activity-name {
  color: var(--bn-ink);
  font-size: 34rpx;
  font-weight: 860;
}

.activity-template {
  margin-top: 8rpx;
  color: var(--bn-muted);
  font-size: 24rpx;
}

.status-pill {
  align-self: flex-start;
  min-width: 96rpx;
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  color: #7a4d00;
  background: #fff2c2;
  font-size: 22rpx;
  font-weight: 760;
  text-align: center;
}

.coupon-face {
  margin-top: 28rpx;
  padding: 28rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #fff6d9, #eaf7f3);
  border: 1rpx solid rgba(243, 190, 62, 0.24);
}

.face-value {
  color: #c73b31;
  font-size: 64rpx;
  font-weight: 900;
}

.face-meta {
  margin-top: 8rpx;
  color: #5f6268;
  font-size: 25rpx;
}

.activity-meta {
  margin-top: 22rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  color: var(--bn-muted);
  font-size: 24rpx;
}

.activity-desc {
  margin-top: 20rpx;
  color: var(--bn-muted);
  font-size: 25rpx;
  line-height: 1.5;
}

.seckill-button {
  margin-top: 28rpx;
}

.result-panel {
  padding: 24rpx;
}

.section-title {
  color: var(--bn-ink);
  font-size: 28rpx;
  font-weight: 820;
}

.result-status {
  margin-top: 18rpx;
  color: var(--bn-coral);
  font-size: 30rpx;
  font-weight: 820;
}

.result-line {
  margin-top: 8rpx;
  color: var(--bn-muted);
  font-size: 23rpx;
}

.pay-box {
  margin-top: 18rpx;
  padding: 18rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  border-radius: 16rpx;
  background: #f6f7f8;
}

.pay-title {
  color: var(--bn-ink);
  font-size: 27rpx;
  font-weight: 760;
}

.pay-subtitle {
  margin-top: 6rpx;
  color: var(--bn-muted);
  font-size: 22rpx;
}

.pay-button {
  width: 180rpx;
  min-height: 72rpx;
  font-size: 24rpx;
}

.coupon-section {
  padding-bottom: 44rpx;
}

.coupon-list {
  margin-top: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.coupon-card {
  padding: 22rpx;
  box-shadow: var(--bn-shadow-soft);
}

.coupon-main {
  display: flex;
  align-items: center;
  gap: 22rpx;
}

.coupon-value {
  flex: 0 0 132rpx;
  color: #c73b31;
  font-size: 42rpx;
  font-weight: 900;
}

.coupon-name {
  color: var(--bn-ink);
  font-size: 27rpx;
  font-weight: 760;
}

.coupon-rule,
.coupon-time {
  margin-top: 8rpx;
  color: var(--bn-muted);
  font-size: 22rpx;
}

.empty-action {
  min-width: 220rpx;
  margin-top: 8rpx;
}
</style>
