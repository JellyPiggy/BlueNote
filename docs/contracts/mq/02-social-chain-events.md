# 第二条主链路 MQ 事件契约

版本：v0.2
状态：第二条主链路开发基线

本文扩展第二条主链路需要的 Topic、事件类型、payload 和消费组。Event Envelope 继承 `01-main-chain-events.md`。

## 1. Topic 清单

| Topic | 生产者 | 事件类型 | 用途 |
|---|---|---|---|
| `relation-event` | `bluenote-relation` | `UserFollowed` / `UserUnfollowed` | 计数、Feed、通知 |
| `comment-event` | `bluenote-comment` | `CommentCreated` / `CommentDeleted` / `CommentLiked` / `CommentUnliked` / `CommentStatusChanged` | 计数、通知、审核后续 |
| `counter-delta-event` | `bluenote-counter` | `CounterDeltaCreated` | 计数服务内部异步写 |
| `counter-event` | `bluenote-counter` | `CounterChanged` / `CounterRebuilt` | 排行、展示读模型后续 |
| `feed-fanout-task-event` | `bluenote-feed` | `FeedFanoutSubTaskCreated` | Feed 服务内部扩散任务 |
| `feed-event` | `bluenote-feed` | `FeedDelivered` / `FeedRebuilt` | 运维监控、后续读模型 |
| `notification-event` | `bluenote-notification` | `NotificationCreated` / `NotificationAggregated` / `NotificationRead` / `NotificationReadBatch` / `NotificationDeleted` | 通知生命周期 |
| `push-request-event` | `bluenote-notification` | `PushSendRequested` | 推送服务后续消费 |

第二条链路还消费第一条链路已定义的：

1. `note-event`: `NotePublished`、`NoteDeleted`、`NoteUpdated`
2. `interaction-event`: `NoteLiked`、`NoteUnliked`、`NoteCollected`、`NoteUncollected`

第二条链路新增需要笔记服务发布：

1. `NoteVisibilityChanged`
2. `NoteStatusChanged`

## 2. NoteVisibilityChanged

Topic：`note-event`

触发：公开、私密等可见性真实变化。

```json
{
  "eventId": "uuid",
  "eventType": "NoteVisibilityChanged",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T10:00:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-note",
  "bizKey": "800001",
  "payload": {
    "noteId": "800001",
    "authorId": "10001",
    "fromVisibility": "PRIVATE",
    "toVisibility": "PUBLIC",
    "noteStatus": "PUBLISHED",
    "currentVersion": 2,
    "publishedAt": "2026-06-11T10:00:00+08:00"
  }
}
```

消费者要求：

1. Feed 只在 `PRIVATE -> PUBLIC` 且 `noteStatus=PUBLISHED` 时扩散。
2. Feed 在 `PUBLIC -> PRIVATE` 时标记不可见。
3. Counter 根据前后状态决定是否调整 `USER.note_count`。

## 3. NoteStatusChanged

Topic：`note-event`

触发：笔记审核、下架、恢复、删除等状态真实变化。

```json
{
  "eventId": "uuid",
  "eventType": "NoteStatusChanged",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T10:10:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-note",
  "bizKey": "800001",
  "payload": {
    "noteId": "800001",
    "authorId": "10001",
    "fromStatus": "PUBLISHED",
    "toStatus": "OFFLINE",
    "visibility": "PUBLIC",
    "currentVersion": 3,
    "reason": "CONTENT_RISK"
  }
}
```

消费者要求：

1. Feed 必须将不可展示状态从列表中过滤。
2. Counter 只对公开已发布状态进入或退出时调整 `USER.note_count`。
3. Notification 可对审核失败、下架生成系统通知。

## 4. UserFollowed

Topic：`relation-event`

```json
{
  "eventId": "uuid",
  "eventType": "UserFollowed",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T10:20:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-relation",
  "bizKey": "10001:10002",
  "payload": {
    "followerId": "10001",
    "followeeId": "10002",
    "followedAt": "2026-06-11T10:20:00+08:00",
    "relationVersion": 3
  }
}
```

消费者：

| 服务 | 用途 |
|---|---|
| counter | `follower.following_count +1`，`followee.follower_count +1` |
| feed | 新关注补作者近期公开笔记 |
| notification | 给被关注者生成关注通知 |

## 5. UserUnfollowed

Topic：`relation-event`

```json
{
  "eventId": "uuid",
  "eventType": "UserUnfollowed",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T10:30:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-relation",
  "bizKey": "10001:10002",
  "payload": {
    "followerId": "10001",
    "followeeId": "10002",
    "canceledAt": "2026-06-11T10:30:00+08:00",
    "relationVersion": 4
  }
}
```

