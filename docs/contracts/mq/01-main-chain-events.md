# 第一条主链路 MQ 事件契约

版本：v0.1  
状态：第一条主链路开发基线

第一阶段主链路可以先通过 outbox 表可靠记录事件，RocketMQ 发送器和消费者可按开发阶段逐步开启。但事件 envelope、topic 和 payload 从第一版开始固定，避免后续返工。

## 1. Topic 清单

| Topic | 生产者 | 事件类型 | 第一阶段用途 |
|---|---|---|---|
| `auth-event` | `bluenote-auth` | `UserRegistered` / `UserLoggedIn` / `UserLoggedOut` | 用户创建、审计、后续通知 |
| `user-event` | `bluenote-user` | `UserProfileUpdated` / `UserStatusChanged` | 资料快照更新 |
| `file-event` | `bluenote-file` | `FileUploaded` / `FileBound` / `FileDeleted` | 文件审计、后续审核和清理 |
| `note-event` | `bluenote-note` | `NotePublished` / `NoteUpdated` / `NoteDeleted` | Feed、排行、通知、计数 |
| `interaction-event` | `bluenote-note` | `NoteLiked` / `NoteUnliked` / `NoteCollected` / `NoteUncollected` | 计数、通知、排行 |

## 2. Event Envelope

所有事件统一使用：

```json
{
  "eventId": "uuid",
  "eventType": "NotePublished",
  "eventVersion": 1,
  "occurredAt": "2026-06-05T10:00:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-note",
  "bizKey": "800001",
  "payload": {}
}
```

字段要求：

| 字段 | 要求 |
|---|---|
| `eventId` | 全局唯一，用于消费幂等 |
| `eventType` | 过去式事实事件 |
| `eventVersion` | 从 1 开始 |
| `occurredAt` | 事件发生时间 |
| `traceId` | 请求链路 ID |
| `producer` | 生产服务名 |
| `bizKey` | 业务键，用于分区、顺序或排查 |
| `payload` | 业务字段，不放大对象和文件正文 |

## 3. UserRegistered

Topic：`auth-event`

触发：注册成功，账号、密码、用户资料和会话创建完成。

```json
{
  "eventId": "uuid",
  "eventType": "UserRegistered",
  "eventVersion": 1,
  "occurredAt": "2026-06-05T10:00:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-auth",
  "bizKey": "10001",
  "payload": {
    "userId": "10001",
    "username": "blue_note_user",
    "registerChannel": "APP",
    "deviceId": "device-id"
  }
}
```

说明：

1. `username` 不用于公开展示。
2. 不包含密码、Token、Refresh Token。

## 4. UserProfileUpdated

Topic：`user-event`

触发：用户资料更新成功。

```json
{
  "eventId": "uuid",
  "eventType": "UserProfileUpdated",
  "eventVersion": 1,
  "occurredAt": "2026-06-05T10:00:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-user",
  "bizKey": "10001",
  "payload": {
    "userId": "10001",
    "bluenoteNo": "BN10001",
    "nickname": "小蓝",
    "avatarFileId": "90001",
    "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
    "bio": "记录生活",
    "userStatus": "NORMAL",
    "profileVersion": 2
  }
}
```

消费者处理：

1. 只接受更高 `profileVersion` 的快照。
2. 低版本事件应记录后忽略。

## 5. FileUploaded

Topic：`file-event`

触发：上传确认成功。

```json
{
  "eventId": "uuid",
  "eventType": "FileUploaded",
  "eventVersion": 1,
  "occurredAt": "2026-06-05T10:00:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-file",
  "bizKey": "900001",
  "payload": {
    "fileId": "900001",
    "ownerId": "10001",
    "scene": "NOTE_IMAGE",
    "mimeType": "image/jpeg",
    "fileSize": 2048000,
    "accessLevel": "PUBLIC",
    "fileStatus": "UPLOADED"
  }
}
```

说明：

