# 第一条主链路数据库契约

版本：v0.1  
状态：第一条主链路开发基线

本文定义 auth、user、file、note 四个逻辑服务第一阶段必须落地的 schema、核心表、唯一约束和索引。本文不是最终可执行 SQL，后续 `backend/sql/` 的 DDL 必须以本文为基线生成。

## 1. 通用表规则

所有业务表默认遵守：

1. ID 在数据库中使用 `BIGINT`，API 中使用 string。
2. 时间使用 `DATETIME(3)`。
3. 核心表包含 `created_at`、`updated_at`。
4. 支持逻辑删除的表包含 `deleted TINYINT NOT NULL DEFAULT 0`。
5. 状态字段使用 `VARCHAR(32)`。
6. JSON 字段只保存事件 payload、响应快照等结构化扩展数据，不用于高频查询条件。
7. 所有唯一约束必须由数据库兜底，不能只依赖应用判断。

## 2. Schema 清单

| Schema | 归属服务 | 第一阶段用途 |
|---|---|---|
| `bluenote_auth` | `bluenote-auth` | 账号、密码、设备会话、登录审计 |
| `bluenote_user` | `bluenote-user` | 用户资料、资料审计、用户事件 |
| `bluenote_file` | `bluenote-file` | 文件元数据、上传会话、业务绑定 |
| `bluenote_note` | `bluenote-note` | 笔记、版本、媒体、点赞收藏、幂等、outbox |

禁止跨 schema join。跨服务数据只能通过内部接口或事件读模型获得。

## 3. bluenote_auth

### 3.1 auth_account

账号表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `user_id` | BIGINT | PK | 用户 ID |
| `username` | VARCHAR(32) | NOT NULL | 登录用户名 |
| `account_status` | VARCHAR(32) | NOT NULL | `NORMAL` / `DISABLED` / `BANNED` / `DELETED` |
| `register_channel` | VARCHAR(32) | NOT NULL | `APP` / `H5` 等 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除 |

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(user_id)` | 按用户 ID 查询 |
| `uk_auth_account_username(username)` | 用户名唯一 |
| `idx_auth_account_status(account_status)` | 管理端按状态筛选 |

### 3.2 auth_password

密码凭证表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `user_id` | BIGINT | NOT NULL | 用户 ID |
| `password_hash` | VARCHAR(255) | NOT NULL | 密码哈希 |
| `password_algo` | VARCHAR(32) | NOT NULL | `BCrypt` / `Argon2id` |
| `password_version` | INT | NOT NULL | 密码版本 |
| `password_updated_at` | DATETIME(3) | NOT NULL | 密码更新时间 |
| `need_reset` | TINYINT | NOT NULL DEFAULT 0 | 是否要求重置密码 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(id)` | 主键 |
| `uk_auth_password_user(user_id)` | 一个用户一条密码凭证 |

### 3.3 auth_session

