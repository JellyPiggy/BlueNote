# 通知 API 契约

版本：v0.3
状态：第二条主链路开发基线，订单通知最小纵切面开发基线

移动端通过网关访问 `/api/notifications/**`。通知服务负责站内通知和未读数，不负责 WebSocket、系统 Push 或 IM 消息。

## 1. 枚举

### 1.1 category

| 值 | 说明 |
|---|---|
| `INTERACTION` | 互动通知 |
| `FOLLOW` | 关注通知 |
| `SYSTEM` | 系统通知 |
| `ORDER` | 订单通知 |

### 1.2 notificationType

| 值 | category | 说明 |
|---|---|---|
| `NOTE_LIKED` | `INTERACTION` | 有人点赞你的笔记 |
| `NOTE_COLLECTED` | `INTERACTION` | 有人收藏你的笔记 |
| `NOTE_COMMENTED` | `INTERACTION` | 有人评论你的笔记 |
| `COMMENT_REPLIED` | `INTERACTION` | 有人回复你的评论 |
| `USER_FOLLOWED` | `FOLLOW` | 有人关注你 |
| `NOTE_AUDIT_REJECTED` | `SYSTEM` | 笔记审核失败 |
| `NOTE_OFFLINE` | `SYSTEM` | 笔记下架 |
| `SYSTEM_ANNOUNCEMENT` | `SYSTEM` | 系统公告 |
| `ORDER_STATUS_CHANGED` | `ORDER` | 神券订单状态变化 |

## 2. 通用对象

### 2.1 NotificationItem

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `notificationId` | string | 是 | 通知 ID |
| `category` | string | 是 | 通知分类 |
| `notificationType` | string | 是 | 通知类型 |
| `aggregate` | boolean | 是 | 是否聚合通知 |
| `actorCount` | number | 是 | 触发人数，明细通知为 1 |
| `title` | string | 是 | 列表标题 |
| `content` | string | 是 | 摘要 |
| `read` | boolean | 是 | 是否已读 |
| `actors` | array | 是 | 最近触发人摘要列表 |
| `target` | object | 是 | 目标对象摘要 |
| `jump` | object | 是 | 移动端跳转参数 |
| `createdAt` | string | 是 | 创建时间 |
| `lastEventAt` | string | 是 | 最近触发时间 |

订单通知约定：

1. `category` 为 `ORDER`。
2. `notificationType` 为 `ORDER_STATUS_CHANGED`。
3. `target.targetType` 为 `ORDER`，`target.targetId` 为 `orderId`。
4. `target` 可携带 `orderNo`、`activityId`、`status`、`userCouponId`、`validEndAt`。
5. `jump.page` 为 `ORDER_ACTIVITY`，移动端跳转到神券活动页；后续如补订单详情页，再扩展为 `ORDER_DETAIL`。

## 3. 查询未读数

```text
GET /api/notifications/unread-count
```

鉴权：需要。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalUnread": 12,
    "categories": {
      "INTERACTION": 8,
      "FOLLOW": 3,
      "SYSTEM": 1,
      "ORDER": 0
    }
  },
  "traceId": "trace-id"
}
```

## 4. 查询通知列表

```text
GET /api/notifications?category=INTERACTION&cursor=xxx&size=20
```

鉴权：需要。

请求参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `category` | 否 | 为空时查全部通知 |
| `cursor` | 否 | 首次请求不传 |
| `size` | 否 | 默认 20，最大 50 |

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "notificationId": "n_1001",
        "category": "INTERACTION",
        "notificationType": "NOTE_LIKED",
        "aggregate": true,
        "actorCount": 5,
        "title": "小白等 5 人点赞了你的笔记",
        "content": "杭州周末咖啡店记录",
        "read": false,
        "actors": [
          {
            "userId": "10002",
            "nickname": "小白",
            "avatarUrl": "https://oss.bluenote.example.com/avatar.png"
          }
        ],
        "target": {
          "targetType": "NOTE",
          "targetId": "800001",
          "title": "杭州周末咖啡店记录",
          "coverUrl": "https://oss.bluenote.example.com/cover.jpg"
        },
        "jump": {
          "page": "NOTE_DETAIL",
          "noteId": "800001"
        },
        "createdAt": "2026-06-11T10:00:00+08:00",
        "lastEventAt": "2026-06-11T10:10:00+08:00"
      }
    ],
    "nextCursor": "2026-06-11T10:10:00+08:00_n_1001",
    "hasMore": true
  },
  "traceId": "trace-id"
}
```

