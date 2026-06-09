<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { showApiError } from '@/api/request'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')

const title = computed(() => (mode.value === 'login' ? '欢迎回来' : '创建 BlueNote 账号'))
const subtitle = computed(() => (mode.value === 'login' ? '继续记录和发布你的生活笔记' : '注册后会自动进入登录态'))
const submitText = computed(() => (mode.value === 'login' ? '登录' : '注册并进入'))

onLoad(() => {
  if (auth.isAuthenticated) {
    uni.switchTab({ url: '/pages/home/index' })
  }
})

async function submit() {
  const cleanUsername = username.value.trim()
  if (!cleanUsername || password.value.length < 8) {
    uni.showToast({
      title: '请输入用户名和至少 8 位密码',
      icon: 'none'
    })
    return
  }
  try {
    if (mode.value === 'login') {
      await auth.loginWithPassword(cleanUsername, password.value)
    } else {
      await auth.registerWithPassword(cleanUsername, password.value)
    }
    uni.switchTab({ url: '/pages/home/index' })
  } catch (error) {
    showApiError(error, '登录失败')
  }
}
</script>

<template>
  <view class="screen login-screen top-safe">
    <view class="brand-panel">
      <view class="brand-mark">
        <text>BN</text>
      </view>
      <view>
        <view class="brand-name">BlueNote</view>
        <view class="brand-line">把今天写成一条好看的笔记</view>
      </view>
    </view>

    <view class="login-card panel">
      <view class="tabs">
        <button class="tab" :class="{ active: mode === 'login' }" @tap="mode = 'login'">登录</button>
        <button class="tab" :class="{ active: mode === 'register' }" @tap="mode = 'register'">注册</button>
      </view>

      <view class="form-title">{{ title }}</view>
      <view class="form-subtitle">{{ subtitle }}</view>

      <view class="form">
        <input v-model="username" class="field" placeholder="用户名，4-32 位字母数字或下划线" maxlength="32" />
        <input
          v-model="password"
          class="field"
          placeholder="密码，至少 8 位"
          password
          maxlength="128"
          confirm-type="done"
          @confirm="submit"
        />
        <button class="primary-button" :disabled="auth.submitting" @tap="submit">
          {{ auth.submitting ? '处理中' : submitText }}
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.login-screen {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  background:
    linear-gradient(180deg, rgba(39, 118, 223, 0.12), rgba(36, 181, 159, 0.06) 420rpx, rgba(247, 249, 246, 0) 620rpx),
    var(--bn-bg);
}

.brand-panel {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-top: 24rpx;
  padding: 34rpx 28rpx;
  border-radius: 16rpx;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.84), rgba(235, 249, 246, 0.82)),
    #fff;
  border: 1rpx solid rgba(255, 255, 255, 0.9);
  box-shadow: var(--bn-shadow-soft);
}

.brand-mark {
  width: 108rpx;
  height: 108rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(135deg, rgba(39, 118, 223, 0.94), rgba(36, 181, 159, 0.9)),
    var(--bn-blue);
  box-shadow: 0 18rpx 32rpx rgba(39, 118, 223, 0.18);
  color: #fff;
  font-size: 34rpx;
  font-weight: 780;
}

.brand-name {
  font-size: 48rpx;
  font-weight: 860;
  color: var(--bn-ink);
}

.brand-line {
  margin-top: 10rpx;
  color: var(--bn-muted);
  font-size: 25rpx;
}

.login-card {
  padding: 28rpx;
  box-shadow: var(--bn-shadow);
}

.tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10rpx;
  padding: 8rpx;
  border-radius: 16rpx;
  background: #edf5f1;
}

.tab {
  height: 70rpx;
  border-radius: 14rpx;
  color: var(--bn-muted);
  font-weight: 720;
}

.tab.active {
  background: #fff;
  color: var(--bn-blue);
  box-shadow: 0 8rpx 18rpx rgba(24, 33, 42, 0.07);
}

.form-title {
  margin-top: 38rpx;
  font-size: 42rpx;
  font-weight: 840;
}

.form-subtitle {
  margin-top: 10rpx;
  color: var(--bn-muted);
  font-size: 25rpx;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  margin-top: 34rpx;
}
</style>