设备会话表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `session_id` | BIGINT | PK | 会话 ID |
| `user_id` | BIGINT | NOT NULL | 用户 ID |
| `device_id` | VARCHAR(128) | NOT NULL | 设备 ID |
| `device_name` | VARCHAR(128) | NULL | 设备名称 |
| `platform` | VARCHAR(32) | NOT NULL | `IOS` / `ANDROID` / `H5` |
| `app_version` | VARCHAR(32) | NOT NULL | App 版本 |
| `refresh_token_hash` | VARCHAR(128) | NOT NULL | 当前 Refresh Token hash |
| `refresh_token_expires_at` | DATETIME(3) | NOT NULL | Refresh Token 过期时间 |
| `session_status` | VARCHAR(32) | NOT NULL | `ACTIVE` / `LOGGED_OUT` / `EXPIRED` / `REVOKED` |
| `login_ip` | VARCHAR(64) | NULL | 登录 IP |
| `last_active_at` | DATETIME(3) | NULL | 最近活跃时间 |
| `revoked_at` | DATETIME(3) | NULL | 失效时间 |
| `revoke_reason` | VARCHAR(64) | NULL | 失效原因 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(session_id)` | 会话 ID 查询 |
| `uk_auth_session_refresh_hash(refresh_token_hash)` | Refresh Token 查询 |
| `idx_auth_session_user_status(user_id, session_status)` | 查询用户有效会话 |
| `idx_auth_session_device(user_id, device_id)` | 查询设备会话 |

### 3.4 auth_login_audit

登录审计表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `user_id` | BIGINT | NULL | 用户 ID |
| `username` | VARCHAR(32) | NULL | 登录用户名 |
| `action` | VARCHAR(32) | NOT NULL | `REGISTER` / `LOGIN` / `LOGOUT` / `REFRESH` / `CHANGE_PASSWORD` |
| `result` | VARCHAR(32) | NOT NULL | `SUCCESS` / `FAIL` |
| `fail_reason` | VARCHAR(128) | NULL | 失败原因 |
| `ip` | VARCHAR(64) | NULL | IP |
| `device_id` | VARCHAR(128) | NULL | 设备 ID |
| `platform` | VARCHAR(32) | NULL | 平台 |
| `app_version` | VARCHAR(32) | NULL | App 版本 |
| `trace_id` | VARCHAR(64) | NOT NULL | 链路 ID |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |

索引：

| 索引 | 说明 |
|---|---|
| `idx_auth_audit_user_time(user_id, created_at)` | 用户登录历史 |
| `idx_auth_audit_username_time(username, created_at)` | 账号失败排查 |
| `idx_auth_audit_ip_time(ip, created_at)` | IP 风险排查 |

### 3.5 auth_outbox_event

登录服务 outbox 事件表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `event_id` | VARCHAR(64) | NOT NULL | 事件 ID |
| `event_type` | VARCHAR(64) | NOT NULL | 事件类型 |
| `aggregate_id` | BIGINT | NOT NULL | userId |
| `payload` | JSON | NOT NULL | 事件内容 |
| `status` | VARCHAR(32) | NOT NULL | `INIT` / `SENT` / `FAILED` |
| `retry_count` | INT | NOT NULL DEFAULT 0 | 重试次数 |
| `next_retry_at` | DATETIME(3) | NULL | 下次重试时间 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `uk_auth_outbox_event(event_id)` | 事件唯一 |
| `idx_auth_outbox_status_retry(status, next_retry_at)` | 扫描待投递事件 |

## 4. bluenote_user

### 4.1 user_profile

用户主资料表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `user_id` | BIGINT | PK | 用户 ID |
| `bluenote_no` | VARCHAR(32) | NOT NULL | BlueNote 号 |
| `nickname` | VARCHAR(64) | NOT NULL | 昵称 |
| `avatar_file_id` | BIGINT | NULL | 头像文件 ID |
| `avatar_url` | VARCHAR(512) | NULL | 头像 URL 快照 |
| `bio` | VARCHAR(256) | NULL | 简介 |
| `gender` | VARCHAR(16) | NOT NULL | `UNKNOWN` / `MALE` / `FEMALE` |
| `birthday` | DATE | NULL | 生日 |
| `region_code` | VARCHAR(32) | NULL | 地区编码 |
| `home_cover_file_id` | BIGINT | NULL | 主页背景文件 ID |
| `home_cover_url` | VARCHAR(512) | NULL | 主页背景 URL 快照 |
| `user_status` | VARCHAR(32) | NOT NULL | `NORMAL` / `DISABLED` / `DELETED` |
| `profile_version` | BIGINT | NOT NULL | 资料版本 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除 |

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(user_id)` | 按用户 ID 查询 |
| `uk_user_profile_bluenote_no(bluenote_no)` | BlueNote 号唯一 |
| `idx_user_profile_status(user_status, updated_at)` | 管理端按状态筛选 |

### 4.2 user_profile_audit

资料修改审计表，必须记录用户主动修改资料。

关键字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | 用户 ID |
| `field_name` | VARCHAR(64) | 修改字段 |
| `old_value_mask` | VARCHAR(512) | 修改前值，脱敏 |
| `new_value_mask` | VARCHAR(512) | 修改后值，脱敏 |
| `operator_id` | BIGINT | 操作人 |
| `operator_type` | VARCHAR(32) | `USER` / `ADMIN` / `SYSTEM` |
| `trace_id` | VARCHAR(64) | 链路 ID |
| `created_at` | DATETIME(3) | 创建时间 |

索引：

| 索引 | 说明 |
|---|---|
| `idx_user_audit_user_time(user_id, created_at)` | 查询用户资料变更历史 |

### 4.3 user_outbox_event

用户服务本地事件表。

