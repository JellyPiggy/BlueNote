# 第四条订单链路权限矩阵

版本：v0.1
状态：订单 foundation 开发基线

## 1. 外部接口权限矩阵

| 方法 | 路径 | 登录 | 资源归属 | 幂等 | 限流 | 说明 |
|---|---|---|---|---|---|---|
| GET | `/api/order/coupon-activities/current` | 是 | 当前用户参与状态 | 否 | 用户 / IP | 查询当前活动 |
| POST | `/api/order/seckill/token` | 是 | 当前用户 | token TTL | 用户 / 活动 | 获取秒杀 token |
| POST | `/api/order/seckill/orders` | 是 | 当前用户 | `userId + clientRequestId` | 用户 / 活动 / 设备 | 提交抢券 |
| GET | `/api/order/seckill/results/{requestId}` | 是 | 请求发起人 | 否 | 用户 | 查询抢券结果 |
| GET | `/api/order/orders/{orderId}` | 是 | 订单所属用户 | 否 | 用户 | 查询订单详情 |
| POST | `/api/order/orders/{orderId}/pay` | 是 | 订单所属用户 | 支付流水 | 用户 / 订单 | MOCK 支付 |
| POST | `/api/order/orders/{orderId}/cancel` | 是 | 订单所属用户 | 订单状态 CAS | 用户 / 订单 | 取消待支付订单 |
| GET | `/api/order/my-coupons` | 是 | 当前用户 | 否 | 用户 | 查询我的神券 |

## 2. 内部接口权限矩阵

| 方法 | 路径 | 调用方 | 被调用方 | 要求 |
|---|---|---|---|---|
| POST | `/internal/order/coupon-activities` | ops / admin | order | 内部管理权限，活动参数校验 |
| POST | `/internal/order/coupon-activities/{activityId}/preheat` | ops / admin | order | 内部管理权限，预热幂等 |
| POST | `/internal/order/coupon-activities/{activityId}/pause` | ops / admin | order | 内部管理权限，状态 CAS |
| POST | `/internal/order/coupon-activities/{activityId}/resume` | ops / admin | order | 内部管理权限，状态 CAS |
| POST | `/internal/order/coupon-activities/{activityId}/end` | ops / admin | order | 内部管理权限，状态 CAS |

网关不得暴露 `/internal/**` 到公网。

## 3. 参数和归属规则

1. 移动端订单接口必须登录，服务端以网关注入用户作为当前用户。
2. 移动端不得传 `userId`、支付金额、库存、券面额。
3. `clientRequestId` 长度 1 到 128，移动端重试必须复用同一个值。
4. 用户只能查询自己的抢券请求、订单和神券。
5. 只有 `WAIT_PAY` 订单可以支付或取消。
6. 支付成功、用户取消和超时关单必须使用订单状态 CAS。
7. 秒杀 token 必须绑定 `activityId + userId` 并一次性消费。