说明：

1. 已删除和归档通知默认不返回。
2. 聚合通知按 `lastEventAt` 排序。
3. 通知中的昵称、头像、笔记标题和封面是展示快照，点击后以目标业务服务最新数据为准。

## 5. 查询通知详情

```text
GET /api/notifications/{notificationId}
```

鉴权：需要，只能查询自己的通知。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "notificationId": "n_1002",
    "category": "INTERACTION",
    "notificationType": "NOTE_COMMENTED",
    "title": "小白评论了你的笔记",
    "content": "拍得真好看",
    "read": false,
    "snapshot": {
      "actor": {
        "userId": "10002",
        "nickname": "小白",
        "avatarUrl": "https://oss.bluenote.example.com/avatar.png"
      },
      "target": {
        "targetType": "NOTE",
        "targetId": "800001",
        "title": "杭州周末咖啡店记录"
      },
      "comment": {
        "commentId": "30001",
        "summary": "拍得真好看"
      }
    },
    "jump": {
      "page": "NOTE_DETAIL",
      "noteId": "800001",
      "commentId": "30001"
    }
  },
  "traceId": "trace-id"
}
```

## 6. 标记单条已读

```text
POST /api/notifications/{notificationId}/read
```

鉴权：需要。

幂等：需要，重复调用不能重复扣减未读数。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "notificationId": "n_1001",
    "read": true,
    "totalUnread": 11
  },
  "traceId": "trace-id"
}
```

## 7. 批量标记已读

```text
POST /api/notifications/read-all
```

鉴权：需要。

请求：

```json
{
  "category": "INTERACTION"
}
```

`category` 为空表示全部分类已读。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "updatedCount": 8,
    "totalUnread": 4,
    "categories": {
      "INTERACTION": 0,
      "FOLLOW": 3,
      "SYSTEM": 1,
      "ORDER": 0
    }
  },
  "traceId": "trace-id"
}
```

## 8. 删除通知

```text
DELETE /api/notifications/{notificationId}
```

鉴权：需要。

删除是用户侧隐藏，不物理删除通知事实。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "notificationId": "n_1001",
    "deleted": true
  },
  "traceId": "trace-id"
}
```

## 9. 批量删除通知

```text
DELETE /api/notifications
```

鉴权：需要。

请求：

```json
{
  "notificationIds": ["n_1001", "n_1002"]
}
```

限制：单次最多 50 条。

## 10. 内部接口

### 10.1 创建系统通知

```text
POST /internal/notifications/system
```

请求：

```json
{
  "requestId": "sys_req_1001",
  "notificationType": "SYSTEM_ANNOUNCEMENT",
  "receiverIds": ["10001", "10002"],
  "title": "系统通知",
  "content": "BlueNote 将在今晚进行短暂维护",
  "jump": {
    "page": "SYSTEM_NOTICE",
    "noticeId": "notice_1001"
  },
  "pushRequired": false,
  "expireAt": "2026-12-31T23:59:59+08:00"
}
```

### 10.2 批量查询通知摘要

```text
POST /internal/notifications/batch-summary
```

### 10.3 重建用户未读数

```text
POST /internal/notifications/users/{userId}/rebuild-unread
```

### 10.4 事件重放

```text
POST /internal/notifications/events/replay
```

内部接口必须使用服务身份认证，不允许网关暴露。

## 11. 移动端实现要求

1. 通知入口展示 `totalUnread`。
2. 通知页按 `互动`、`关注`、`系统`、`订单` 分 Tab。
3. 点击通知时先调用已读接口，再跳转目标页面。
4. 跳转后的目标内容不可见时，由目标页面展示“内容已删除或不可见”。
5. App 回到前台时重新拉取未读数。