消费者：

| 服务 | 用途 |
|---|---|
| counter | `follower.following_count -1`，`followee.follower_count -1` |
| feed | 清理或读取时过滤已取关作者笔记 |

通知服务第一阶段不消费 `UserUnfollowed`。

## 6. CommentCreated

Topic：`comment-event`

```json
{
  "eventId": "uuid",
  "eventType": "CommentCreated",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T10:40:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-comment",
  "bizKey": "30001",
  "payload": {
    "commentId": "30001",
    "noteId": "800001",
    "noteAuthorId": "10001",
    "userId": "10002",
    "rootId": "30001",
    "parentCommentId": null,
    "replyToUserId": null,
    "level": 1,
    "contentPreview": "拍得真好看",
    "commentStatus": "VISIBLE",
    "createdAt": "2026-06-11T10:40:00+08:00"
  }
}
```

二级回复时：

```json
{
  "commentId": "30002",
  "noteId": "800001",
  "noteAuthorId": "10001",
  "userId": "10003",
  "rootId": "30001",
  "parentCommentId": "30001",
  "replyToUserId": "10002",
  "level": 2,
  "contentPreview": "我也觉得",
  "commentStatus": "VISIBLE",
  "createdAt": "2026-06-11T10:45:00+08:00"
}
```

消费者：

| 服务 | 用途 |
|---|---|
| counter | 笔记评论数 +1，二级回复还增加一级评论回复数 |
| notification | 评论笔记或回复评论通知 |

## 7. CommentDeleted

Topic：`comment-event`

```json
{
  "eventId": "uuid",
  "eventType": "CommentDeleted",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T10:50:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-comment",
  "bizKey": "30001",
  "payload": {
    "commentId": "30001",
    "noteId": "800001",
    "userId": "10002",
    "rootId": "30001",
    "level": 1,
    "affectedCommentCount": 4,
    "affectedReplyCount": 3,
    "deletedAt": "2026-06-11T10:50:00+08:00"
  }
}
```

`affectedCommentCount` 表示本次删除对笔记可见评论数的影响。计数服务必须优先使用该字段。

## 8. CommentLiked / CommentUnliked

Topic：`comment-event`

```json
{
  "eventId": "uuid",
  "eventType": "CommentLiked",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T11:00:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-comment",
  "bizKey": "30001:10003",
  "payload": {
    "commentId": "30001",
    "commentUserId": "10002",
    "userId": "10003",
    "noteId": "800001",
    "occurredActionAt": "2026-06-11T11:00:00+08:00"
  }
}
```

事件类型：

1. `CommentLiked`
2. `CommentUnliked`

消费者：counter。通知服务第一阶段不为评论点赞生成通知。

## 9. CounterDeltaCreated

Topic：`counter-delta-event`

生产者和消费者均为 `bluenote-counter`，不作为跨服务协议。

```json
{
  "eventId": "evt_counter_delta_evt_note_liked_800001_10002_NOTE_800001_like_count",
  "eventType": "CounterDeltaCreated",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T11:10:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-counter",
  "bizKey": "NOTE:800001",
  "payload": {
    "deltaId": "evt_note_liked_800001_10002:NOTE:800001:like_count",
    "sourceEventId": "evt_note_liked_800001_10002",
    "sourceEventType": "NoteLiked",
    "targetType": "NOTE",
    "targetId": "800001",
    "counterField": "like_count",
    "deltaValue": 1
  }
}
```

## 10. CounterChanged

Topic：`counter-event`

```json
{
  "eventId": "evt_counter_changed_NOTE_800001_1781166600000",
  "eventType": "CounterChanged",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T11:10:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-counter",
  "bizKey": "NOTE:800001",
  "payload": {
    "targetType": "NOTE",
    "targetId": "800001",
    "changedFields": {
      "like_count": {
        "delta": 3,
        "currentValue": 128
      }
    }
  }
}
```

第一阶段不要求每个 delta 都发布 `CounterChanged`，允许按对象和字段合并发布。

## 11. FeedFanoutSubTaskCreated

Topic：`feed-fanout-task-event`

```json
{
  "eventId": "evt_feed_fanout_sub_task_800001_1",
  "eventType": "FeedFanoutSubTaskCreated",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T11:20:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-feed",
  "bizKey": "feed_fanout_800001",
  "payload": {
    "taskId": "feed_fanout_800001",
    "subTaskId": "feed_fanout_800001_1",
    "noteId": "800001",
    "authorId": "10001",
    "publishedAt": "2026-06-11T10:01:00+08:00",
    "targetUserIds": ["20001", "20002"]
  }
}
```

该 Topic 只在 Feed 服务内部使用。

