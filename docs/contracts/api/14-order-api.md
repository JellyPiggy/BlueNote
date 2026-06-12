# 订单 API 契约

版本：v0.1
状态：第四条订单链路 foundation 开发基线

本文定义 `bluenote-order` 第一阶段神券秒杀、订单查询、MOCK 支付和我的神券接口。所有外部接口必须通过网关 `/api/order/**` 访问，并使用统一响应结构。

## 1. 通用约定

金额字段单位为分，ID 字段统一为字符串。时间字段使用 ISO-8601 带时区格式，例如 `2026-06-12T20:00:00+08:00`。

活动状态：

| 状态 | 说明 |
|---|---|
| `NOT_STARTED` | 未开始 |
| `PREHEATED` | 已预热 |
| `ONLINE` | 可抢 |
| `SOLD_OUT` | 已抢光 |
| `PAUSED` | 暂停 |
| `ENDED` | 已结束 |

抢券结果状态：

| 状态 | 说明 |
|---|---|
| `PROCESSING` | 已接收，异步下单中 |
| `SUCCESS` | 抢券成功，免费券已到账或付费券已支付发券 |
| `WAIT_PAY` | 付费券待支付 |
| `SOLD_OUT` | 库存不足 |
| `DUPLICATE` | 已参与 |
| `CANCELLED` | 用户取消 |
| `CLOSED` | 超时关闭 |
| `FAILED` | 下单失败 |

订单状态：`WAIT_PAY`、`SUCCESS`、`CLOSED`、`CANCELLED`、`FAILED`。

券状态：`UNUSED`、`USED`、`EXPIRED`。

## 2. 查询当前神券活动

```http
GET /api/order/coupon-activities/current
```

响应：

```json
{
  "code": 0,
  "message": "SUCCESS",
  "data": {
    "activity": {
      "activityId": "10001",
      "activityName": "BlueNote 周末神券",
      "templateName": "满30减10神券",
      "faceValue": 1000,
      "thresholdAmount": 3000,
      "payAmount": 0,
      "status": "ONLINE",
      "startAt": "2026-06-12T20:00:00+08:00",
      "endAt": "2026-06-12T22:00:00+08:00",
      "userJoined": false,
      "description": "限时福利",
      "serverTime": "2026-06-12T20:01:00+08:00"
    }
  },
  "traceId": "trace-id"
}
```

没有可展示活动时 `activity=null`。

## 3. 获取秒杀 token

```http
POST /api/order/seckill/token
```

请求：

```json
{
  "activityId": "10001"
}
```

响应：

```json
{
  "code": 0,
  "message": "SUCCESS",
  "data": {
    "seckillToken": "token-string",
    "expiresInSeconds": 30
  },
  "traceId": "trace-id"
}
```

## 4. 发起抢券

```http
POST /api/order/seckill/orders
```

请求：

```json
{
  "activityId": "10001",
  "clientRequestId": "app-generated-uuid",
  "seckillToken": "token-string"
}
```

响应：

```json
{
  "code": 0,
  "message": "SUCCESS",
  "data": {
    "requestId": "20001",
    "status": "PROCESSING",
    "message": "抢券处理中"
  },
  "traceId": "trace-id"
}
```

幂等规则：

1. 同一用户重复提交相同 `clientRequestId`，返回同一请求结果。
2. 同一用户同一活动已成功、处理中或待支付时，返回已有请求结果。
3. `seckillToken` 一次性使用，过期或重复使用返回 `ORDER_SECKILL_TOKEN_INVALID`。

## 5. 查询抢券结果

```http
GET /api/order/seckill/results/{requestId}
```

响应：

```json
{
  "code": 0,
  "message": "SUCCESS",
  "data": {
    "requestId": "20001",
    "status": "WAIT_PAY",
    "orderId": "30001",
    "userCouponId": null,
    "payRequired": true,
    "payAmount": 990,
    "expireAt": "2026-06-12T20:16:00+08:00",
    "message": "待支付"
  },
  "traceId": "trace-id"
}
```

