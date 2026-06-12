# 排行榜 API 契约

版本：v0.1
状态：排行榜 foundation 开发基线

本文定义 `bluenote-rank` 第一阶段排行榜接口。物理部署在 `bluenote-social-app`，逻辑边界独立。移动端通过网关访问 `/api/ranks/**`，内部接口只允许服务间或运维调用。

## 1. 榜单定义

| rankCode | 名称 | memberType | periodType | 移动端可见 | 说明 |
|---|---|---|---|---|---|
| `WEEKLY_HOT_NOTE` | 本周热门笔记榜 | `NOTE` | `WEEKLY` | 是 | 按本周互动净增量加权排序，展示 Top 100 |
| `YEARLY_CREATOR_GROWTH` | 本年创作者成长榜 | `USER` | `YEARLY` | 是 | 按本年发布公开笔记获得的本年互动净增量加权排序，展示 Top 100 |

分数权重：

| counterField | 权重 |
|---|---:|
| `like_count` | 1 |
| `comment_count` | 2 |
| `collect_count` | 3 |

## 2. 公共字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `rankNo` | number | 从 1 开始的排名 |
| `score` | number | 真实整数分，不暴露 Redis 同分排序小数 |
| `periodId` | string | 周榜如 `2026W23`，年榜如 `2026` |
| `cursor` | string | 形如 `rankNo_memberId`，为空表示首页 |
| `rankMode` | string | `EXACT` / `ESTIMATED` / `NOT_RANKED` |
| `degraded` | boolean | 是否使用快照、MySQL 或部分下游降级结果 |

分页：

1. `size` 默认 20，最大 50。
2. 移动端榜单只返回 Top 100 范围内数据。
3. `nextCursor` 为空时没有下一页。

## 3. 查询本周热门笔记榜

```http
GET /api/ranks/weekly-hot-notes?cursor=&size=20
```

登录：可选。未登录时不返回个性化状态。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "rankCode": "WEEKLY_HOT_NOTE",
    "periodId": "2026W23",
    "items": [
      {
        "rankNo": 1,
        "noteId": "800001",
        "authorId": "10001",
        "authorNickname": "青蓝",
        "authorAvatarUrl": "https://cdn.example.com/avatar.jpg",
        "title": "杭州周末咖啡店记录",
        "coverUrl": "https://cdn.example.com/cover.jpg",
        "score": 328,
        "counts": {
          "likeCount": 120,
          "commentCount": 35,
          "collectCount": 46
        },
        "publishedAt": "2026-06-03T22:01:00+08:00"
      }
    ],
    "nextCursor": "1_800001",
    "hasMore": true,
    "degraded": false
  },
  "traceId": "trace-id"
}
```

展示要求：

1. 只展示仍为 `PUBLISHED + PUBLIC` 的笔记。
2. 查询链路必须二次调用笔记摘要接口校验可见性。
3. 展示计数来自计数服务或其降级结果，不使用排序分替代。

## 4. 查询本年创作者成长榜

```http
GET /api/ranks/yearly-creator-growth?cursor=&size=20
```

登录：可选。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "rankCode": "YEARLY_CREATOR_GROWTH",
    "periodId": "2026",
    "items": [
      {
        "rankNo": 1,
        "creatorId": "10001",
        "nickname": "青蓝",
        "avatarUrl": "https://cdn.example.com/avatar.jpg",
        "bio": "记录生活和咖啡",
        "score": 12080,
        "rankMode": "EXACT",
        "stats": {
          "publicNoteCount": 35,
          "followerCount": 920,
          "likedCount": 12800
        }
      }
    ],
    "nextCursor": "1_10001",
    "hasMore": true,
    "degraded": false
  },
  "traceId": "trace-id"
}
```

## 5. 查询本人本年成长排名

```http
GET /api/ranks/creators/me/yearly-growth-rank
```

