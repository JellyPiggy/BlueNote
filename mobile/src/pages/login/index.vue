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
    <view class="brand">
      <view class="brand-mark">BN</view>
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
  gap: 36rpx;
}

.brand {
  display: flex;
  align-items: center;
  gap: 22rpx;
  margin-top: 34rpx;
}

.brand-mark {
  width: 96rpx;
  height: 96rpx;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--bn-blue), var(--bn-teal));
  color: #fff;
  font-size: 32rpx;
  font-weight: 780;
}

.brand-name {
  font-size: 46rpx;
  font-weight: 820;
  color: var(--bn-ink);
}

.brand-line {
  margin-top: 8rpx;
  color: var(--bn-muted);
  font-size: 24rpx;
}

.login-card {
  padding: 30rpx;
}

.tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10rpx;
  padding: 8rpx;
  border-radius: 8rpx;
  background: #edf3f2;
}

.tab {
  height: 70rpx;
  border-radius: 8rpx;
  color: var(--bn-muted);
  font-weight: 650;
}

.tab.active {
  background: #fff;
  color: var(--bn-blue);
  box-shadow: 0 8rpx 18rpx rgba(24, 33, 42, 0.07);
}

.form-title {
  margin-top: 38rpx;
  font-size: 42rpx;
  font-weight: 800;
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
  margin-top: 36rpx;
}
</style>

