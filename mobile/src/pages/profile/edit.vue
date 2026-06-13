<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { showApiError } from '@/api/request'
import type { UserProfile } from '@/api/types'
import { updateCurrentUserProfile } from '@/api/user'
import AvatarCircle from '@/components/AvatarCircle.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import { pickSingleImage, uploadProfileImage } from '@/utils/upload'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const uploadingAvatar = ref(false)
const uploadingCover = ref(false)
const form = ref<ProfileEditForm>(createEmptyForm())

const canSave = computed(() => {
  return auth.isAuthenticated && !loading.value && !saving.value && !uploadingAvatar.value && !uploadingCover.value
})

const coverStyle = computed(() => {
  if (!form.value.homeCoverUrl) {
    return {}
  }
  return {
    backgroundImage: `linear-gradient(180deg, rgba(7, 27, 39, 0.12), rgba(7, 27, 39, 0.58)), url("${form.value.homeCoverUrl}")`
  }
})

interface ProfileEditForm {
  nickname: string
  bio: string
  gender: UserProfile['gender']
  birthday: string
  regionCode: string
  avatarFileId: string | null
  avatarUrl: string | null
  homeCoverFileId: string | null
  homeCoverUrl: string | null
  profileVersion: number
}

onShow(async () => {
  if (!auth.isAuthenticated) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  await loadProfile()
})

async function loadProfile() {
  loading.value = true
  try {
    await auth.fetchCurrentUser()
    if (auth.profile) {
      fillForm(auth.profile)
    }
  } catch (error) {
    showApiError(error, '资料加载失败')
  } finally {
    loading.value = false
  }
}

async function chooseAvatar() {
  await chooseProfileImage('USER_AVATAR')
}

async function chooseCover() {
  await chooseProfileImage('USER_HOME_COVER')
}

async function chooseProfileImage(scene: 'USER_AVATAR' | 'USER_HOME_COVER') {
  if (!canSave.value) {
    return
  }
  const isAvatar = scene === 'USER_AVATAR'
  try {
    if (isAvatar) {
      uploadingAvatar.value = true
    } else {
      uploadingCover.value = true
    }
    const image = await pickSingleImage()
    if (!image) {
      return
    }
    const uploaded = await uploadProfileImage(image, scene)
    if (isAvatar) {
      form.value.avatarFileId = uploaded.fileId
      form.value.avatarUrl = uploaded.accessUrl
    } else {
      form.value.homeCoverFileId = uploaded.fileId
      form.value.homeCoverUrl = uploaded.accessUrl
    }
  } catch (error) {
    showApiError(error, isAvatar ? '头像上传失败' : '封面上传失败')
  } finally {
    if (isAvatar) {
      uploadingAvatar.value = false
    } else {
      uploadingCover.value = false
    }
  }
}

function clearCover() {
  form.value.homeCoverFileId = null
  form.value.homeCoverUrl = null
}

function setGender(gender: UserProfile['gender']) {
  form.value.gender = gender
}

async function saveProfile() {
  if (!canSave.value) {
    return
  }
  const nickname = form.value.nickname.trim()
  if (!nickname) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await updateCurrentUserProfile({
      nickname,
      avatarFileId: form.value.avatarFileId,
      bio: normalizeOptionalText(form.value.bio),
      gender: form.value.gender,
      birthday: normalizeOptionalText(form.value.birthday),
      regionCode: normalizeOptionalText(form.value.regionCode),
      homeCoverFileId: form.value.homeCoverFileId,
      baseProfileVersion: form.value.profileVersion
    })
    await auth.fetchCurrentUser()
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => {
      uni.switchTab({ url: '/pages/profile/index' })
    }, 320)
  } catch (error) {
    showApiError(error, '保存失败')
    if (isVersionConflict(error)) {
      await loadProfile()
    }
  } finally {
    saving.value = false
  }
}

function goBack() {
  uni.switchTab({ url: '/pages/profile/index' })
}

function fillForm(profile: UserProfile) {
  form.value = {
    nickname: profile.nickname ?? '',
    bio: profile.bio ?? '',
    gender: profile.gender ?? 'UNKNOWN',
    birthday: profile.birthday ?? '',
    regionCode: profile.regionCode ?? '',
    avatarFileId: profile.avatarFileId,
    avatarUrl: profile.avatarUrl,
    homeCoverFileId: profile.homeCoverFileId,
    homeCoverUrl: profile.homeCoverUrl,
    profileVersion: profile.profileVersion
  }
}

function createEmptyForm(): ProfileEditForm {
  return {
    nickname: '',
    bio: '',
    gender: 'UNKNOWN',
    birthday: '',
    regionCode: '',
    avatarFileId: null,
    avatarUrl: null,
    homeCoverFileId: null,
    homeCoverUrl: null,
    profileVersion: 0
  }
}

function normalizeOptionalText(value: string) {
  const trimmed = value.trim()
  return trimmed ? trimmed : null
}

function isVersionConflict(error: unknown) {
  return error && typeof error === 'object' && 'code' in error && Number((error as { code?: unknown }).code) === 21007
}
</script>

