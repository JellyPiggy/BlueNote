# IM API 契约

版本：v0.3
状态：第三条实时链路 IM 最小纵切面开发基线

移动端通过网关访问 `/api/im/**`。IM 服务负责会话、消息、未读和已读/送达事实；在线下发与离线提醒通过 `PushSendRequested` 交给推送服务。第一阶段只落地单聊文字消息，群聊、撤回、编辑、图片消息和复杂黑名单策略后续扩展。

## 1. 枚举

### 1.1 conversationType

| 值 | 说明 |
|---|---|
| `SINGLE` | 单聊 |
| `GROUP` | 群聊，后续 |

### 1.2 messageType

| 值 | 说明 |
|---|---|
| `TEXT` | 文字消息 |
| `IMAGE` | 图片消息，后续 |
| `NOTE_CARD` | 笔记卡片，后续 |

### 1.3 messageStatus

| 值 | 说明 |
|---|---|
| `NORMAL` | 正常 |
| `RECALLED` | 已撤回，后续 |
| `DELETED` | 发送方或系统删除，后续 |

## 2. 通用对象

### 2.1 ImUserSummary

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | string | 是 | 用户 ID |
| `nickname` | string | 是 | 昵称 |
| `avatarUrl` | string/null | 否 | 头像 |

### 2.2 ImMessageItem

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `messageId` | string | 是 | 消息 ID |
| `conversationId` | string | 是 | 会话 ID |
| `conversationSeq` | number | 是 | 会话内连续序号 |
| `senderId` | string | 是 | 发送人 |
| `receiverId` | string | 是 | 单聊接收人 |
| `messageType` | string | 是 | 消息类型 |
| `content` | object | 是 | 消息内容，TEXT 为 `{ "text": "..." }` |
| `summary` | string | 是 | 列表摘要 |
| `messageStatus` | string | 是 | 消息状态 |
| `mine` | boolean | 是 | 是否当前用户发送 |
| `sentAt` | string | 是 | 发送时间 |

### 2.3 ImConversationItem

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `conversationId` | string | 是 | 会话 ID |
| `conversationType` | string | 是 | 会话类型 |
| `peerUser` | object/null | 否 | 单聊对方摘要 |
| `lastMessage` | object/null | 否 | 最近一条消息 |
| `lastConversationSeq` | number | 是 | 当前会话最新序号 |
| `lastReadSeq` | number | 是 | 当前用户已读到的序号 |
| `lastReceivedSeq` | number | 是 | 当前用户已送达到的序号 |
| `unreadCount` | number | 是 | 当前会话未读数 |
| `pinned` | boolean | 是 | 是否置顶 |
| `mute` | boolean | 是 | 是否免打扰 |
| `hidden` | boolean | 是 | 是否用户侧隐藏 |
| `updatedAt` | string | 是 | 会话更新时间 |

## 3. 创建或获取单聊会话

```text
POST /api/im/conversations/single
```

鉴权：需要。

请求：

```json
{
  "targetUserId": "10002"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "conversationId": "900001",
    "conversationType": "SINGLE",
    "peerUser": {
      "userId": "10002",
      "nickname": "小白",
      "avatarUrl": "https://oss.bluenote.example.com/avatar.png"
    },
    "lastMessage": null,
    "lastConversationSeq": 0,
    "lastReadSeq": 0,
    "lastReceivedSeq": 0,
    "unreadCount": 0,
    "pinned": false,
    "mute": false,
    "hidden": false,
    "updatedAt": "2026-06-12T10:00:00+08:00"
  },
  "traceId": "trace-id"
}
```

规则：

1. `targetUserId` 不能是当前用户。
2. 单聊唯一键为 `min(userA,userB):max(userA,userB)`。
3. 重复调用返回已有会话，并将当前用户侧 `hidden=false`。

## 4. 查询会话列表

```text
GET /api/im/conversations?cursor=xxx&pageSize=20
```

鉴权：需要。

请求参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `cursor` | 否 | 首次请求不传，格式为 `lastMessageAt_conversationId` |
| `pageSize` | 否 | 默认 20，最大 50 |

成功响应：

```json
{
  "items": [
    {
      "conversationId": "900001",
      "conversationType": "SINGLE",
      "peerUser": {
        "userId": "10002",
        "nickname": "小白",
        "avatarUrl": null
      },
      "lastMessage": {
        "messageId": "910001",
        "conversationId": "900001",
        "conversationSeq": 8,
        "senderId": "10002",
        "receiverId": "10001",
        "messageType": "TEXT",
        "content": { "text": "晚上一起吃饭吗" },
        "summary": "晚上一起吃饭吗",
        "messageStatus": "NORMAL",
        "mine": false,
        "sentAt": "2026-06-12T10:01:00+08:00"
      },
      "lastConversationSeq": 8,
      "lastReadSeq": 4,
      "lastReceivedSeq": 8,
      "unreadCount": 4,
      "pinned": false,
      "mute": false,
      "hidden": false,
      "updatedAt": "2026-06-12T10:01:00+08:00"
    }
  ],
  "nextCursor": "2026-06-12T10:01:00+08:00_900001",
  "hasMore": true
}
```