登录：必须。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "rankCode": "YEARLY_CREATOR_GROWTH",
    "periodId": "2026",
    "creatorId": "10001",
    "rankNo": 128,
    "rankMode": "ESTIMATED",
    "score": 3280,
    "notRanked": false,
    "degraded": false
  },
  "traceId": "trace-id"
}
```

说明：

1. Top 100 内返回 `EXACT`。
2. Top 100 外但有分数时返回 `ESTIMATED`。
3. 没有分数或分数为 0 时返回 `NOT_RANKED`，`notRanked=true`，`rankNo=null`。

## 6. 批量查询成员排名

```http
POST /internal/ranks/members/batch-rank
```

请求：

```json
{
  "rankCode": "YEARLY_CREATOR_GROWTH",
  "periodId": "2026",
  "memberType": "USER",
  "memberIds": ["10001", "10002"]
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "rankCode": "YEARLY_CREATOR_GROWTH",
    "periodId": "2026",
    "members": [
      {
        "memberType": "USER",
        "memberId": "10001",
        "rankNo": 1,
        "rankMode": "EXACT",
        "score": 12080,
        "notRanked": false
      }
    ],
    "degraded": false
  },
  "traceId": "trace-id"
}
```

限制：单次最多 100 个成员。

## 7. 触发榜单重建

```http
POST /internal/ranks/rebuild
```

请求：

```json
{
  "rankCode": "WEEKLY_HOT_NOTE",
  "periodId": "2026W23",
  "taskType": "REBUILD_REDIS",
  "reason": "REDIS_DATA_LOST"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": "rank_rebuild_WEEKLY_HOT_NOTE_2026W23_20260612120000000",
    "rankCode": "WEEKLY_HOT_NOTE",
    "periodId": "2026W23",
    "taskType": "REBUILD_REDIS",
    "taskStatus": "SUCCESS",
    "progress": {
      "total": 42,
      "success": 42,
      "failed": 0
    }
  },
  "traceId": "trace-id"
}
```

## 8. 查询重建任务

```http
GET /internal/ranks/rebuild-tasks/{taskId}
```

响应字段同触发重建接口。

## 9. 创建榜单快照

```http
POST /internal/ranks/snapshots
```

请求：

```json
{
  "rankCode": "WEEKLY_HOT_NOTE",
  "periodId": "2026W23",
  "snapshotType": "MANUAL",
  "reason": "E2E_CHECK"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "snapshotId": "323840000000000001",
    "rankCode": "WEEKLY_HOT_NOTE",
    "periodId": "2026W23",
    "snapshotType": "MANUAL",
    "itemCount": 20,
    "createdAt": "2026-06-12T12:00:00+08:00"
  },
  "traceId": "trace-id"
}
```

## 10. 查询最近榜单快照

```http
GET /internal/ranks/snapshots/latest?rankCode=WEEKLY_HOT_NOTE&periodId=2026W23&snapshotType=MANUAL
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "snapshotId": "323840000000000001",
    "rankCode": "WEEKLY_HOT_NOTE",
    "periodId": "2026W23",
    "snapshotType": "MANUAL",
    "items": [
      {
        "rankNo": 1,
        "memberType": "NOTE",
        "memberId": "800001",
        "score": 328
      }
    ],
    "createdAt": "2026-06-12T12:00:00+08:00"
  },
  "traceId": "trace-id"
}
```

## 11. 手动消费事件

用于本地联调、MQ 重放和运维排查。

```http
POST /internal/ranks/events/consume
```

请求：

```json
{
  "consumerGroup": "bluenote-rank-counter-consumer",
  "topic": "counter-event",
  "eventId": "evt_counter_changed_NOTE_800001_1781166600000",
  "eventType": "CounterChanged",
  "occurredAt": "2026-06-11T11:10:00+08:00",
  "traceId": "trace-id",
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

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "eventId": "evt_counter_changed_NOTE_800001_1781166600000",
    "eventType": "CounterChanged",
    "consumeStatus": "SUCCESS",
    "updatedRanks": 2
  },
  "traceId": "trace-id"
}
```

## 12. 错误码

| code | reason | message |
|---|---|---|
| `27007` | `RANK_CODE_UNSUPPORTED` | 不支持的榜单 |
| `27008` | `RANK_PERIOD_NOT_FOUND` | 榜单周期不存在 |
| `27009` | `RANK_MEMBER_TYPE_INVALID` | 榜单成员类型不正确 |
| `27010` | `RANK_MEMBER_NOT_FOUND` | 榜单成员不存在 |
| `27011` | `RANK_NOTE_NOT_ELIGIBLE` | 笔记不具备上榜资格 |
| `27012` | `RANK_QUERY_SIZE_EXCEEDED` | 榜单查询数量超出限制 |
| `27013` | `RANK_CURSOR_INVALID` | 榜单游标不正确 |
| `27014` | `RANK_REBUILD_TASK_NOT_FOUND` | 榜单重建任务不存在 |
| `27015` | `RANK_REBUILD_TOO_FREQUENT` | 榜单重建过于频繁 |
| `27016` | `RANK_SCORE_RULE_INVALID` | 榜单分数规则不正确 |
| `27017` | `RANK_QUERY_DEGRADED` | 榜单查询已降级 |

## 13. 安全要求

1. 移动端只能通过网关访问 `/api/ranks/**`。
2. `/api/ranks/weekly-hot-notes` 和 `/api/ranks/yearly-creator-growth` 支持可选登录。
3. `/api/ranks/creators/me/yearly-growth-rank` 必须登录。
4. `/internal/ranks/**` 不允许移动端访问。
5. 排行榜不保存笔记正文、手机号、邮箱、Token 和完整预签名 URL。
