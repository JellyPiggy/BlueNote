# Feed API 契约

版本：v0.2
状态：第二条主链路开发基线

移动端通过网关访问 `/api/feed/**`。第一阶段只冻结关注页 Feed，不包含推荐流。

## 1. 枚举

### 1.1 feedSourceType

| 值 | 说明 |
|---|---|
| `PUSH` | 发布时推送到收件箱 |
| `PULL` | 读取时从作者发件箱拉取 |
| `FOLLOW_BACKFILL` | 新关注作者后的历史补充 |

### 1.2 feedItemStatus

| 值 | 说明 |
|---|---|
| `VISIBLE` | 可展示 |
| `HIDDEN` | 不可展示 |
| `DELETED` | 目标笔记已删除 |

## 2. FeedCard

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `feedId` | string | 是 | Feed 项 ID，可由 `userId + noteId` 生成 |
| `noteId` | string | 是 | 笔记 ID |
| `author` | UserSummary | 是 | 作者摘要 |
| `title` | string | 是 | 笔记标题 |
| `contentPreview` | string | 是 | 正文摘要 |
| `coverUrl` | string / null | 是 | 封面 URL |
| `noteType` | string | 是 | `IMAGE_TEXT` / `VIDEO` |
| `counts` | object | 是 | `likeCount`、`collectCount`、`commentCount` |
| `viewerAction` | object / null | 是 | 第一阶段可返回 `null`，详情页再查 |
| `publishedAt` | string | 是 | 发布时间 |
| `sourceType` | string | 是 | Feed 来源 |
| `degraded` | boolean | 是 | 计数、作者或摘要是否降级 |

## 3. 查询关注页 Feed

```text
GET /api/feed/following?cursor=xxx&size=20
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
        "feedId": "10001_800001",
        "noteId": "800001",
        "author": {
          "userId": "10002",
          "bluenoteNo": "BN10002",
          "nickname": "小白",
          "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
          "bio": "记录生活",
          "userStatus": "NORMAL",
          "profileVersion": 1
        },
        "title": "杭州周末咖啡店记录",
        "contentPreview": "这家店的拿铁不错...",
        "coverUrl": "https://oss.bluenote.example.com/cover.jpg",
        "noteType": "IMAGE_TEXT",
        "counts": {
          "likeCount": 12,
          "collectCount": 3,
          "commentCount": 5
        },
        "viewerAction": null,
        "publishedAt": "2026-06-11T10:01:00+08:00",
        "sourceType": "PUSH",
        "degraded": false
      }
    ],
    "nextCursor": "1781162460000_800001",
    "hasMore": true,
    "degraded": false
  },
  "traceId": "trace-id"
}
```

请求参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `cursor` | 否 | 上滑加载更早内容时传入 |
| `size` | 否 | 默认 20，最大 50 |

游标格式：

```text
publishedAtMillis_noteId
```

排序规则：

```text
publishedAt DESC, noteId DESC
```

可能错误码：

| code | reason |
|---|---|
| `27001` | `FEED_CURSOR_INVALID` |
| `27002` | `FEED_SIZE_EXCEEDED` |
| `27003` | `FEED_QUERY_DEGRADED` |
| `27006` | `FEED_INTERNAL_DEPENDENCY_FAILED` |

读取规则：

1. 用户未关注任何人时返回空列表，不返回错误。
2. Feed 服务必须过滤已取关作者、私密笔记、删除笔记、下架笔记。
3. 计数、作者资料或笔记摘要部分失败时，可以返回可用内容并设置 `degraded=true`。
4. `viewerAction` 第一阶段可为 `null`，进入详情后由笔记详情返回点赞收藏状态。

## 4. 刷新关注页 Feed

刷新使用同一接口，不传 cursor：

```text
GET /api/feed/following?size=20
```

移动端下拉刷新必须以服务端返回的 `nextCursor` 覆盖本地游标。

## 5. 内部接口

### 5.1 触发用户 Feed 重建

```text
POST /internal/feed/users/{userId}/rebuild
```

请求：

```json
{
  "reason": "REDIS_LOST"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "feed_rebuild_10001_20260611",
    "taskStatus": "PENDING"
  },
  "traceId": "trace-id"
}
```

### 5.2 查询重建任务

```text
GET /internal/feed/rebuild-tasks/{taskId}
```

### 5.3 查询 Feed 投递任务

```text
GET /internal/feed/fanout-tasks/{taskId}
```

### 5.4 手动重试 Fanout 任务

```text
POST /internal/feed/fanout-tasks/{taskId}/retry
```

内部接口只能由运维任务或管理端调用，不允许网关暴露给移动端。

## 6. 后端实现要求

1. Feed 服务不保存笔记正文最终版本，只保存轻量索引。
2. 消费 `NotePublished` 时只处理 `visibility=PUBLIC` 且 `noteStatus=PUBLISHED` 的笔记。
3. 消费 `UserFollowed` 后补充作者近期公开笔记。
4. 消费 `UserUnfollowed` 后异步清理收件箱，并在读取时兜底过滤。
5. 消费笔记删除、私密、下架事件后标记不可见，读取时不能展示。
6. Redis 收件箱丢失时必须能从 MySQL 快照、作者发件箱、关系和笔记服务重建。

## 7. 移动端实现要求

1. 首次进入和下拉刷新不传 cursor。
2. 上滑加载必须使用服务端返回的 `nextCursor`。
3. 空列表展示关注引导或空态。
4. 降级时仍展示可用卡片，不因为计数或作者头像失败清空列表。
5. 列表项点击进入 `/api/notes/{noteId}` 详情。
