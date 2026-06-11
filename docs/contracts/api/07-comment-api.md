# 评论 API 契约

版本：v0.2
状态：第二条主链路开发基线

移动端通过网关访问 `/api/comments/**`。评论事实归评论服务，笔记是否可评论通过笔记服务内部接口校验。

## 1. 枚举

### 1.1 commentStatus

| 值 | 说明 |
|---|---|
| `VISIBLE` | 正常可见 |
| `DELETED` | 用户删除 |
| `HIDDEN` | 系统隐藏 |

### 1.2 commentSort

| 值 | 说明 |
|---|---|
| `HOT` | 热门排序，优先展示热度高的一级评论 |
| `TIME_DESC` | 按创建时间倒序 |
| `TIME_ASC` | 按创建时间正序 |

## 2. 通用对象

### 2.1 CommentAuthor

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | string | 是 | 用户 ID |
| `nickname` | string | 是 | 昵称 |
| `avatarUrl` | string / null | 是 | 头像 URL |
| `userStatus` | string | 是 | 用户状态 |

### 2.2 CommentItem

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `commentId` | string | 是 | 评论 ID |
| `noteId` | string | 是 | 笔记 ID |
| `rootId` | string | 是 | 一级评论 ID，一级评论为自身 ID |
| `parentCommentId` | string / null | 是 | 父评论 ID，一级评论为 `null` |
| `replyToUser` | CommentAuthor / null | 是 | 被回复用户 |
| `author` | CommentAuthor | 是 | 评论作者 |
| `content` | string | 是 | 评论正文 |
| `level` | number | 是 | 1 或 2 |
| `commentStatus` | string | 是 | 评论状态 |
| `likeCount` | number | 是 | 评论点赞数 |
| `replyCount` | number | 是 | 一级评论回复数，二级评论为 0 |
| `viewerAction` | object | 是 | `{ "liked": true }` |
| `createdAt` | string | 是 | 创建时间 |
| `degraded` | boolean | 是 | 作者或计数是否降级 |

## 3. 发布一级评论

```text
POST /api/comments/notes/{noteId}
```

鉴权：需要。

幂等：需要，使用 `clientRequestId` 或 `Idempotency-Key`。

请求：

```json
{
  "clientRequestId": "comment-req-001",
  "content": "拍得真好看"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "commentId": "30001",
    "noteId": "800001",
    "rootId": "30001",
    "parentCommentId": null,
    "level": 1,
    "commentStatus": "VISIBLE",
    "createdAt": "2026-06-11T10:20:00+08:00"
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `24004` | `COMMENT_CONTENT_INVALID` |
| `24006` | `COMMENT_NOTE_NOT_ALLOWED` |
| `24007` | `COMMENT_RATE_LIMITED` |
| `24009` | `COMMENT_IDEMPOTENCY_MISMATCH` |

后端要求：

1. 调用 `/internal/notes/comment-check` 校验笔记存在、公开、已发布且允许评论。
2. 本地事务写 `content_comment`、`user_comment`、`comment_content` 和 outbox。
3. 发布 `CommentCreated`，用于计数和通知。

## 4. 回复评论

```text
POST /api/comments/{commentId}/replies
```

鉴权：需要。

幂等：需要。

请求：

```json
{
  "clientRequestId": "reply-req-001",
  "content": "我也这么觉得"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "commentId": "30002",
    "noteId": "800001",
    "rootId": "30001",
    "parentCommentId": "30001",
    "level": 2,
    "commentStatus": "VISIBLE",
    "createdAt": "2026-06-11T10:22:00+08:00"
  },
  "traceId": "trace-id"
}
```

说明：

1. 路径中的 `commentId` 可以是一级评论或二级评论。
2. 回复二级评论时，`rootId` 仍为所属一级评论。
3. 通知接收人由评论服务在事件中携带 `replyToUserId`。

## 5. 删除评论

```text
DELETE /api/comments/{commentId}
```

鉴权：需要。评论作者可以删除自己的评论；后续管理端可通过内部接口隐藏评论。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "commentId": "30001",
    "commentStatus": "DELETED",
    "deletedAt": "2026-06-11T10:30:00+08:00"
  },
  "traceId": "trace-id"
}
```

