<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { publishNote, saveDraft } from '@/api/note'
import { showApiError } from '@/api/request'
import { useAuthStore } from '@/stores/auth'
import { createClientRequestId, splitTopics } from '@/utils/format'
import { pickNoteImages, uploadNoteImages, type PickedImage } from '@/utils/upload'

const auth = useAuthStore()
const title = ref('')
const content = ref('')
const topicText = ref('')
const visibility = ref<'PUBLIC' | 'PRIVATE'>('PUBLIC')
const commentEnabled = ref(true)
const images = ref<PickedImage[]>([])
const submitting = ref(false)
const progressText = ref('')

const canSubmit = computed(() => {
  return title.value.trim().length > 0 && content.value.trim().length > 0 && images.value.length > 0 && !submitting.value
})

onShow(() => {
  if (!auth.isAuthenticated) {
    uni.navigateTo({ url: '/pages/login/index' })
  }
})

async function chooseImages() {
  if (!auth.isAuthenticated) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  try {
    const remain = 9 - images.value.length
    if (remain <= 0) {
      uni.showToast({ title: '最多选择 9 张图片', icon: 'none' })
      return
    }
    const picked = await pickNoteImages(remain)
    images.value = [...images.value, ...picked].slice(0, 9)
  } catch {
    uni.showToast({ title: '未选择图片', icon: 'none' })
  }
}

function removeImage(id: string) {
  images.value = images.value.filter((image) => image.id !== id)
}

function toggleComment(event: Event) {
  const detail = (event as unknown as { detail?: { value?: boolean } }).detail
  commentEnabled.value = Boolean(detail?.value)
}

async function submitPublish() {
  await submit(false)
}

async function submitDraft() {
  await submit(true)
}

async function submit(asDraft: boolean) {
  if (!auth.isAuthenticated) {
    uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  if (!title.value.trim()) {
    uni.showToast({ title: '先写一个标题', icon: 'none' })
    return
  }
  if (!asDraft && (!content.value.trim() || images.value.length === 0)) {
    uni.showToast({ title: '发布需要正文和图片', icon: 'none' })
    return
  }
  submitting.value = true
  progressText.value = images.value.length ? '正在上传图片' : '正在保存'
  const clientRequestId = createClientRequestId(asDraft ? 'draft' : 'publish')
  try {
    const uploaded = images.value.length ? await uploadNoteImages(images.value) : []
    progressText.value = asDraft ? '正在保存草稿' : '正在发布笔记'
    const payload = {
      clientRequestId,
      title: title.value.trim(),
      content: content.value.trim(),
      visibility: asDraft ? 'PRIVATE' : visibility.value,
      commentEnabled: commentEnabled.value,
      mediaFiles: uploaded.map((file, index) => ({
        fileId: file.fileId,
        mediaType: 'IMAGE' as const,
        sortOrder: index + 1,
        cover: index === 0
      })),
      topics: splitTopics(topicText.value)
    }
    if (asDraft) {
      const draft = await saveDraft(payload, clientRequestId)
      uni.showToast({ title: '草稿已保存', icon: 'success' })
      resetForm()
      uni.navigateTo({ url: `/pages/note/detail?noteId=${draft.noteId}` })
    } else {
      const note = await publishNote(payload, clientRequestId)
      uni.showToast({ title: '发布成功', icon: 'success' })
      resetForm()
      uni.navigateTo({ url: `/pages/note/detail?noteId=${note.noteId}` })
    }
  } catch (error) {
    showApiError(error, asDraft ? '草稿保存失败' : '发布失败')
  } finally {
    submitting.value = false
    progressText.value = ''
  }
}

function resetForm() {
  title.value = ''
  content.value = ''
  topicText.value = ''
  visibility.value = 'PUBLIC'
  commentEnabled.value = true
  images.value = []
}
</script>

<template>
  <view class="screen publish-screen top-safe">
    <view class="publish-header">
      <view>
        <view class="eyebrow">创作</view>
        <view class="headline">写一条新笔记</view>
      </view>
      <button class="ghost-button clear-button" :disabled="submitting" @tap="resetForm">清空</button>
    </view>

    <view class="image-panel panel">
      <view class="section-row">
        <view>
          <view class="section-title">图片</view>
          <view class="section-hint">第一张会作为封面</view>
        </view>
        <view class="chip">{{ images.length }}/9</view>
      </view>

      <scroll-view v-if="images.length" class="image-strip" scroll-x>
        <view class="image-list">
          <view v-for="image in images" :key="image.id" class="picked-image">
            <image class="preview" :src="image.path" mode="aspectFill" />
            <button class="remove-button" @tap="removeImage(image.id)">×</button>
            <view v-if="images[0]?.id === image.id" class="cover-badge">封面</view>
          </view>
          <button class="add-tile" @tap="chooseImages">+</button>
        </view>
      </scroll-view>

      <button v-else class="picker-tile" @tap="chooseImages">
        <view class="picker-mark">+</view>
        <view class="picker-title">选择图片</view>
        <view class="picker-subtitle">第一张会成为笔记封面</view>
      </button>
    </view>

    <view class="form-panel panel">
      <view class="input-group">
        <input v-model="title" class="title-input" placeholder="写一个具体的标题" maxlength="128" />
        <textarea
          v-model="content"
          class="content-input"
          placeholder="记录发生了什么、在哪里、有什么感受"
          maxlength="5000"
          auto-height
        />
      </view>
      <input v-model="topicText" class="field topic-field" placeholder="话题，用空格或 # 分隔，例如 周末 咖啡" />

      <view class="option-row">
        <view>
          <view class="option-title">可见性</view>
          <view class="option-hint">{{ visibility === 'PUBLIC' ? '公开展示在你的主页' : '仅自己可见' }}</view>
        </view>
        <view class="segmented">
          <button class="segment" :class="{ active: visibility === 'PUBLIC' }" @tap="visibility = 'PUBLIC'">公开</button>
          <button class="segment" :class="{ active: visibility === 'PRIVATE' }" @tap="visibility = 'PRIVATE'">私密</button>
        </view>
      </view>

      <view class="option-row">
        <view>
          <view class="option-title">评论</view>
          <view class="option-hint">{{ commentEnabled ? '允许别人评论' : '关闭评论入口' }}</view>
        </view>
        <switch :checked="commentEnabled" color="#2eb7a6" @change="toggleComment" />
      </view>
    </view>

    <view class="actions">
      <button class="secondary-button action-button" :disabled="submitting || !title.trim()" @tap="submitDraft">
        保存草稿
      </button>
      <button class="primary-button action-button" :disabled="!canSubmit" @tap="submitPublish">
        {{ submitting ? progressText || '发布中' : '发布' }}
      </button>
    </view>
  </view>
</template>

<style scoped lang="scss">
.publish-screen {
  padding-bottom: 280rpx;
}

.publish-header,
.section-row,
.option-row,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22rpx;
}

