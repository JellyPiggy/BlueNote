export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  traceId: string
}

export interface CursorPage<T> {
  items: T[]
  nextCursor: string | null
  hasMore: boolean
}

export interface TokenPair {
  userId: string
  accessToken: string
  refreshToken: string
  accessTokenExpiresIn: number
  refreshTokenExpiresIn: number
}

export interface DeviceInfo {
  deviceId: string
  deviceName: string | null
  platform: 'IOS' | 'ANDROID' | 'H5'
  appVersion: string
}

export interface UserProfile {
  userId: string
  bluenoteNo: string
  nickname: string
  avatarFileId: string | null
  avatarUrl: string | null
  bio: string | null
  gender: 'UNKNOWN' | 'MALE' | 'FEMALE'
  birthday: string | null
  regionCode: string | null
  homeCoverFileId: string | null
  homeCoverUrl: string | null
  userStatus: 'NORMAL' | 'DISABLED' | 'DELETED'
  profileVersion: number
}

export interface UserSummary {
  userId: string
  bluenoteNo: string
  nickname: string
  avatarUrl: string | null
  bio: string | null
  userStatus: string
  profileVersion: number
}

export interface UserHome {
  user: UserSummary
  counts: {
    followingCount: number
    followerCount: number
    noteCount: number
    likedCount: number
  }
  relation: {
    followStatus: string
  }
  degraded: boolean
}

export interface UploadToken {
  fileId: string
  uploadMethod: 'PRESIGNED_PUT'
  uploadUrl: string
  headers: Record<string, string>
  expireAt: string
  objectKey: string
}

export interface ConfirmUploadResult {
  fileId: string
  fileStatus: 'INIT' | 'UPLOADED' | 'BOUND' | 'DELETED' | 'BLOCKED'
  scene: string
  accessUrl: string
}

export interface NoteMediaInput {
  fileId: string
  mediaType: 'IMAGE'
  sortOrder: number
  cover: boolean
}

export interface UpsertNoteRequest {
  clientRequestId: string
  title: string
  content: string
  visibility: 'PUBLIC' | 'PRIVATE'
  commentEnabled: boolean
  mediaFiles: NoteMediaInput[]
  topics: string[]
}

export interface PublishNoteResponse {
  noteId: string
  noteStatus: 'PUBLISHED' | 'PRIVATE'
  visibility: 'PUBLIC' | 'PRIVATE'
  currentVersion: number
  publishedAt: string
}

export interface DraftNoteResponse {
  noteId: string
  noteStatus: 'DRAFT'
  latestVersion: number
  updatedAt: string
}

export interface NoteCard {
  noteId: string
  title: string
  coverUrl: string | null
  authorId: string
  likeCount: number
  collectCount: number
  publishedAt: string | null
}

export interface NoteDetail {
  noteId: string
  author: {
    userId: string
    nickname: string
    avatarUrl: string | null
    userStatus: string
  }
  title: string
  content: string
  visibility: 'PUBLIC' | 'PRIVATE'
  noteStatus: string
  currentVersion: number
  commentEnabled: boolean
  mediaFiles: Array<{
    fileId: string
    mediaType: 'IMAGE' | 'VIDEO'
    sortOrder: number
    cover: boolean
    accessUrl: string
  }>
  topics: string[]
  counts: {
    likeCount: number
    collectCount: number
    commentCount: number
  }
  viewerAction: {
    liked: boolean
    collected: boolean
  }
  publishedAt: string | null
  degraded: boolean
}

