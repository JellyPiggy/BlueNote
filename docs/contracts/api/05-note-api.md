# 笔记 API 契约

版本：v0.2
状态：第一、第二条主链路开发基线

移动端通过网关访问 `/api/notes/**`。

## 1. 枚举

### 1.1 visibility

| 值 | 说明 |
|---|---|
| `PUBLIC` | 公开 |
| `PRIVATE` | 私密，仅作者可见 |

### 1.2 noteStatus

| 值 | 说明 |
|---|---|
| `DRAFT` | 草稿 |
| `PUBLISHED` | 已发布 |
| `PRIVATE` | 私密 |
| `AUDIT_REJECTED` | 审核拒绝 |
| `OFFLINE` | 下架 |
| `DELETED` | 删除 |

### 1.3 mediaType

| 值 | 说明 |
|---|---|
| `IMAGE` | 图片 |
| `VIDEO` | 视频，第一阶段可暂不开放 |

## 2. 通用对象

### 2.1 NoteMediaInput

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `fileId` | string | 是 | 文件 ID，必须属于当前用户 |
| `mediaType` | string | 是 | `IMAGE` / `VIDEO` |
| `sortOrder` | number | 是 | 从 1 开始 |
| `cover` | boolean | 是 | 是否封面 |

### 2.2 NoteCard

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `noteId` | string | 是 | 笔记 ID |
| `title` | string | 是 | 标题 |
| `coverUrl` | string / null | 是 | 封面 URL |
| `authorId` | string | 是 | 作者 ID |
| `likeCount` | number | 是 | 点赞数 |
| `collectCount` | number | 是 | 收藏数 |
| `publishedAt` | string | 是 | 发布时间 |

## 3. 创建或保存草稿

```text
POST /api/notes/drafts
```

鉴权：需要。

幂等：需要，使用 `clientRequestId` 或 `Idempotency-Key`。

请求：

```json
{
  "clientRequestId": "req-20260605-001",
  "title": "杭州周末咖啡店记录",
  "content": "这家店的拿铁不错，适合下午坐一会儿。",
  "visibility": "PRIVATE",
  "commentEnabled": true,
  "mediaFiles": [
    {
      "fileId": "900001",
      "mediaType": "IMAGE",
      "sortOrder": 1,
      "cover": true
    }
  ],
  "topics": ["咖啡", "周末"]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "noteId": "800001",
    "noteStatus": "DRAFT",
    "latestVersion": 1,
    "updatedAt": "2026-06-05T10:00:00+08:00"
  },
  "traceId": "trace-id"
}
```

## 4. 发布新笔记

```text
POST /api/notes
```

鉴权：需要。

幂等：需要，使用 `clientRequestId` 或 `Idempotency-Key`。

请求：