后端要求：

1. 删除一级评论时可以隐藏该评论及其回复展示，但事件必须携带 `affectedCommentCount`。
2. 重复删除返回最终 `DELETED` 状态。
3. 真实状态变化时发布 `CommentDeleted`。

## 6. 查询笔记评论列表

```text
GET /api/comments/notes/{noteId}?sort=HOT&cursor=xxx&size=20
```

鉴权：可选。登录后返回 `viewerAction.liked`。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "commentId": "30001",
        "noteId": "800001",
        "rootId": "30001",
        "parentCommentId": null,
        "replyToUser": null,
        "author": {
          "userId": "10002",
          "nickname": "小白",
          "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
          "userStatus": "NORMAL"
        },
        "content": "拍得真好看",
        "level": 1,
        "commentStatus": "VISIBLE",
        "likeCount": 12,
        "replyCount": 3,
        "viewerAction": {
          "liked": true
        },
        "createdAt": "2026-06-11T10:20:00+08:00",
        "degraded": false
      }
    ],
    "nextCursor": "HOT_12_2026-06-11T10:20:00+08:00_30001",
    "hasMore": true,
    "degraded": false
  },
  "traceId": "trace-id"
}
```

说明：

1. 默认 `sort=HOT`。
2. 一级评论列表只返回一级评论；回复通过二级回复接口分页获取。
3. 计数降级时 `likeCount` 和 `replyCount` 返回 0，`degraded=true`。

## 7. 查询回复列表

```text
GET /api/comments/{rootCommentId}/replies?cursor=xxx&size=20
```

鉴权：可选。

响应结构同评论列表，`items` 中均为 `level=2` 的评论。

## 8. 评论点赞和取消点赞

```text
POST /api/comments/{commentId}/like
DELETE /api/comments/{commentId}/like
```

鉴权：需要。

幂等：需要，业务键为 `currentUserId + commentId`。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "commentId": "30001",
    "liked": true
  },
  "traceId": "trace-id"
}
```

真实状态变化时分别发布 `CommentLiked` 或 `CommentUnliked`。

## 9. 查询我的评论

```text
GET /api/comments/me?cursor=xxx&size=20
```

鉴权：需要。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "commentId": "30001",
        "noteId": "800001",
        "content": "拍得真好看",
        "commentStatus": "VISIBLE",
        "createdAt": "2026-06-11T10:20:00+08:00",
        "note": {
          "noteId": "800001",
          "title": "杭州周末咖啡店记录",
          "coverUrl": "https://oss.bluenote.example.com/cover.jpg"
        }
      }
    ],
    "nextCursor": "2026-06-11T10:20:00+08:00_30001",
    "hasMore": true
  },
  "traceId": "trace-id"
}
```

## 10. 内部接口

### 10.1 批量查询评论摘要

```text
POST /internal/comments/batch-summary
```

调用方：notification、feed、admin 等服务。

请求：

```json
{
  "commentIds": ["30001", "30002"]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "comments": [
      {
        "commentId": "30001",
        "noteId": "800001",
        "userId": "10002",
        "rootId": "30001",
        "contentPreview": "拍得真好看",
        "commentStatus": "VISIBLE",
        "createdAt": "2026-06-11T10:20:00+08:00"
      }
    ]
  },
  "traceId": "trace-id"
}
```

### 10.2 计数来源接口

```text
POST /internal/comments/counter-source
```

调用方：counter。

请求：

```json
{
  "targets": [
    {
      "targetType": "NOTE",
      "targetId": "800001",
      "fields": ["comment_count"]
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
          "comment_count": 5
        }
      },
      {
        "targetType": "COMMENT",
        "targetId": "30001",
        "counts": {
          "like_count": 12,
          "reply_count": 3
        }
      }
    ]
  },
  "traceId": "trace-id"
}
```

## 11. 移动端实现要求

1. 评论发布和回复按钮必须防重复点击。
2. 发送失败保留输入内容，允许重试。
3. 评论区必须处理 loading、empty、error、分页结束和网络失败。
4. 评论点赞可以乐观更新，但刷新后以后端返回为准。
5. 笔记详情页收到 `24006 COMMENT_NOTE_NOT_ALLOWED` 后应禁用评论入口。
