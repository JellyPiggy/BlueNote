# 第四条订单链路 Redis Key 契约

版本：v0.1
状态：订单 foundation 开发基线

订单 Redis 只用于活动预热、秒杀预扣、限购标记、短期 token 和补偿辅助。库存事实仍以 `bluenote_order.coupon_activity` 为准。

## 1. Key 清单

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:order:seckill:stock:{activityId}` | String | 活动结束后延长 1 小时 | order | order | Redis 预扣库存 |
| `bluenote:{env}:order:seckill:users:{activityId}` | Set | 活动结束后延长 1 小时 | order | order | 已参与用户集合 |
| `bluenote:{env}:order:seckill:token:{activityId}:{userId}:{token}` | String | 30 秒 | order | order Lua | 一次性秒杀 token |
| `bluenote:{env}:order:seckill:request:{activityId}` | Hash | 活动结束后延长 1 小时 | order | order | `userId -> requestId` 排查和补偿映射 |
| `bluenote:{env}:order:seckill:soldout:{activityId}` | String | 活动结束后延长 1 小时 | order | order | 售罄快速失败标识 |
| `bluenote:{env}:order:activity:rebuilding:{activityId}` | String | 5 分钟 | order | order | Redis 库存重建保护 |
| `bluenote:{env}:order:rate:seckill:{userId}:{activityId}` | String | 2 秒 | order | order | 秒杀提交轻量限流 |
| `bluenote:{env}:order:idempotent:pay:{channel}:{tradeNo}` | String | 7 天 | order | order | 支付回调短期幂等 |

## 2. Lua 预扣返回码

| 返回码 | 含义 | API 行为 |
|---|---|---|
| `1` | 预扣成功 | 写请求记录和 outbox，返回 `PROCESSING` |
| `0` | 库存不足 | 返回 `ORDER_STOCK_NOT_ENOUGH` 或请求结果 `SOLD_OUT` |
| `-1` | 重复参与 | 返回已有请求结果或 `ORDER_DUPLICATE_REQUEST` |
| `-2` | token 无效或过期 | 返回 `ORDER_SECKILL_TOKEN_INVALID` |
| `-4` | 活动未预热 | 返回 `ORDER_ACTIVITY_NOT_PREHEATED` |

## 3. Redis 重建

Redis 库存丢失时：

1. 设置 `order:activity:rebuilding:{activityId}` 或暂停活动。
2. 从 `coupon_activity.available_stock` 重建库存。
3. 从 `voucher_order` 里 `WAIT_PAY`、`SUCCESS` 订单重建用户集合。
4. 删除错误售罄标识。
5. 移除 rebuilding 标识后恢复活动。