.eyebrow {
  color: var(--bn-coral);
  font-size: 23rpx;
  font-weight: 820;
}

.headline {
  margin-top: 8rpx;
  font-size: 44rpx;
  font-weight: 860;
  line-height: 1.18;
}

.clear-button {
  width: 126rpx;
  min-height: 66rpx;
  font-size: 24rpx;
}

.image-panel,
.form-panel {
  margin-top: 28rpx;
  padding: 26rpx;
}

.section-title,
.option-title {
  font-size: 30rpx;
  font-weight: 800;
}

.section-hint,
.option-hint {
  margin-top: 6rpx;
  color: var(--bn-muted);
  font-size: 23rpx;
}

.image-strip {
  width: 100%;
  margin-top: 22rpx;
  white-space: nowrap;
}

.image-list {
  display: inline-flex;
  gap: 18rpx;
}

.picked-image,
.add-tile {
  position: relative;
  width: 192rpx;
  height: 232rpx;
  border-radius: 16rpx;
  overflow: hidden;
  background: #e6f1ed;
}

.preview {
  width: 100%;
  height: 100%;
}

.remove-button {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: rgba(24, 33, 42, 0.72);
  color: #fff;
  font-size: 34rpx;
  line-height: 44rpx;
}

.cover-badge {
  position: absolute;
  left: 10rpx;
  bottom: 10rpx;
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.92);
  color: var(--bn-blue);
  font-size: 20rpx;
  font-weight: 700;
}

.add-tile,
.picker-tile {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  border: 1rpx dashed rgba(36, 181, 159, 0.48);
  color: var(--bn-teal);
  font-size: 48rpx;
}

.picker-tile {
  width: 100%;
  height: 386rpx;
  margin-top: 24rpx;
  border-radius: 16rpx;
  background:
    linear-gradient(135deg, rgba(36, 181, 159, 0.08), rgba(39, 118, 223, 0.05)),
    #f6fbf8;
}

.picker-mark {
  width: 92rpx;
  height: 92rpx;
  border-radius: 16rpx;
  background: rgba(36, 181, 159, 0.14);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 58rpx;
}

.picker-title {
  margin-top: 22rpx;
  color: var(--bn-ink);
  font-size: 30rpx;
  font-weight: 740;
}

.picker-subtitle {
  margin-top: 8rpx;
  color: var(--bn-muted);
  font-size: 22rpx;
}

.form-panel {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.input-group {
  padding: 4rpx 2rpx 2rpx;
}

.title-input {
  width: 100%;
  min-height: 90rpx;
  color: var(--bn-ink);
  font-size: 38rpx;
  font-weight: 820;
}

.content-input {
  width: 100%;
  min-height: 270rpx;
  color: var(--bn-ink);
  font-size: 28rpx;
  line-height: 1.68;
}

.topic-field {
  background: #f8fbfa;
}

.option-row {
  padding-top: 22rpx;
  border-top: 1rpx solid var(--bn-line);
}

.segmented {
  display: flex;
  gap: 8rpx;
  padding: 6rpx;
  border-radius: 16rpx;
  background: #edf5f1;
}

.segment {
  width: 100rpx;
  height: 58rpx;
  border-radius: 14rpx;
  color: var(--bn-muted);
  font-size: 23rpx;
}

.segment.active {
  background: #fff;
  color: var(--bn-blue);
  font-weight: 720;
}

.actions {
  position: sticky;
  bottom: 116rpx;
  z-index: 4;
  margin-top: 30rpx;
  padding: 16rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.92);
  border: 1rpx solid rgba(229, 236, 232, 0.9);
  box-shadow: 0 18rpx 40rpx rgba(27, 43, 56, 0.12);
}

.action-button {
  flex: 1;
}
</style>