1. 不传文件二进制。
2. `objectKey` 默认不放入事件，避免消费者依赖存储内部路径。

## 6. NotePublished

Topic：`note-event`

触发：公开笔记发布成功。

```json
{
  "eventId": "uuid",
  "eventType": "NotePublished",
  "eventVersion": 1,
  "occurredAt": "2026-06-05T10:01:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-note",
  "bizKey": "800001",
  "payload": {
    "noteId": "800001",
    "authorId": "10001",
    "title": "杭州周末咖啡店记录",
    "contentPreview": "这家店的拿铁不错...",
    "coverFileId": "900001",
    "visibility": "PUBLIC",
    "noteStatus": "PUBLISHED",
    "currentVersion": 1,
    "publishedAt": "2026-06-05T10:01:00+08:00"
  }
}
```

说明：

1. 私密草稿不发布 `NotePublished`。
2. 下游 Feed、排行必须再次根据 `visibility` 和 `noteStatus` 过滤。

## 7. NoteUpdated

Topic：`note-event`

触发：笔记编辑成功。

payload：

```json
{
  "noteId": "800001",
  "authorId": "10001",
  "title": "更新后的标题",
  "contentPreview": "更新后的摘要...",
  "coverFileId": "900002",
  "visibility": "PUBLIC",
  "noteStatus": "PUBLISHED",
  "currentVersion": 2,
  "updatedAt": "2026-06-05T10:10:00+08:00"
}
```

## 8. NoteDeleted

Topic：`note-event`

触发：笔记删除成功。

payload：

```json
{
  "noteId": "800001",
  "authorId": "10001",
  "deletedAt": "2026-06-05T10:12:00+08:00"
}
```

## 9. 互动事件

Topic：`interaction-event`

点赞：

```json
{
  "eventId": "uuid",
  "eventType": "NoteLiked",
  "eventVersion": 1,
  "occurredAt": "2026-06-05T10:20:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-note",
  "bizKey": "800001",
  "payload": {
    "noteId": "800001",
    "authorId": "10001",
    "userId": "10002",
    "occurredActionAt": "2026-06-05T10:20:00+08:00"
  }
}
```

事件类型：

1. `NoteLiked`
2. `NoteUnliked`
3. `NoteCollected`
4. `NoteUncollected`

幂等业务键：

```text
{eventType}:{noteId}:{userId}:{occurredActionAt}
```

消费者仍必须优先使用 `eventId + consumerGroup` 去重。

## 10. Outbox 要求

核心写操作必须同事务写业务表和 outbox：

1. 注册成功写 `auth_outbox_event`。
2. 用户资料更新写 `user_outbox_event`。
3. 文件上传确认和绑定写 `file_outbox_event`。
4. 笔记发布、编辑、删除、点赞、收藏写 `note_outbox_event`。

Outbox 发送流程：

```text
本地事务成功
  -> outbox status=INIT
  -> 后台任务扫描 INIT/FAILED
  -> 发送 RocketMQ
  -> 成功后标记 SENT
  -> 失败增加 retry_count 和 next_retry_at
```

## 11. 消费幂等

消费者统一使用：

```text
event_id + consumer_group
```

消费记录表建议字段：

| 字段 | 说明 |
|---|---|
| `event_id` | 事件 ID |
| `topic` | Topic |
| `consumer_group` | 消费组 |
| `event_type` | 事件类型 |
| `biz_key` | 业务键 |
| `consume_status` | `SUCCESS` / `FAIL` |
| `retry_count` | 重试次数 |
| `error_message` | 失败原因 |
| `consumed_at` | 消费成功时间 |
| `created_at` | 创建时间 |

## 12. 变更规则

事件变更必须：

1. 保持 `eventType` 含义稳定。
2. 新增字段必须可选或有默认值。
3. 删除字段、改字段类型、改语义必须升级 `eventVersion`。
4. 大对象、文件二进制、完整正文不能放入 MQ。