字段与通用 outbox 一致，主键为 `event_id`。

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(event_id)` | 事件唯一 |
| `idx_user_outbox_status_retry(send_status, next_retry_at)` | 扫描待投递事件 |
| `idx_user_outbox_aggregate(aggregate_id, event_type)` | 按用户排查事件 |

## 5. bluenote_file

### 5.1 file_object

文件元数据主表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `file_id` | BIGINT | PK | 文件 ID |
| `owner_id` | BIGINT | NOT NULL | 上传用户 ID |
| `scene` | VARCHAR(32) | NOT NULL | 文件场景 |
| `storage_type` | VARCHAR(32) | NOT NULL | `MINIO` |
| `bucket` | VARCHAR(128) | NOT NULL | bucket |
| `object_key` | VARCHAR(512) | NOT NULL | 对象 key |
| `original_filename` | VARCHAR(255) | NULL | 原始文件名 |
| `file_ext` | VARCHAR(16) | NOT NULL | 扩展名 |
| `mime_type` | VARCHAR(128) | NOT NULL | MIME |
| `file_size` | BIGINT | NOT NULL | 字节数 |
| `etag` | VARCHAR(128) | NULL | 对象存储 ETag |
| `width` | INT | NULL | 图片宽度 |
| `height` | INT | NULL | 图片高度 |
| `duration_ms` | BIGINT | NULL | 视频时长 |
| `file_status` | VARCHAR(32) | NOT NULL | `INIT` / `UPLOADED` / `BOUND` / `DELETED` / `BLOCKED` |
| `audit_status` | VARCHAR(32) | NOT NULL | `SKIPPED` / `PENDING` / `PASSED` / `REJECTED` |
| `access_level` | VARCHAR(32) | NOT NULL | `PUBLIC` / `PRIVATE` |
| `uploaded_at` | DATETIME(3) | NULL | 上传确认时间 |
| `bound_at` | DATETIME(3) | NULL | 首次绑定时间 |
| `deleted_at` | DATETIME(3) | NULL | 删除时间 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除 |

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(file_id)` | 按 fileId 查询 |
| `uk_file_object_key(bucket, object_key)` | 对象路径唯一 |
| `idx_file_owner_scene(owner_id, scene, created_at)` | 查询用户某场景文件 |
| `idx_file_status_time(file_status, created_at)` | 清理任务扫描 |

### 5.2 file_upload_session

上传会话表。

关键字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `upload_id` | BIGINT | 主键 |
| `file_id` | BIGINT | 文件 ID |
| `owner_id` | BIGINT | 上传用户 |
| `upload_method` | VARCHAR(32) | `PRESIGNED_PUT` |
| `upload_status` | VARCHAR(32) | `INIT` / `UPLOADED` / `EXPIRED` / `FAILED` |
| `expected_size` | BIGINT | 申请时声明大小 |
| `expected_mime_type` | VARCHAR(128) | 申请时声明 MIME |
| `upload_url_expire_at` | DATETIME(3) | 上传 URL 过期时间 |
| `confirmed_at` | DATETIME(3) | 确认时间 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `idx_upload_file(file_id)` | 查询上传会话 |
| `idx_upload_status_expire(upload_status, upload_url_expire_at)` | 扫描过期会话 |

### 5.3 file_binding

文件业务绑定表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `file_id` | BIGINT | 文件 ID |
| `owner_id` | BIGINT | 文件 ownerId |
| `bind_type` | VARCHAR(64) | `USER_AVATAR` / `USER_HOME_COVER` / `NOTE_MEDIA` |
| `bind_id` | VARCHAR(128) | 业务对象 ID |
| `bind_status` | VARCHAR(32) | `BOUND` / `UNBOUND` |
| `bind_version` | BIGINT | 绑定版本 |
| `bound_at` | DATETIME(3) | 绑定时间 |
| `unbound_at` | DATETIME(3) | 解绑时间 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `uk_file_binding(file_id, bind_type, bind_id)` | 绑定幂等 |
| `idx_file_binding_file(file_id, bind_status)` | 查询文件绑定 |
| `idx_file_binding_biz(bind_type, bind_id)` | 按业务对象查询 |

### 5.4 file_outbox_event

文件服务本地事件表，主键为 `event_id`。

必须支持 `FileUploaded` 和 `FileBound`。

## 6. bluenote_note

### 6.1 note