## 5. 发送消息

```text
POST /api/im/messages
```

鉴权：需要。

请求：

```json
{
  "conversationId": "900001",
  "targetUserId": "10002",
  "clientMsgId": "device-uuid-001",
  "messageType": "TEXT",
  "content": {
    "text": "晚上一起吃饭吗"
  }
}
```

说明：

1. `conversationId` 和 `targetUserId` 至少传一个。
2. 没有 `conversationId` 时按 `targetUserId` 创建或获取单聊会话。
3. `clientMsgId` 是发送方维度幂等键，建议由移动端按设备生成 UUID。
4. 第一阶段只支持 `TEXT`，正文 1 到 1000 字符。

成功响应：

```json
{
  "message": {
    "messageId": "910001",
    "conversationId": "900001",
    "conversationSeq": 8,
    "senderId": "10001",
    "receiverId": "10002",
    "messageType": "TEXT",
    "content": { "text": "晚上一起吃饭吗" },
    "summary": "晚上一起吃饭吗",
    "messageStatus": "NORMAL",
    "mine": true,
    "sentAt": "2026-06-12T10:01:00+08:00"
  },
  "conversation": {
    "conversationId": "900001",
    "conversationType": "SINGLE",
    "peerUser": {
      "userId": "10002",
      "nickname": "小白",
      "avatarUrl": null
    },
    "lastMessage": null,
    "lastConversationSeq": 8,
    "lastReadSeq": 8,
    "lastReceivedSeq": 8,
    "unreadCount": 0,
    "pinned": false,
    "mute": false,
    "hidden": false,
    "updatedAt": "2026-06-12T10:01:00+08:00"
  }
}
```

## 6. 查询消息列表

```text
GET /api/im/conversations/{conversationId}/messages?afterSeq=0&beforeSeq=&limit=30
```

鉴权：需要，只能查询本人参与的会话。

请求参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `afterSeq` | 否 | 查询大于该序号的消息，升序返回 |
| `beforeSeq` | 否 | 查询小于该序号的历史消息，升序返回 |
| `limit` | 否 | 默认 30，最大 100 |

成功响应：

```json
{
  "items": [],
  "nextAfterSeq": 8,
  "nextBeforeSeq": 1,
  "hasMore": false
}
```

## 7. 上报送达

```text
POST /api/im/conversations/{conversationId}/received
```

请求：

```json
{
  "receivedSeq": 8
}
```

响应：

```json
{
  "conversationId": "900001",
  "lastReceivedSeq": 8
}
```

## 8. 标记已读

```text
POST /api/im/conversations/{conversationId}/read
```

请求：

```json
{
  "readSeq": 8
}
```

响应：

```json
{
  "conversationId": "900001",
  "lastReadSeq": 8,
  "unreadCount": 0,
  "totalUnread": 3
}
```

规则：重复标记同一序号必须幂等，不能重复扣减未读数。

## 9. 查询总未读

```text
GET /api/im/unread-count
```

响应：

```json
{
  "totalUnread": 3
}
```

## 10. 更新会话设置

```text
PUT /api/im/conversations/{conversationId}/settings
```

请求字段均可选：

```json
{
  "pinned": true,
  "mute": false
}
```

响应同 `ImConversationItem`。

## 11. 删除会话

```text
DELETE /api/im/conversations/{conversationId}
```

删除是当前用户侧隐藏，不物理删除消息事实。

响应：

```json
{
  "conversationId": "900001",
  "deleted": true
}
```

## 12. 内部接口

### 12.1 批量查询会话摘要

```text
POST /internal/im/conversations/batch-summary
```

请求：

```json
{
  "conversationIds": ["900001"]
}
```

### 12.2 查询成员 Push 策略

```text
GET /internal/im/conversations/{conversationId}/members/{userId}/push-policy
```

响应：

```json
{
  "conversationId": "900001",
  "userId": "10002",
  "mute": false,
  "pushAllowed": true
}
```

### 12.3 重建用户未读数

```text
POST /internal/im/users/{userId}/rebuild-unread
```

响应：

```json
{
  "userId": "10002",
  "totalUnread": 3,
  "rebuiltAt": "2026-06-12T10:05:00+08:00"
}
```

## 13. 降级和边界

1. IM 消息事实以 `im_message`、`im_conversation_message`、`im_user_message` 和 `im_conversation_member` 为准。
2. `PushSendRequested` 只用于提醒，失败不回滚消息发送。
3. 移动端收到 WebSocket `PUSH_MESSAGE` 后需要重新拉取会话或消息详情。
4. 第一阶段不做端到端加密，消息正文不得进入日志。
5. 内部接口不得暴露给移动端。
