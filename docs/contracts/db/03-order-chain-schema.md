# 第四条订单链路数据库契约

版本：v0.1
状态：订单 foundation 开发基线

订单服务独占 `bluenote_order` schema。Redis、MQ 和 outbox 都不是订单事实来源，订单、库存、支付和用户券事实必须能从 MySQL 重建。

## 1. Schema

| Schema | 归属服务 | 用途 |
|---|---|---|
| `bluenote_order` | `bluenote-order` | 神券模板、秒杀活动、订单、支付、用户券、库存流水、消费幂等和 outbox |

禁止跨 schema 外键和跨 schema join。

## 2. 表清单

| 表 | 说明 |
|---|---|
| `coupon_template` | 神券模板 |
| `coupon_activity` | 秒杀活动和 MySQL 可用库存事实 |
| `coupon_seckill_request` | 移动端抢券请求和异步结果 |
| `voucher_order` | 神券订单 |
| `payment_record` | 支付流水，第一阶段支持 `MOCK` |
| `user_coupon` | 用户已获得神券 |
| `coupon_stock_log` | 库存变更流水 |
| `order_status_log` | 订单状态流水 |
| `order_timeout_task` | 付费券超时关单任务 |
| `order_consume_record` | MQ 消费幂等记录 |
| `order_outbox_event` | 订单领域事件 outbox |

## 3. 关键约束

1. `coupon_activity.available_stock` 是库存事实，Redis 只做预扣。
2. `coupon_seckill_request.uk_order_request_user_client(user_id, client_request_id)` 保证移动端重试幂等。
3. `coupon_seckill_request.uk_order_request_user_activity(user_id, activity_id)` 和 `voucher_order.uk_order_user_activity(user_id, activity_id)` 兜底一人一券。
4. `voucher_order.uk_order_request(request_id)` 防止重复创建订单。
5. `user_coupon.uk_user_coupon_source_order(source_order_id)` 防止重复发券。
6. `payment_record.uk_payment_channel_trade(channel, channel_trade_no)` 防止重复支付回调。
7. `order_consume_record.uk_order_consumer_event(consumer_group, event_id)` 保证 MQ 消费幂等。
8. `order_consume_record.event_id` 和 `order_outbox_event.event_id` 长度为 128，容纳 `evt_{eventType}_{bizKey}_{uuid}` 格式事件 ID。
9. 订单库存对账以 `voucher_order` 中 `WAIT_PAY`、`SUCCESS` 订单为已占用事实，`coupon_activity.available_stock` / `sold_stock` 必须能由订单事实重算修复。
10. 运营库存调整必须写入 `coupon_stock_log`，`change_type=OPS_ADJUST`，并记录 `operator_type`、`operator_id`、`reason`。扣库存、取消回补、超时回补也应写入操作来源，便于后台审计。

## 4. 游标和状态

`user_coupon` 使用 `issued_at DESC, user_coupon_id DESC` 稳定分页，游标格式为 `issuedAtMillis_userCouponId`。

活动状态：`READY`、`PREHEATED`、`ONLINE`、`SOLD_OUT`、`PAUSED`、`ENDED`、`CANCELLED`。

内部活动列表使用 `created_at DESC, activity_id DESC` 稳定分页，游标格式为 `createdAtMillis_activityId`。

请求状态：`PROCESSING`、`SUCCESS`、`WAIT_PAY`、`SOLD_OUT`、`DUPLICATE`、`CANCELLED`、`CLOSED`、`FAILED`。

订单状态：`WAIT_PAY`、`SUCCESS`、`CLOSED`、`CANCELLED`、`FAILED`。

券状态：`UNUSED`、`USED`、`EXPIRED`。
