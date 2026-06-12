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

export interface CommentCursorPage<T> extends CursorPage<T> {
  degraded: boolean
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

export interface PushDeviceRegisterRequest {
  deviceId: string
  platform: DeviceInfo['platform']
  pushProvider: 'UNI_PUSH' | 'APNS' | 'FCM' | 'VENDOR_PUSH' | 'NOOP'
  providerClientId: string | null
  appVersion: string
  osVersion: string | null
  deviceModel: string | null
}

export interface PushDeviceRegisterResponse {
  deviceId: string
  userId: string
  platform: DeviceInfo['platform']
  pushProvider: string
  deviceStatus: 'ACTIVE' | 'UNBOUND' | string
  realtimeEnabled: boolean
  websocketUrl: string | null
  registeredAt: string
  lastActiveAt: string
}

export interface RealtimePushMessage {
  type: 'PUSH_MESSAGE'
  requestId: string
  scene: string
  title: string
  body: string
  data: Record<string, unknown>
  sentAt: string
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

export interface NoteLikeResponse {
  noteId: string
  liked: boolean
}

export interface NoteCollectResponse {
  noteId: string
  collected: boolean
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

export interface CommentAuthor {
  userId: string
  nickname: string
  avatarUrl: string | null
  userStatus: string
}

export interface CommentItem {
  commentId: string
  noteId: string
  rootId: string
  parentCommentId: string | null
  replyToUser: CommentAuthor | null
  author: CommentAuthor
  content: string
  level: 1 | 2
  commentStatus: 'VISIBLE' | 'DELETED' | 'HIDDEN'
  likeCount: number
  replyCount: number
  viewerAction: {
    liked: boolean
  }
  createdAt: string
  degraded: boolean
}

export interface CreateCommentRequest {
  clientRequestId: string
  content: string
}

export interface CreateCommentResponse {
  commentId: string
  noteId: string
  rootId: string
  parentCommentId: string | null
  level: 1 | 2
  commentStatus: 'VISIBLE' | 'DELETED' | 'HIDDEN'
  createdAt: string
}

export interface DeleteCommentResponse {
  commentId: string
  commentStatus: 'DELETED'
  deletedAt: string
}

export interface CommentLikeResponse {
  commentId: string
  liked: boolean
}

export type NotificationCategory = 'INTERACTION' | 'FOLLOW' | 'SYSTEM' | 'ORDER'

export interface NotificationActorSummary {
  userId: string
  nickname: string
  avatarUrl: string | null
}

export interface NotificationTarget {
  targetType?: string
  targetId?: string
  title?: string
  coverUrl?: string | null
  [key: string]: unknown
}

export interface NotificationJump {
  page?: string
  noteId?: string
  commentId?: string
  userId?: string
  noticeId?: string
  [key: string]: unknown
}

export interface NotificationItem {
  notificationId: string
  category: NotificationCategory
  notificationType: string
  aggregate: boolean
  actorCount: number
  title: string
  content: string
  read: boolean
  actors: NotificationActorSummary[]
  target: NotificationTarget
  jump: NotificationJump
  createdAt: string
  lastEventAt: string
}

export interface NotificationListResponse extends CursorPage<NotificationItem> {}

export interface NotificationUnreadCountResponse {
  totalUnread: number
  categories: Record<NotificationCategory, number>
}

export interface NotificationDetailResponse {
  notificationId: string
  category: NotificationCategory
  notificationType: string
  title: string
  content: string
  read: boolean
  snapshot: Record<string, unknown>
  jump: NotificationJump
}

export interface NotificationReadResponse {
  notificationId: string
  read: boolean
  totalUnread: number
}

export interface NotificationReadAllResponse {
  updatedCount: number
  totalUnread: number
  categories: Record<NotificationCategory, number>
}

export interface NotificationDeleteResponse {
  notificationId: string
  deleted: boolean
}

export interface NotificationDeleteBatchResponse {
  deletedCount: number
}
