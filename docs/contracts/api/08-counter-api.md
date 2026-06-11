# 计数 API 契约

版本：v0.2
状态：第二条主链路开发基线

计数服务第一阶段不暴露移动端公网接口。移动端看到的计数字段由笔记、用户、评论、Feed 等业务接口聚合返回。

## 1. 计数对象和字段

### 1.1 targetType

| 值 | 说明 |
|---|---|
| `NOTE` | 笔记维度 |
| `USER` | 用户维度 |
| `COMMENT` | 评论维度 |

### 1.2 字段白名单

| targetType | counterField | 说明 | 来源 |
|---|---|---|---|
| `NOTE` | `like_count` | 笔记点赞数 | `NoteLiked` / `NoteUnliked` |
| `NOTE` | `collect_count` | 笔记收藏数 | `NoteCollected` / `NoteUncollected` |
| `NOTE` | `comment_count` | 笔记评论数 | `CommentCreated` / `CommentDeleted` |
| `USER` | `following_count` | 关注数 | `UserFollowed` / `UserUnfollowed` |
| `USER` | `follower_count` | 粉丝数 | `UserFollowed` / `UserUnfollowed` |
| `USER` | `note_count` | 公开作品数 | `NotePublished` / `NoteDeleted` / 状态变化 |
| `USER` | `liked_count` | 作品获赞数 | `NoteLiked` / `NoteUnliked` |
| `COMMENT` | `like_count` | 评论点赞数 | `CommentLiked` / `CommentUnliked` |
| `COMMENT` | `reply_count` | 一级评论回复数 | 二级评论创建 / 删除 |

计数字段使用 snake_case。业务 API 返回给移动端时可以转换为 camelCase，例如 `likeCount`。

## 2. 内部接口：批量查询计数

```text
POST /internal/counters/batch
```

调用方：user、note、comment、feed、rank、notification 等服务。

鉴权：内部服务鉴权，不暴露给移动端。

请求：

```json
{
  "targets": [
    {
      "targetType": "NOTE",
      "targetId": "800001",
      "fields": ["like_count", "collect_count", "comment_count"]
    },
    {
      "targetType": "USER",
      "targetId": "10001",
      "fields": ["following_count", "follower_count", "note_count", "liked_count"]
    },
    {
      "targetType": "COMMENT",
      "targetId": "30001",
      "fields": ["like_count", "reply_count"]
    }
  ]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "targetType": "NOTE",
        "targetId": "800001",
        "counts": {
          "like_count": 12,
          "collect_count": 3,
          "comment_count": 5
        },
        "degraded": false
      },
      {
        "targetType": "USER",
        "targetId": "10001",
        "counts": {
          "following_count": 8,
          "follower_count": 20,
          "note_count": 4,
          "liked_count": 128
        },
        "degraded": false
      }
    ]
  },
  "traceId": "trace-id"
}
```

约束：

1. 单次最多 100 个 target。
2. 每个 target 只能查询所属类型支持的字段。
3. 返回顺序与请求顺序一致。
4. 不存在的计数默认返回 0。
5. Redis 不可用时可查 MySQL 快照并返回 `degraded=true`。

## 3. 内部接口：触发计数校准

```text
POST /internal/counters/reconcile
```

调用方：运维任务、管理端、计数服务内部异常检测任务。

请求：

```json
{
  "targetType": "NOTE",
  "targetId": "800001",
  "fields": ["like_count", "collect_count", "comment_count"],
  "reason": "MANUAL_CHECK"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "counter_rebuild_20260611_0001",
    "taskStatus": "PENDING"
  },
  "traceId": "trace-id"
}
```

## 4. 内部接口：查询重建任务

```text
GET /internal/counters/rebuild-tasks/{taskId}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "counter_rebuild_20260611_0001",
    "taskType": "RECONCILE",
    "targetType": "NOTE",
    "targetId": "800001",
    "taskStatus": "SUCCESS",
    "progress": {
      "total": 1,
      "success": 1,
      "failed": 0
    }
  },
  "traceId": "trace-id"
}
```

## 5. 内部接口：批量预热计数

```text
POST /internal/counters/warmup
```

请求：

```json
{
  "targets": [
    {
      "targetType": "NOTE",
      "targetId": "800001"
    }
  ]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "acceptedCount": 1
  },
  "traceId": "trace-id"
}
```

## 6. 错误码

| code | reason |
|---|---|
| `26001` | `COUNTER_BATCH_SIZE_EXCEEDED` |
| `26002` | `COUNTER_TARGET_TYPE_UNSUPPORTED` |
| `26003` | `COUNTER_FIELD_NOT_SUPPORTED` |
| `26004` | `COUNTER_TARGET_ID_INVALID` |
| `26005` | `COUNTER_QUERY_DEGRADED` |
| `26006` | `COUNTER_REBUILD_TASK_NOT_FOUND` |
| `26007` | `COUNTER_REBUILD_TOO_FREQUENT` |
| `26008` | `COUNTER_SOURCE_UNAVAILABLE` |

## 7. 事件处理要求

1. 计数服务消费 `note-event`、`interaction-event`、`comment-event`、`relation-event` 后生成内部 `CounterDeltaCreated`。
2. 上游业务服务不直接生产 `counter-delta-event`。
3. 同一个来源事件生成多条 delta 时，要么全部写入成功，要么整体重试。
4. Redis 计数不能作为唯一事实来源，必须能从来源服务或 MySQL 快照重建。
5. 计数查询失败不能阻断主内容展示，调用方应返回自己的主数据并标记降级。