## 12. FeedDelivered / FeedRebuilt

Topic：`feed-event`

`FeedDelivered` 第一阶段可按任务聚合发布，不逐用户发布。

```json
{
  "eventId": "evt_feed_delivered_800001",
  "eventType": "FeedDelivered",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T11:30:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-feed",
  "bizKey": "800001",
  "payload": {
    "taskId": "feed_fanout_800001",
    "noteId": "800001",
    "authorId": "10001",
    "deliveredCount": 128,
    "failedCount": 0
  }
}
```

`FeedRebuilt` payload：

```json
{
  "taskId": "feed_rebuild_10001_20260611",
  "userId": "10001",
  "rebuiltCount": 200,
  "rebuiltAt": "2026-06-11T11:40:00+08:00"
}
```

## 13. NotificationCreated / NotificationAggregated

Topic：`notification-event`

```json
{
  "eventId": "evt_notification_created_n_1001",
  "eventType": "NotificationCreated",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T11:50:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-notification",
  "bizKey": "n_1001",
  "payload": {
    "notificationId": "n_1001",
    "receiverId": "10001",
    "category": "INTERACTION",
    "notificationType": "NOTE_COMMENTED",
    "targetType": "NOTE",
    "targetId": "800001",
    "sourceBizId": "30001",
    "aggregate": false,
    "createdAt": "2026-06-11T11:50:00+08:00"
  }
}
```

`NotificationAggregated` 使用相同结构，并额外携带 `actorCount` 和 `lastEventAt`。

## 14. NotificationRead / NotificationReadBatch / NotificationDeleted

Topic：`notification-event`

单条已读：

```json
{
  "eventId": "evt_notification_read_n_1001",
  "eventType": "NotificationRead",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T12:00:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-notification",
  "bizKey": "n_1001",
  "payload": {
    "notificationId": "n_1001",
    "receiverId": "10001",
    "category": "INTERACTION",
    "readAt": "2026-06-11T12:00:00+08:00"
  }
}
```

批量已读和删除事件必须携带 `receiverId`，不能让消费者根据通知 ID 反查跨服务数据。

## 15. PushSendRequested

Topic：`push-request-event`

```json
{
  "eventId": "evt_push_n_1001",
  "eventType": "PushSendRequested",
  "eventVersion": 1,
  "occurredAt": "2026-06-11T11:50:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-notification",
  "bizKey": "push_req_n_1001",
  "payload": {
    "requestId": "push_req_n_1001",
    "sourceService": "bluenote-notification",
    "sourceBizType": "NOTIFICATION",
    "sourceBizId": "n_1001",
    "scene": "COMMENT_NOTIFICATION",
    "targetUserId": "10001",
    "targetDevicePolicy": "ALL_ACTIVE_DEVICES",
    "deliveryStrategy": "ONLINE_THEN_OFFLINE",
    "priority": 5,
    "title": "你收到一条新评论",
    "body": "有人评论了你的笔记",
    "data": {
      "notificationId": "n_1001",
      "targetType": "NOTE",
      "targetId": "800001"
    },
    "expireAt": "2026-06-11T12:00:00+08:00"
  }
}
```

推送服务尚未落地时，通知服务只需可靠写出 outbox。

## 16. 消费组建议

| consumerGroup | 消费 Topic | 所属服务 |
|---|---|---|
| `bluenote-counter-note-consumer` | `note-event` / `interaction-event` | counter |
| `bluenote-counter-comment-consumer` | `comment-event` | counter |
| `bluenote-counter-relation-consumer` | `relation-event` | counter |
| `bluenote-counter-delta-updater` | `counter-delta-event` | counter |
| `bluenote-feed-note-consumer` | `note-event` | feed |
| `bluenote-feed-relation-consumer` | `relation-event` | feed |
| `bluenote-feed-fanout-executor` | `feed-fanout-task-event` | feed |
| `bluenote-notification-interaction-consumer` | `interaction-event` | notification |
| `bluenote-notification-comment-consumer` | `comment-event` | notification |
| `bluenote-notification-relation-consumer` | `relation-event` | notification |
| `bluenote-notification-note-consumer` | `note-event` | notification |

## 17. 幂等和变更规则

1. 消费幂等统一使用 `consumer_group + event_id`。
2. 业务重复判断不能只依赖 Redis，必须有 MySQL 唯一约束或消费记录兜底。
3. 新增 payload 字段必须兼容旧消费者。
4. 删除字段、改变类型或改变语义必须升级 `eventVersion`。
5. 大对象、完整正文、文件二进制、Access Token 不能进入 MQ。
6. 生产者必须在本地事务内写 outbox，事务提交后异步发送 RocketMQ。