```json
{
  "clientRequestId": "req-20260605-002",
  "title": "杭州周末咖啡店记录",
  "content": "这家店的拿铁不错，适合下午坐一会儿。",
  "visibility": "PUBLIC",
  "commentEnabled": true,
  "mediaFiles": [
    {
      "fileId": "900001",
      "mediaType": "IMAGE",
      "sortOrder": 1,
      "cover": true
    }
  ],
  "topics": ["咖啡", "周末"]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "noteId": "800001",
    "noteStatus": "PUBLISHED",
    "visibility": "PUBLIC",
    "currentVersion": 1,
    "publishedAt": "2026-06-05T10:01:00+08:00"
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `23005` | `NOTE_TITLE_INVALID` |
| `23006` | `NOTE_CONTENT_INVALID` |
| `23007` | `NOTE_MEDIA_INVALID` |
| `23008` | `NOTE_MEDIA_COUNT_EXCEEDED` |
| `23010` | `NOTE_PUBLISH_RATE_LIMITED` |
| `23013` | `NOTE_IDEMPOTENCY_MISMATCH` |

后端要求：

1. 先校验用户状态。
2. 通过文件服务批量校验媒体文件。
3. 本地事务写入笔记、版本、媒体和 outbox 事件。
4. 调用文件服务批量绑定文件。

移动端要求：

1. 发布按钮必须防重复点击。
2. 发布失败保留用户输入和已选媒体。

## 5. 发布草稿

```text
POST /api/notes/{noteId}/publish
```

鉴权：需要，只有作者可操作。

请求：

```json
{
  "clientRequestId": "req-20260605-003",
  "visibility": "PUBLIC"
}
```

成功响应同发布新笔记。

## 6. 编辑笔记

```text
PUT /api/notes/{noteId}
```

鉴权：需要，只有作者可操作。

幂等：需要，使用 `clientRequestId` 或 `Idempotency-Key`。并发编辑使用 `baseVersion`。

请求：

```json
{
  "clientRequestId": "req-20260605-004",
  "baseVersion": 1,
  "title": "杭州周末咖啡店记录",
  "content": "更新后的正文。",
  "visibility": "PUBLIC",
  "commentEnabled": true,
  "mediaFiles": [
    {
      "fileId": "900002",
      "mediaType": "IMAGE",
      "sortOrder": 1,
      "cover": true
    }
  ],
  "topics": ["咖啡"]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "noteId": "800001",
    "noteStatus": "PUBLISHED",
    "currentVersion": 2,
    "updatedAt": "2026-06-05T10:10:00+08:00"
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `23001` | `NOTE_NOT_FOUND` |
| `23003` | `NOTE_AUTHOR_FORBIDDEN` |
| `23009` | `NOTE_VERSION_CONFLICT` |

## 7. 删除笔记

```text
DELETE /api/notes/{noteId}
```

鉴权：需要，只有作者可操作。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "noteId": "800001",
    "noteStatus": "DELETED",
    "deletedAt": "2026-06-05T10:12:00+08:00"
  },
  "traceId": "trace-id"
}
```

## 8. 查询笔记详情

```text
GET /api/notes/{noteId}
```

鉴权：可选。登录后返回 `viewerAction`。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "noteId": "800001",
    "author": {
      "userId": "10001",
      "nickname": "小蓝",
      "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
      "userStatus": "NORMAL"
    },
    "title": "杭州周末咖啡店记录",
    "content": "这家店的拿铁不错，适合下午坐一会儿。",
    "visibility": "PUBLIC",
    "noteStatus": "PUBLISHED",
    "currentVersion": 1,
    "commentEnabled": true,
    "mediaFiles": [
      {
        "fileId": "900001",
        "mediaType": "IMAGE",
        "sortOrder": 1,
        "cover": true,
        "accessUrl": "https://oss.bluenote.example.com/..."
      }
    ],
    "topics": ["咖啡", "周末"],
    "counts": {
      "likeCount": 12,
      "collectCount": 3,
      "commentCount": 5
    },
    "viewerAction": {
      "liked": true,
      "collected": false
    },
    "publishedAt": "2026-06-05T10:01:00+08:00",
    "degraded": false
  },
  "traceId": "trace-id"
}
```

降级规则：

1. 计数查询失败时 `counts` 返回 0，`degraded=true`。
2. 当前用户互动状态查询失败时 `viewerAction` 返回 `liked=false`、`collected=false`，`degraded=true`。
3. 笔记本体不存在、删除、私密不可见时返回错误，不伪造成功。

## 9. 查询作者笔记列表

```text
GET /api/notes/users/{userId}?cursor=xxx&size=20
```

鉴权：可选。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "noteId": "800001",
        "title": "杭州周末咖啡店记录",
        "coverUrl": "https://oss.bluenote.example.com/...",
        "authorId": "10001",
        "likeCount": 12,
        "collectCount": 3,
        "publishedAt": "2026-06-05T10:01:00+08:00"
      }
    ],
    "nextCursor": "2026-06-05T10:01:00+08:00_800001",
    "hasMore": false
  },
  "traceId": "trace-id"
}
```

## 10. 查询我的笔记

```text
GET /api/notes/me?status=PUBLISHED&cursor=xxx&size=20
```

鉴权：需要。

`status` 可选：

```text
DRAFT
PUBLISHED
PRIVATE
AUDIT_REJECTED
OFFLINE
```

响应结构同作者笔记列表。

## 11. 点赞和取消点赞

```text
POST /api/notes/{noteId}/like
DELETE /api/notes/{noteId}/like
```

鉴权：需要。

幂等：需要。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "noteId": "800001",
    "liked": true
  },
  "traceId": "trace-id"
}
```

## 12. 收藏和取消收藏

```text
POST /api/notes/{noteId}/collect
DELETE /api/notes/{noteId}/collect
```

鉴权：需要。

