# 用户关系 API 契约

版本：v0.2
状态：第二条主链路开发基线

移动端通过网关访问 `/api/relations/**`。关注关系事实归关系服务，用户资料只通过用户服务批量摘要补全。

## 1. 枚举

### 1.1 followStatus

| 值 | 说明 |
|---|---|
| `FOLLOWING` | 当前已关注 |
| `NOT_FOLLOWING` | 当前未关注 |
| `UNKNOWN` | 关系服务降级或未登录时无法判断 |

### 1.2 relationStatus

| 值 | 说明 |
|---|---|
| `ACTIVE` | 有效关注 |
| `CANCELED` | 已取消 |

## 2. 通用对象

### 2.1 RelationUserItem

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `user` | UserSummary | 是 | 用户摘要，结构见 `03-user-api.md` |
| `followStatus` | string | 是 | 当前登录用户对该用户的关注状态 |
| `followedAt` | string | 是 | 关注发生时间 |

### 2.2 FollowActionResult

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `followerId` | string | 是 | 关注人 ID |
| `followeeId` | string | 是 | 被关注人 ID |
| `followStatus` | string | 是 | 操作后的状态 |
| `followedAt` | string / null | 是 | 关注时间，取消关注时为 `null` |
| `canceledAt` | string / null | 是 | 取消时间，关注时为 `null` |

## 3. 关注用户

```text
POST /api/relations/following/{followeeId}
```

鉴权：需要。

幂等：需要，业务键为 `currentUserId + followeeId`，可附带 `Idempotency-Key`。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "followerId": "10001",
    "followeeId": "10002",
    "followStatus": "FOLLOWING",
    "followedAt": "2026-06-11T10:00:00+08:00",
    "canceledAt": null
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `25001` | `RELATION_TARGET_NOT_FOUND` |
| `25002` | `RELATION_TARGET_DISABLED` |
| `25003` | `RELATION_SELF_FOLLOW_FORBIDDEN` |
| `25005` | `RELATION_RATE_LIMITED` |

后端要求：

1. 先通过用户服务校验 `followeeId` 存在且状态允许关注。
2. 本地事务写 `relation_following` 和 `relation_change_log`。
3. 真实状态从非关注变为关注时发布 `UserFollowed`。
4. 重复关注返回当前 `FOLLOWING` 状态，不重复发布事件。

## 4. 取消关注

```text
DELETE /api/relations/following/{followeeId}
```

鉴权：需要。

幂等：需要。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "followerId": "10001",
    "followeeId": "10002",
    "followStatus": "NOT_FOLLOWING",
    "followedAt": null,
    "canceledAt": "2026-06-11T10:05:00+08:00"
  },
  "traceId": "trace-id"
}
```

后端要求：

1. 当前已经是未关注时直接返回 `NOT_FOLLOWING`。
2. 真实状态从关注变为未关注时发布 `UserUnfollowed`。
3. 取消关注不删除历史行，更新 `relation_status` 和版本。

## 5. 查询关注列表

```text
GET /api/relations/users/{userId}/following?cursor=xxx&size=20
```

鉴权：可选。登录后返回当前用户视角的 `followStatus`。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "user": {
          "userId": "10002",
          "bluenoteNo": "BN10002",
          "nickname": "小白",
          "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
          "bio": "记录生活",
          "userStatus": "NORMAL",
          "profileVersion": 1
        },
        "followStatus": "FOLLOWING",
        "followedAt": "2026-06-11T10:00:00+08:00"
      }
    ],
    "nextCursor": "2026-06-11T10:00:00+08:00_10002",
    "hasMore": true,
    "degraded": false
  },
  "traceId": "trace-id"
}
```

## 6. 查询粉丝列表

```text
GET /api/relations/users/{userId}/followers?cursor=xxx&size=20
```

鉴权：可选。响应结构同关注列表。

说明：

1. 粉丝列表以 `relation_follower` 为优先读取源。
2. 派生数据异常时可以回源 `relation_following`，并返回 `degraded=true`。
3. 用户资料补全失败时该用户项可以返回基础 ID 和 `status=DEGRADED`，但不能泄露禁用或删除用户。

## 7. 查询单个关注状态

```text
GET /api/relations/following/{targetUserId}/status
```

鉴权：需要。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "currentUserId": "10001",
    "targetUserId": "10002",
    "followStatus": "FOLLOWING",
    "followedAt": "2026-06-11T10:00:00+08:00"
  },
  "traceId": "trace-id"
}
```

## 8. 批量查询关注状态

```text
POST /api/relations/following/status/batch
```

鉴权：需要。

请求：

```json
{
  "targetUserIds": ["10002", "10003"]
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
        "targetUserId": "10002",
        "followStatus": "FOLLOWING",
        "followedAt": "2026-06-11T10:00:00+08:00"
      },
      {
        "targetUserId": "10003",
        "followStatus": "NOT_FOLLOWING",
        "followedAt": null
      }
    ]
  },
  "traceId": "trace-id"
}
```

限制：单次最多 100 个目标用户。

## 9. 内部接口

### 9.1 批量查询关注状态

```text
POST /internal/relations/following/status/batch
```

调用方：user、note、feed、notification、im 等服务。

请求：

```json
{
  "viewerId": "10001",
  "targetUserIds": ["10002", "10003"]
}
```

### 9.2 分页查询粉丝

```text
GET /internal/relations/users/{userId}/followers/page?cursor=xxx&size=500
```

调用方：feed。

返回字段：`followerId`、`followedAt`、`nextCursor`、`hasMore`。

### 9.3 分页查询关注

```text
GET /internal/relations/users/{userId}/following/page?cursor=xxx&size=500
```

调用方：feed、notification、im。

返回字段：`followeeId`、`followedAt`、`nextCursor`、`hasMore`。

### 9.4 计数来源接口

```text
POST /internal/relations/counter-source
```

调用方：counter。

请求：

```json
{
  "targets": [
    {
      "targetType": "USER",
      "targetId": "10001",
      "fields": ["following_count", "follower_count"]
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
        "targetType": "USER",
        "targetId": "10001",
        "counts": {
          "following_count": 12,
          "follower_count": 256
        }
      }
    ]
  },
  "traceId": "trace-id"
}
```

## 10. 移动端实现要求

1. 关注按钮必须防重复点击，可乐观切换，但失败时回滚。
2. 进入用户主页时优先使用 `/api/users/{userId}/home` 的关系状态，不额外重复请求。
3. 关注列表和粉丝列表统一使用游标分页。
4. `UNKNOWN` 状态不能展示为已关注或未关注，应保留按钮可刷新状态。
