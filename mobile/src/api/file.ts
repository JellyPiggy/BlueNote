import { apiRequest } from './request'
import type { ConfirmUploadResult, UploadToken } from './types'

export function createUploadToken(payload: {
  scene: 'USER_AVATAR' | 'USER_HOME_COVER' | 'NOTE_IMAGE'
  filename: string
  mimeType: string
  fileSize: number
}) {
  return apiRequest<UploadToken>({
    method: 'POST',
    path: '/api/files/upload-token',
    data: payload
  })
}

export function confirmUpload(fileId: string, payload: { etag?: string; fileSize: number }) {
  return apiRequest<ConfirmUploadResult>({
    method: 'POST',
    path: `/api/files/${fileId}/confirm`,
    data: payload
  })
}