只能查询自己的请求。

## 6. 订单详情

```http
GET /api/order/orders/{orderId}
```

响应：

```json
{
  "code": 0,
  "message": "SUCCESS",
  "data": {
    "orderId": "30001",
    "orderNo": "BN2026061220000001",
    "activityId": "10001",
    "activityName": "BlueNote 周末神券",
    "templateName": "满30减10神券",
    "faceValue": 1000,
    "thresholdAmount": 3000,
    "payAmount": 990,
    "orderStatus": "WAIT_PAY",
    "expireAt": "2026-06-12T20:16:00+08:00",
    "paidAt": null,
    "successAt": null,
    "closedAt": null,
    "userCouponId": null,
    "createdAt": "2026-06-12T20:01:00+08:00"
  },
  "traceId": "trace-id"
}
```

只能查询自己的订单。

## 7. MOCK 支付

```http
POST /api/order/orders/{orderId}/pay
```

请求：

```json
{
  "channel": "MOCK"
}
```

响应：

```json
{
  "code": 0,
  "message": "SUCCESS",
  "data": {
    "orderId": "30001",
    "orderStatus": "SUCCESS",
    "paid": true,
    "userCouponId": "40001"
  },
  "traceId": "trace-id"
}
```

第一阶段只支持 `MOCK`。客户端不能传支付金额。

## 8. 取消待支付订单

```http
POST /api/order/orders/{orderId}/cancel
```

响应：

```json
{
  "code": 0,
  "message": "SUCCESS",
  "data": {
    "orderId": "30001",
    "orderStatus": "CANCELLED",
    "cancelled": true
  },
  "traceId": "trace-id"
}
```

只能取消自己的 `WAIT_PAY` 订单。

## 9. 我的神券列表

```http
GET /api/order/my-coupons?status=UNUSED&cursor=xxx&pageSize=20
```

响应：

```json
{
  "code": 0,
  "message": "SUCCESS",
  "data": {
    "items": [
      {
        "userCouponId": "40001",
        "templateId": "50001",
        "templateName": "满30减10神券",
        "activityName": "BlueNote 周末神券",
        "faceValue": 1000,
        "thresholdAmount": 3000,
        "couponStatus": "UNUSED",
        "validStartAt": "2026-06-12T20:01:00+08:00",
        "validEndAt": "2026-06-19T23:59:59+08:00",
        "issuedAt": "2026-06-12T20:01:00+08:00"
      }
    ],
    "nextCursor": "1760270460000_40001",
    "hasMore": false
  },
  "traceId": "trace-id"
}
```

`status` 可选，支持 `UNUSED`、`USED`、`EXPIRED`。分页游标为 `issuedAtMillis_userCouponId`。

## 10. 内部活动接口

```http
POST /internal/order/coupon-activities
POST /internal/order/coupon-activities/{activityId}/preheat
POST /internal/order/coupon-activities/{activityId}/pause
POST /internal/order/coupon-activities/{activityId}/resume
POST /internal/order/coupon-activities/{activityId}/end
POST /internal/order/timeout-tasks/scan-once
```

创建活动请求：

```json
{
  "activityName": "BlueNote 周末神券",
  "templateName": "满30减10神券",
  "faceValue": 1000,
  "thresholdAmount": 3000,
  "validDays": 7,
  "totalStock": 100,
  "perUserLimit": 1,
  "payAmount": 0,
  "startAt": "2026-06-12T20:00:00+08:00",
  "endAt": "2026-06-12T22:00:00+08:00",
  "payTimeoutMinutes": 15,
  "description": "限时福利"
}
```

创建活动响应：

```json
{
  "activityId": "10001",
  "templateId": "50001",
  "status": "READY"
}
```

状态操作响应：

```json
{
  "activityId": "10001",
  "status": "PREHEATED"
}
```

超时任务扫一次响应：

```json
{
  "scannedCount": 1,
  "closedCount": 1,
  "failedCount": 0
}
```