幂等：需要。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "noteId": "800001",
    "collected": true
  },
  "traceId": "trace-id"
}
```

## 13. 查询我的收藏

```text
GET /api/notes/me/collections?cursor=xxx&size=20
```

鉴权：需要。

响应结构同作者笔记列表。

说明：

1. 只返回仍可见的收藏笔记。
2. 已删除、下架、私密且非本人可见的笔记不展示。
3. 按收藏时间倒序游标分页。

## 14. 查询我的赞过

```text
GET /api/notes/me/likes?cursor=xxx&size=20
```

鉴权：需要。

响应结构同作者笔记列表。

说明：

1. 只返回仍可见的点赞笔记。
2. 已删除、下架、私密且非本人可见的笔记不展示。
3. 按点赞时间倒序游标分页。

## 15. 内部接口：批量查询笔记摘要

```text
POST /internal/notes/batch-summary
```

调用方：Feed、排行榜、评论、通知等服务。

请求：

```json
{
  "noteIds": ["800001", "800002"],
  "viewerId": "10001",
  "includeInvisible": false
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "notes": [
      {
        "noteId": "800001",
        "authorId": "10001",
        "title": "杭州周末咖啡店记录",
        "contentPreview": "这家店的拿铁不错...",
        "coverFileId": "900001",
        "coverUrl": "https://oss.bluenote.example.com/...",
        "noteStatus": "PUBLISHED",
        "visibility": "PUBLIC",
        "publishedAt": "2026-06-05T10:01:00+08:00"
      }
    ]
  },
  "traceId": "trace-id"
}
```

## 16. 内部接口：校验笔记可评论

```text
POST /internal/notes/comment-check
```

调用方：评论服务。

请求：

```json
{
  "noteId": "800001",
  "viewerId": "10002"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "exists": true,
    "commentAllowed": true,
    "authorId": "10001",
    "noteStatus": "PUBLISHED",
    "visibility": "PUBLIC"
  },
  "traceId": "trace-id"
}
```

## 17. 内部接口：查询作者近期公开笔记

```text
POST /internal/notes/authors/recent
```

调用方：Feed 服务。

用途：用户新关注作者、Feed 重建或大 V 拉模式时补充作者近期公开笔记。

请求：

```json
{
  "authorIds": ["10001", "10002"],
  "limitPerAuthor": 20,
  "publishedAfter": "2026-03-13T00:00:00+08:00"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "authors": [
      {
        "authorId": "10001",
        "notes": [
          {
            "noteId": "800001",
            "title": "杭州周末咖啡店记录",
            "contentPreview": "这家店的拿铁不错...",
            "coverFileId": "900001",
            "coverUrl": "https://oss.bluenote.example.com/...",
            "noteStatus": "PUBLISHED",
            "visibility": "PUBLIC",
            "publishedAt": "2026-06-05T10:01:00+08:00"
          }
        ]
      }
    ]
  },
  "traceId": "trace-id"
}
```

约束：

1. 单次最多 50 个作者。
2. `limitPerAuthor` 最大 50。
3. 只返回 `visibility=PUBLIC` 且 `noteStatus=PUBLISHED` 的笔记。
4. 返回顺序按 `publishedAt DESC, noteId DESC`。

## 18. 内部接口：计数来源

```text
POST /internal/notes/counter-source
```

调用方：计数服务。

请求：

```json
{
  "targets": [
    {
      "targetType": "NOTE",
      "targetId": "800001",
      "fields": ["like_count", "collect_count"]
    },
    {
      "targetType": "USER",
      "targetId": "10001",
      "fields": ["note_count", "liked_count"]
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
          "collect_count": 3
        }
      },
      {
        "targetType": "USER",
        "targetId": "10001",
        "counts": {
          "note_count": 18,
          "liked_count": 1002
        }
      }
    ]
  },
  "traceId": "trace-id"
}
```

约束：

1. 单次最多 100 个 target。
2. `NOTE.like_count` 和 `NOTE.collect_count` 从点赞、收藏明细聚合。
3. `USER.note_count` 只统计公开已发布笔记。
4. `USER.liked_count` 统计用户公开作品累计获赞数。

## 19. 后端实现要求

1. 笔记发布、编辑、删除必须写 outbox 事件。
2. 文件必须通过文件服务校验和绑定。
3. 点赞、收藏必须通过唯一约束和幂等逻辑防重复。
4. Redis 只做缓存和限流，MySQL 是事实来源。
5. 私密、删除、下架笔记不能通过列表或详情泄露给无权限用户。

## 20. 移动端实现要求

1. 发布、编辑、点赞、收藏按钮必须防重复点击。
2. 发布失败要保留用户输入。
3. 详情页处理笔记不可见、已删除、加载失败和降级状态。
4. 列表统一使用游标分页。
