# 推送与实时投递 API 契约

版本：v0.2
状态：Push + WebSocket 实时投递开发基线

移动端通过网关访问 `/api/push/**` 和 `/ws/realtime`。推送服务当前支持设备、偏好、投递请求和小规模 WebSocket 在线下行；离线 uni-push / 厂商 Push 仍为配置关闭的扩展通道。业务事实仍以通知、IM、订单等来源服务为准。

## 1. 枚举

### 1.1 platform

| 值 | 说明 |
|---|---|
| `IOS` | iOS |
| `ANDROID` | Android |
| `H5` | H5 调试或浏览器端 |

### 1.2 pushProvider

| 值 | 说明 |
|---|---|
| `UNI_PUSH` | uni-push clientId |
| `APNS` | iOS 原生通道，后续 |
| `FCM` | FCM，后续 |
| `VENDOR_PUSH` | 国内厂商通道，后续 |
| `NOOP` | 本地或 H5 占位通道 |

### 1.3 deliveryStrategy

| 值 | 说明 |
|---|---|
| `ONLINE_ONLY` | 只尝试在线投递 |
| `OFFLINE_PUSH_ONLY` | 只尝试离线系统 Push |
| `ONLINE_THEN_OFFLINE` | 在线优先，失败后离线 Push |
| `ONLINE_AND_OFFLINE` | 在线和离线都投递 |
| `NO_PUSH` | 只记录请求，不投递 |

### 1.4 requestStatus

| 值 | 说明 |
|---|---|
| `RECEIVED` | 已接收 |
| `PROCESSING` | 处理中 |
| `DELIVERED` | 已被推送服务接收并生成通道尝试 |
| `FILTERED` | 因偏好、策略或无设备被过滤 |
| `FAILED` | 处理失败 |
| `EXPIRED` | 请求已过期 |

### 1.5 attemptStatus

| 值 | 说明 |
|---|---|
| `SENT_TO_CONNECTION` | 已写入 WebSocket 连接 |
| `ACKED` | 客户端已确认收到 WebSocket 消息 |
| `SENT_TO_PROVIDER` | 已提交离线 Push 通道 |
| `SKIPPED` | 策略、偏好、离线或通道不可用跳过 |
| `FAILED` | 通道发送失败 |

## 2. 注册或刷新设备

```text
POST /api/push/devices/register
```

鉴权：需要登录。

请求：

```json
{
  "deviceId": "device_h5_local_001",
  "platform": "H5",
  "pushProvider": "NOOP",
  "providerClientId": null,
  "appVersion": "0.1.0",
  "osVersion": "Chrome",
  "deviceModel": "Desktop"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "deviceId": "device_h5_local_001",
    "userId": "10001",
    "platform": "H5",
    "pushProvider": "NOOP",
    "deviceStatus": "ACTIVE",
    "realtimeEnabled": true,
    "websocketUrl": "/ws/realtime",
    "registeredAt": "2026-06-12T10:00:00+08:00",
    "lastActiveAt": "2026-06-12T10:00:00+08:00"
  },
  "traceId": "trace-id"
}
```

规则：

1. 服务端以网关注入的当前用户作为绑定用户，不信任移动端传 `userId`。
2. 同一 `deviceId` 重复注册时更新 provider token、版本和最近活跃时间。
3. 同一设备切换用户登录时，当前记录更新为新用户绑定。
4. 当前 `realtimeEnabled=true`，移动端可使用返回的 `websocketUrl` 建立实时连接。

## 3. 查询我的设备

```text
GET /api/push/devices
```

响应：

```json
{
  "devices": [
    {
      "deviceId": "device_h5_local_001",
      "platform": "H5",
      "pushProvider": "NOOP",
      "deviceStatus": "ACTIVE",
      "appVersion": "0.1.0",
      "lastActiveAt": "2026-06-12T10:00:00+08:00"
    }
  ]
}
```

## 4. 解绑设备

```text
DELETE /api/push/devices/{deviceId}
```

幂等：需要。重复解绑返回 `deviceStatus=UNBOUND`。

响应：

```json
{
  "deviceId": "device_h5_local_001",
  "deviceStatus": "UNBOUND",
  "unboundAt": "2026-06-12T10:05:00+08:00"
}
```

## 5. 查询推送偏好

```text
GET /api/push/preferences
```

响应：

```json
{
  "globalEnabled": true,
  "interactionEnabled": true,
  "followEnabled": true,
  "systemEnabled": true,
  "orderEnabled": true,
  "imEnabled": true,
  "showImDetail": true,
  "quietHoursEnabled": false,
  "quietStart": null,
  "quietEnd": null,
  "updatedAt": "2026-06-12T10:00:00+08:00"
}
```

## 6. 更新推送偏好

```text
PUT /api/push/preferences
```

请求字段均可选，未传字段保持当前值：

```json
{
  "globalEnabled": true,
  "interactionEnabled": false,
  "followEnabled": true,
  "systemEnabled": true,
  "orderEnabled": true,
  "imEnabled": true,
  "showImDetail": false,
  "quietHoursEnabled": true,
  "quietStart": "23:00",
  "quietEnd": "08:00"
}
```

响应同查询接口。

规则：

1. 关闭 Push 偏好不影响站内通知和业务事实。
2. `quietStart`、`quietEnd` 使用 `HH:mm`。
3. 第一阶段免打扰只过滤普通优先级离线 Push 请求。

## 7. 点击回传

```text
POST /api/push/clicks
```

请求：

```json
{
  "requestId": "push_req_10001",
  "deviceId": "device_h5_local_001",
  "clickedAt": "2026-06-12T10:10:00+08:00",
  "data": {
    "notificationId": "10001"
  }
}
```