<template>
  <view class="screen edit-screen top-safe">
    <view class="edit-header">
      <button class="back-button" @tap="goBack">‹</button>
      <view class="header-title">编辑主页</view>
      <button class="save-button" :disabled="!canSave" @tap="saveProfile">
        {{ saving ? '保存中' : '保存' }}
      </button>
    </view>

    <EmptyState v-if="!auth.isAuthenticated" title="还没有登录" subtitle="登录后可以编辑自己的主页。">
      <button class="primary-button empty-action" @tap="goBack">返回我的</button>
    </EmptyState>

    <view v-else>
      <view class="cover-editor" :style="coverStyle">
        <button class="cover-action" :disabled="uploadingCover || saving" @tap="chooseCover">
          {{ uploadingCover ? '上传中' : '更换封面' }}
        </button>
        <button v-if="form.homeCoverFileId" class="cover-clear" :disabled="saving" @tap="clearCover">清除</button>
      </view>

      <view class="avatar-section panel">
        <button class="avatar-button" :disabled="uploadingAvatar || saving" @tap="chooseAvatar">
          <AvatarCircle :src="form.avatarUrl" :name="form.nickname" size="large" />
          <view class="avatar-copy">
            <view class="avatar-title">{{ uploadingAvatar ? '正在上传头像' : '更换头像' }}</view>
            <view class="avatar-hint">支持 JPG、PNG、WebP</view>
          </view>
        </button>
      </view>

      <view class="form-panel panel">
        <view class="field-group">
          <view class="field-label">昵称</view>
          <input v-model="form.nickname" class="field-input" maxlength="64" placeholder="取一个好记的昵称" />
        </view>

        <view class="field-group">
          <view class="field-label">简介</view>
          <textarea
            v-model="form.bio"
            class="field-textarea"
            maxlength="256"
            auto-height
            placeholder="写几句别人点进主页时能看到的话"
          />
          <view class="char-count">{{ form.bio.length }}/256</view>
        </view>

        <view class="field-group">
          <view class="field-label">性别</view>
          <view class="segmented">
            <button class="segment" :class="{ active: form.gender === 'UNKNOWN' }" @tap="setGender('UNKNOWN')">保密</button>
            <button class="segment" :class="{ active: form.gender === 'FEMALE' }" @tap="setGender('FEMALE')">女</button>
            <button class="segment" :class="{ active: form.gender === 'MALE' }" @tap="setGender('MALE')">男</button>
          </view>
        </view>

        <view class="field-group two-column">
          <view>
            <view class="field-label">生日</view>
            <input v-model="form.birthday" class="field-input" placeholder="yyyy-MM-dd" />
          </view>
          <view>
            <view class="field-label">地区</view>
            <input v-model="form.regionCode" class="field-input" maxlength="32" placeholder="CN-330100" />
          </view>
        </view>
      </view>

      <button class="primary-button bottom-save" :disabled="!canSave" @tap="saveProfile">
        {{ saving ? '保存中' : '保存资料' }}
      </button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.edit-screen {
  padding-bottom: calc(44rpx + env(safe-area-inset-bottom));
}

.edit-header {
  display: grid;
  grid-template-columns: 72rpx 1fr 108rpx;
  align-items: center;
  gap: 14rpx;
  min-height: 78rpx;
  margin-bottom: 22rpx;
}

.back-button,
.save-button {
  min-height: 64rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  box-shadow: var(--bn-shadow-soft);
}

.back-button {
  color: var(--bn-ink);
  font-size: 48rpx;
  line-height: 1;
}

.save-button {
  color: var(--bn-coral);
  font-size: 25rpx;
  font-weight: 800;
}

.save-button[disabled] {
  opacity: 0.55;
}

.header-title {
  text-align: center;
  color: var(--bn-ink);
  font-size: 34rpx;
  font-weight: 900;
}

.empty-action {
  width: 240rpx;
  margin-top: 10rpx;
}

.cover-editor {
  position: relative;
  height: 310rpx;
  border-radius: 16rpx;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(7, 27, 39, 0.08), rgba(7, 27, 39, 0.64)),
    linear-gradient(135deg, #58bfe2 0%, #276fbf 48%, #184461 100%);
  background-size: cover;
  background-position: center;
  box-shadow: var(--bn-shadow);
}

.cover-action,
.cover-clear {
  position: absolute;
  right: 24rpx;
  min-height: 58rpx;
  padding: 0 22rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.9);
  color: var(--bn-ink);
  font-size: 24rpx;
  font-weight: 760;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-action {
  bottom: 24rpx;
}

.cover-clear {
  top: 24rpx;
  color: var(--bn-coral);
}

.avatar-section {
  margin-top: -54rpx;
  padding: 24rpx;
  position: relative;
  z-index: 1;
}

.avatar-button {
  width: 100%;
  min-height: 144rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.avatar-copy {
  min-width: 0;
  text-align: left;
}

.avatar-title {
  color: var(--bn-ink);
  font-size: 30rpx;
  font-weight: 860;
}

.avatar-hint {
  margin-top: 8rpx;
  color: var(--bn-muted);
  font-size: 23rpx;
}

.form-panel {
  margin-top: 22rpx;
  padding: 26rpx;
  display: flex;
  flex-direction: column;
  gap: 26rpx;
}

.field-group {
  min-width: 0;
}

.field-label {
  margin-bottom: 12rpx;
  color: var(--bn-muted);
  font-size: 24rpx;
  font-weight: 720;
}

.field-input,
.field-textarea {
  width: 100%;
  min-height: 84rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: #f7f9fa;
  color: var(--bn-ink);
  font-size: 28rpx;
}

.field-textarea {
  min-height: 170rpx;
  padding-top: 22rpx;
  line-height: 1.55;
}

.char-count {
  margin-top: 8rpx;
  text-align: right;
  color: var(--bn-faint);
  font-size: 22rpx;
}

.segmented {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8rpx;
  padding: 8rpx;
  border-radius: 16rpx;
  background: #f1f3f4;
}

.segment {
  min-height: 62rpx;
  border-radius: 14rpx;
  color: var(--bn-muted);
  font-size: 24rpx;
  font-weight: 740;
}

.segment.active {
  background: #fff;
  color: var(--bn-coral);
  box-shadow: var(--bn-shadow-soft);
}

.two-column {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18rpx;
}

.bottom-save {
  width: 100%;
  margin-top: 28rpx;
}
</style>
