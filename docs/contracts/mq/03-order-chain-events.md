# 第四条订单链路 MQ 事件契约

版本：v0.1
状态：订单 foundation 开发基线

订单事件继承 `01-main-chain-events.md` 的通用 Envelope。

## 1. Topic 清单

| Topic | 生产者 | 事件类型 | 用途 |
|---|---|---|---|
| `order-seckill-task-event` | `bluenote-order` | `CouponSeckillAccepted` | Redis 预扣成功后的异步下单任务 |
| `order-timeout-event` | `bluenote-order` | `OrderTimeoutCheck` | 待支付订单超时关单检查 |
| `order-event` | `bluenote-order` | `OrderCreated` / `OrderPaid` / `OrderClosed` / `OrderCancelled` / `CouponIssued` | 订单和发券事实事件 |
| `push-request-event` | `bluenote-order` | `PushSendRequested` | 订单状态提醒 |

## 2. 消费组

| Consumer Group | Topic | 说明 |
|---|---|---|
| `bluenote-order-seckill-consumer` | `order-seckill-task-event` | 异步创建神券订单 |
| `bluenote-order-timeout-consumer` | `order-timeout-event` | 检查并关闭过期待支付订单 |

## 3. CouponSeckillAccepted

```json
{
  "eventId": "uuid",
  "eventType": "CouponSeckillAccepted",
  "eventVersion": 1,
  "occurredAt": "2026-06-12T20:00:01+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-order",
  "bizKey": "20001",
  "payload": {
    "requestId": "20001",
    "clientRequestId": "app-uuid",
    "userId": "10001",
    "activityId": "30001",
    "expectedPayAmount": 0
  }
}
```

## 4. OrderCreated

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "eventVersion": 1,
  "occurredAt": "2026-06-12T20:00:02+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-order",
  "bizKey": "40001",
  "payload": {
    "orderId": "40001",
    "orderNo": "BN2026061220000001",
    "requestId": "20001",
    "userId": "10001",
    "activityId": "30001",
    "payAmount": 0,
    "status": "SUCCESS"
  }
}
```

## 5. OrderTimeoutCheck

```json
{
  "eventId": "uuid",
  "eventType": "OrderTimeoutCheck",
  "eventVersion": 1,
  "occurredAt": "2026-06-12T20:00:05+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-order",
  "bizKey": "40001",
  "payload": {
    "orderId": "40001",
    "orderNo": "BN2026061220000001",
    "userId": "10001",
    "activityId": "30001",
    "expireAt": "2026-06-12T20:15:05+08:00"
  }
}
```

第一阶段通用 outbox 不携带 RocketMQ 延时级别，订单服务必须同时使用扫表任务兜底超时关单。

## 6. CouponIssued

```json
{
  "eventId": "uuid",
  "eventType": "CouponIssued",
  "eventVersion": 1,
  "occurredAt": "2026-06-12T20:00:02+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-order",
  "bizKey": "50001",
  "payload": {
    "userCouponId": "50001",
    "orderId": "40001",
    "userId": "10001",
    "activityId": "30001",
    "templateId": "60001",
    "validEndAt": "2026-06-19T23:59:59+08:00"
  }
}
```