笔记主表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `note_id` | BIGINT | PK | 笔记 ID |
| `author_id` | BIGINT | NOT NULL | 作者 ID |
| `note_type` | VARCHAR(32) | NOT NULL | `IMAGE_TEXT` / `VIDEO` |
| `note_status` | VARCHAR(32) | NOT NULL | 生命周期状态 |
| `visibility` | VARCHAR(32) | NOT NULL | `PUBLIC` / `PRIVATE` |
| `current_version` | INT | NOT NULL | 当前生效版本 |
| `latest_version` | INT | NOT NULL | 最新版本 |
| `cover_file_id` | BIGINT | NULL | 封面 fileId |
| `comment_enabled` | TINYINT | NOT NULL | 是否允许评论 |
| `published_at` | DATETIME(3) | NULL | 发布时间 |
| `last_edited_at` | DATETIME(3) | NULL | 最近编辑时间 |
| `offline_reason` | VARCHAR(256) | NULL | 下架原因 |
| `deleted_at` | DATETIME(3) | NULL | 删除时间 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除 |

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(note_id)` | 按笔记 ID 查询 |
| `idx_note_author_status_time(author_id, note_status, published_at, note_id)` | 作者笔记列表 |
| `idx_note_status_visibility_time(note_status, visibility, published_at, note_id)` | 公开笔记补偿、榜单候选 |
| `idx_note_updated(updated_at)` | 补偿和管理查询 |

### 6.2 note_version

笔记版本表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `note_id` | BIGINT | 笔记 ID |
| `version_no` | INT | 版本号 |
| `title` | VARCHAR(128) | 标题 |
| `content` | TEXT | 正文 |
| `content_preview` | VARCHAR(256) | 摘要 |
| `version_status` | VARCHAR(32) | `DRAFT` / `PENDING` / `ACTIVE` / `REJECTED` |
| `audit_status` | VARCHAR(32) | `SKIPPED` / `PENDING` / `PASSED` / `REJECTED` |
| `audit_reason` | VARCHAR(256) | 审核拒绝原因 |
| `created_by` | BIGINT | 创建人 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `uk_note_version(note_id, version_no)` | 版本唯一 |

### 6.3 note_media

笔记媒体表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `note_id` | BIGINT | 笔记 ID |
| `version_no` | INT | 版本号 |
| `file_id` | BIGINT | 文件 ID |
| `media_type` | VARCHAR(32) | `IMAGE` / `VIDEO` |
| `sort_order` | INT | 展示顺序 |
| `is_cover` | TINYINT | 是否封面 |
| `created_at` | DATETIME(3) | 创建时间 |

索引：

| 索引 | 说明 |
|---|---|
| `idx_note_media_version(note_id, version_no, sort_order)` | 查询版本媒体 |
| `idx_note_media_file(file_id)` | 文件反查绑定 |

### 6.4 note_topic

第一阶段简单话题表。

索引：

| 索引 | 说明 |
|---|---|
| `idx_note_topic_name(topic_name, note_id)` | 后续话题查询 |

### 6.5 note_like

笔记点赞明细表。

关键字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `note_id` | BIGINT | 笔记 ID |
| `author_id` | BIGINT | 笔记作者 ID |
| `user_id` | BIGINT | 点赞用户 ID |
| `like_status` | VARCHAR(32) | `ACTIVE` / `CANCELED` |
| `liked_at` | DATETIME(3) | 最近点赞时间 |
| `canceled_at` | DATETIME(3) | 最近取消时间 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `uk_note_like_user(note_id, user_id)` | 点赞幂等 |
| `idx_note_like_note_time(note_id, like_status, liked_at)` | 点赞列表和校准 |

### 6.6 note_collection

笔记收藏明细表。

索引：

| 索引 | 说明 |
|---|---|
| `uk_note_collection_user(note_id, user_id)` | 收藏幂等 |
| `idx_note_collection_user_time(user_id, collection_status, collected_at, note_id)` | 我的收藏列表 |

### 6.7 note_idempotent_request

请求幂等表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `idempotent_key` | VARCHAR(128) | 主键 |
| `user_id` | BIGINT | 用户 ID |
| `operation` | VARCHAR(64) | 操作类型 |
| `biz_id` | BIGINT | 关联业务 ID |
| `request_hash` | VARCHAR(128) | 请求摘要 |
| `request_status` | VARCHAR(32) | `PROCESSING` / `SUCCESS` / `FAIL` |
| `response_payload` | JSON | 成功响应快照 |
| `expire_at` | DATETIME(3) | 过期时间 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(idempotent_key)` | 请求幂等 |
| `idx_note_idem_expire(expire_at)` | 清理过期幂等记录 |

### 6.8 note_outbox_event

笔记服务 outbox 事件表。

必须支持：

1. `NotePublished`
2. `NoteUpdated`
3. `NoteDeleted`
4. `NoteLiked`
5. `NoteUnliked`
6. `NoteCollected`
7. `NoteUncollected`

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(event_id)` | 事件唯一 |
| `idx_note_outbox_status_retry(send_status, next_retry_at)` | 扫描待投递事件 |
| `idx_note_outbox_aggregate(aggregate_id, event_type)` | 排查某笔记事件 |

## 7. DDL 生成要求

后续生成 `backend/sql/` 时：

1. 文件按版本号命名，例如 `V001__auth.sql`。
2. 每个 schema 独立创建。
3. DDL 必须包含主键、唯一约束、必要索引。
4. 不创建跨 schema 外键。
5. 写接口幂等依赖的唯一约束必须在第一版 DDL 中落地。