响应：

```json
{
  "requestId": "push_req_10001",
  "recorded": true
}
```

## 8. WebSocket 实时连接

```text
GET /ws/realtime?deviceId={deviceId}&accessToken={accessToken}
```

说明：

1. H5 端通过 query 携带 `accessToken`；App 端后续可改用 `Authorization: Bearer`。
2. 服务端校验 access token 中的 `userId`、`deviceId` 和已注册 ACTIVE 设备。
3. 连接建立后 Redis 写入在线设备路由，断开后清理。

服务端连接成功消息：

```json
{
  "type": "CONNECTED",
  "connectionId": "session-id",
  "deviceId": "device_h5_local_001",
  "serverTime": "2026-06-12T10:00:00+08:00"
}
```

客户端心跳：

```json
{
  "type": "PING",
  "clientTime": "2026-06-12T10:00:20+08:00"
}
```

服务端下行：

```json
{
  "type": "PUSH_MESSAGE",
  "requestId": "push_req_n_1001",
  "scene": "COMMENT_NOTIFICATION",
  "title": "你收到一条新评论",
  "body": "有人评论了你的笔记",
  "data": {
    "notificationId": "1001"
  },
  "sentAt": "2026-06-12T10:00:01+08:00"
}
```

客户端 ACK：

```json
{
  "type": "ACK",
  "requestId": "push_req_n_1001",
  "receivedAt": "2026-06-12T10:00:02+08:00"
}
```

ACK 只表示客户端收到提醒，不等于通知已读、消息已读或业务状态已处理。

## 9. 内部接口：提交投递请求

```text
POST /internal/push/requests/send
```

调用方：notification、IM、order、运维脚本。

请求：

```json
{
  "requestId": "push_req_n_1001",
  "sourceService": "bluenote-notification",
  "sourceBizType": "NOTIFICATION",
  "sourceBizId": "1001",
  "scene": "COMMENT_NOTIFICATION",
  "targetUserId": "10001",
  "targetDevicePolicy": "ALL_ACTIVE_DEVICES",
  "deliveryStrategy": "ONLINE_THEN_OFFLINE",
  "priority": 5,
  "title": "你收到一条新评论",
  "body": "有人评论了你的笔记",
  "data": {
    "notificationId": "1001",
    "targetType": "NOTE",
    "targetId": "800001"
  },
  "expireAt": "2026-06-12T10:10:00+08:00"
}
```

响应：

```json
{
  "requestId": "push_req_n_1001",
  "requestStatus": "DELIVERED",
  "deliveredDeviceCount": 1,
  "filteredReason": null
}
```

说明：MQ 消费 `PushSendRequested` 复用同一核心逻辑。

## 10. 内部接口：查询投递请求

```text
GET /internal/push/requests/{requestId}
```

响应：

```json
{
  "requestId": "push_req_n_1001",
  "requestStatus": "DELIVERED",
  "targetUserId": "10001",
  "sourceBizType": "NOTIFICATION",
  "sourceBizId": "1001",
  "scene": "COMMENT_NOTIFICATION",
  "title": "你收到一条新评论",
  "body": "有人评论了你的笔记",
  "deliveryStrategy": "ONLINE_THEN_OFFLINE",
  "attempts": [
    {
      "attemptId": "320001",
      "deviceId": "device_h5_local_001",
      "channel": "WEBSOCKET",
      "attemptStatus": "ACKED",
      "skipReason": null,
      "errorMessage": null,
      "attemptedAt": "2026-06-12T10:00:01+08:00",
      "ackedAt": "2026-06-12T10:00:02+08:00"
    }
  ],
  "createdAt": "2026-06-12T10:00:00+08:00",
  "completedAt": "2026-06-12T10:00:01+08:00"
}
```

## 11. 内部接口：重试投递请求

```text
POST /internal/push/requests/{requestId}/retry
```

规则：

1. 只重试 `FAILED`、`FILTERED`、`EXPIRED` 或 `DELIVERED` 的历史请求。
2. 重试不会修改原始业务事实，只新增尝试记录并刷新请求状态。

响应同提交投递请求。

## 12. 内部接口：事件重放

```text
POST /internal/push/events/replay
```

请求：

```json
{
  "topic": "push-request-event",
  "eventId": "evt_push_n_1001"
}
```

## 13. 内部接口：查询在线状态

```text
GET /internal/push/users/{userId}/online-state
```

响应：

```json
{
  "userId": "10001",
  "online": true,
  "devices": [
    {
      "deviceId": "device_h5_local_001",
      "connectionId": "session-id",
      "connectedAt": "2026-06-12T10:00:00+08:00",
      "lastSeenAt": "2026-06-12T10:00:20+08:00"
    }
  ]
}
```

## 14. 内部接口：踢下线

```text
POST /internal/push/users/{userId}/kick
```

请求：

```json
{
  "deviceId": "device_h5_local_001",
  "reason": "ADMIN_KICK"
}
```

`deviceId` 为空时踢该用户当前实例内全部在线连接。

响应：

```json
{
  "eventId": "evt_push_n_1001",
  "eventType": "PushSendRequested",
  "status": "SUCCESS",
  "requestId": "push_req_n_1001"
}
```

## 12. 降级和边界

1. 第一阶段通道为 `NOOP` 时，`DELIVERED` 表示推送服务已接收并记录投递尝试，不表示用户真实收到系统通知。
2. Push 失败不回滚通知、IM 或订单事实。
3. 过期请求应标记 `EXPIRED`，不再投递。
4. 用户关闭对应偏好时标记 `FILTERED`，站内通知仍正常存在。
5. 内部接口不得暴露给移动端。
