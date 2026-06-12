# 用户 API 契约

版本：v0.2
状态：第一、第二条主链路开发基线

移动端通过网关访问 `/api/users/**`。

## 1. 数据模型

### 1.1 UserProfile

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | string | 是 | 用户 ID |
| `bluenoteNo` | string | 是 | BlueNote 号 |
| `nickname` | string | 是 | 昵称 |
| `avatarFileId` | string / null | 是 | 头像文件 ID |
| `avatarUrl` | string / null | 是 | 头像访问 URL |
| `bio` | string / null | 是 | 简介 |
| `gender` | string | 是 | `UNKNOWN` / `MALE` / `FEMALE` |
| `birthday` | string / null | 是 | `yyyy-MM-dd` |
| `regionCode` | string / null | 是 | 地区编码 |
| `homeCoverFileId` | string / null | 是 | 主页背景文件 ID |
| `homeCoverUrl` | string / null | 是 | 主页背景访问 URL |
| `userStatus` | string | 是 | `NORMAL` / `DISABLED` / `DELETED` |
| `profileVersion` | number | 是 | 资料版本 |

### 1.2 UserSummary

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | string | 是 | 用户 ID |
| `bluenoteNo` | string | 是 | BlueNote 号 |
| `nickname` | string | 是 | 昵称 |
| `avatarUrl` | string / null | 是 | 头像 URL |
| `bio` | string / null | 是 | 简介 |
| `userStatus` | string | 是 | 用户状态 |
| `profileVersion` | number | 是 | 资料版本 |

## 2. 查询当前用户资料

```text
GET /api/users/me
```

鉴权：需要。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "10001",
    "bluenoteNo": "BN10001",
    "nickname": "小蓝",
    "avatarFileId": "90001",
    "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
    "bio": "记录生活",
    "gender": "UNKNOWN",
    "birthday": null,
    "regionCode": "CN-330100",
    "homeCoverFileId": null,
    "homeCoverUrl": null,
    "userStatus": "NORMAL",
    "profileVersion": 1
  },
  "traceId": "trace-id"
}
```

移动端要求：

1. App 启动恢复登录态后调用本接口校验用户状态。
2. `userStatus` 非 `NORMAL` 时限制发布、评论、点赞等操作。

## 3. 修改当前用户资料

```text
PUT /api/users/me/profile
```

鉴权：需要。

幂等：建议。移动端可通过防重复点击和 `X-Request-Id` 降低重复提交；后端使用 `profileVersion` 控制并发。

请求：

```json
{
  "nickname": "小蓝",
  "avatarFileId": "90001",
  "bio": "记录生活",
  "gender": "UNKNOWN",
  "birthday": null,
  "regionCode": "CN-330100",
  "homeCoverFileId": null,
  "baseProfileVersion": 1
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "10001",
    "profileVersion": 2,
    "updatedAt": "2026-06-05T10:00:00+08:00"
  },
  "traceId": "trace-id"
}
```

字段规则：

1. 未传字段不修改。
2. `nickname` 不允许为空。
3. `birthday`、`bio`、`regionCode`、`homeCoverFileId` 可以传 `null` 清空。
4. `avatarFileId` 和 `homeCoverFileId` 必须通过文件服务校验归属、场景、状态和类型。

可能错误码：

| code | reason |
|---|---|
| `21003` | `NICKNAME_INVALID` |
| `21004` | `BIO_INVALID` |
| `21005` | `AVATAR_FILE_INVALID` |
| `21006` | `HOME_COVER_FILE_INVALID` |
| `21007` | `PROFILE_VERSION_CONFLICT` |

## 4. 查询用户公开资料

```text
GET /api/users/{userId}/public
```

鉴权：可选。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "10001",
    "bluenoteNo": "BN10001",
    "nickname": "小蓝",
    "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
    "bio": "记录生活",
    "userStatus": "NORMAL",
    "profileVersion": 1
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `21001` | `USER_NOT_FOUND` |

## 5. 查询用户主页头部

```text
GET /api/users/{userId}/home
```

鉴权：可选。登录后返回当前用户与目标用户的关系状态。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "user": {
      "userId": "10001",
      "bluenoteNo": "BN10001",
      "nickname": "小蓝",
      "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
      "bio": "记录生活",
      "userStatus": "NORMAL",
      "profileVersion": 1
    },
    "counts": {
      "followingCount": 12,
      "followerCount": 256,
      "noteCount": 18,
      "likedCount": 1002
    },
    "relation": {
      "followStatus": "FOLLOWING"
    },
    "degraded": false
  },
  "traceId": "trace-id"
}
```

降级规则：

1. 计数服务不可用时 `counts` 返回 0，`degraded=true`。
2. 关系服务不可用时 `followStatus=UNKNOWN`，`degraded=true`。
3. 用户基础资料不可用时不能伪造成功，应返回 `21001` 或 `10007`。

## 6. 内部接口：创建默认用户资料

```text
POST /internal/users/register-profile
```

调用方：登录服务。

鉴权：内部服务鉴权，不暴露给移动端。

用途：注册成功时创建用户服务内的默认资料。登录服务不能直接写 `bluenote_user` schema。

请求：

```json
{
  "userId": "10001",
  "username": "blue_note_user",
  "registerChannel": "APP",
  "defaultNickname": "小蓝"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "10001",
    "bluenoteNo": "BN10001",
    "nickname": "小蓝",
    "userStatus": "NORMAL",
    "profileVersion": 1
  },
  "traceId": "trace-id"
}
```

幂等要求：

1. `user_id` 唯一。
2. 重复调用时，如果请求中的 `userId` 已有资料，返回现有资料。
3. 如果同一 `userId` 的请求内容和已有资料明显冲突，返回 `10008 DATA_CONFLICT`。

## 7. 内部接口：批量查询用户摘要

```text
POST /internal/users/batch-summary
```

调用方：笔记、评论、Feed、IM、通知等服务。

鉴权：内部服务鉴权，不暴露给移动端。

请求：

```json
{
  "userIds": ["10001", "10002", "10003"]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "users": [
      {
        "userId": "10001",
        "nickname": "小蓝",
        "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
        "bluenoteNo": "BN10001",
        "userStatus": "NORMAL",
        "profileVersion": 1,
        "status": "FOUND"
      },
      {
        "userId": "10002",
        "status": "NOT_FOUND"
      }
    ]
  },
  "traceId": "trace-id"
}
```

后端要求：

1. 返回顺序与请求 `userIds` 顺序一致。
2. 支持部分用户不存在。
3. 单次最多 100 个用户。

## 8. 内部接口：校验用户状态

```text
POST /internal/users/status-check
```

请求：

```json
{
  "userIds": ["10001", "10002"],
  "scene": "PUBLISH_NOTE"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "results": [
      {
        "userId": "10001",
        "exists": true,
        "userStatus": "NORMAL",
        "allowed": true
      },
      {
        "userId": "10002",
        "exists": true,
        "userStatus": "DISABLED",
        "allowed": false,
        "reason": "USER_DISABLED"
      }
    ]
  },
  "traceId": "trace-id"
}
```

`scene` 当前支持：

1. `PUBLISH_NOTE`
2. `UPDATE_PROFILE`
3. `COMMENT`
4. `FOLLOW`
5. `SEND_IM`
