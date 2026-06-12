# 推送与实时投递 API 契约

版本：v0.1
状态：Push 最小纵切面开发基线

移动端通过网关访问 `/api/push/**`。推送服务第一阶段先承接设备、偏好和投递请求，不承诺真实厂商 Push 或 WebSocket 必达；业务事实仍以通知、IM、订单等来源服务为准。

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
    "realtimeEnabled": false,
    "websocketUrl": null,
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
4. 第一阶段 `realtimeEnabled=false`，WebSocket 地址预留。

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

## 8. 内部接口：提交投递请求

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

## 9. 内部接口：查询投递请求

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
      "channel": "NOOP",
      "attemptStatus": "SUCCESS",
      "skipReason": null,
      "errorMessage": null,
      "attemptedAt": "2026-06-12T10:00:01+08:00"
    }
  ],
  "createdAt": "2026-06-12T10:00:00+08:00",
  "completedAt": "2026-06-12T10:00:01+08:00"
}
```

## 10. 内部接口：重试投递请求

```text
POST /internal/push/requests/{requestId}/retry
```

规则：

1. 只重试 `FAILED`、`FILTERED`、`EXPIRED` 或 `DELIVERED` 的历史请求。
2. 重试不会修改原始业务事实，只新增尝试记录并刷新请求状态。

响应同提交投递请求。

## 11. 内部接口：事件重放

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
