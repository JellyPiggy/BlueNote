# MQ/outbox 内部运维 API 契约

版本：v0.1
状态：第二条主链路开发基线

本文定义 MQ/outbox 基础设施的内部运维接口。所有接口只允许服务间、运维脚本或管理端内网调用，移动端不得调用。

## 1. 查询 outbox 状态

```text
GET /internal/mq/outbox/stats
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "tableName": "bluenote_relation.relation_outbox_event",
      "initCount": 0,
      "retryableFailedCount": 0,
      "deadLetterCount": 0,
      "sentCount": 128
    }
  ],
  "traceId": "trace-id"
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `tableName` | string | 当前应用声明的 outbox 表名 |
| `initCount` | number | 待首次发送数量 |
| `retryableFailedCount` | number | 已失败且到达下次重试时间的数量 |
| `deadLetterCount` | number | 达到最大重试次数的数量 |
| `sentCount` | number | 已发送数量 |

## 2. 手动触发发送

```text
POST /internal/mq/outbox/dispatch-once
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "dispatchedCount": 10
  },
  "traceId": "trace-id"
}
```

说明：

1. 只扫描当前应用配置的 outbox 表。
2. 只发送 `INIT` 或到达 `next_retry_at` 的 `FAILED` 事件。
3. 成功发送后标记 `SENT`；失败后增加 `retry_count` 并推进 `next_retry_at`。

## 3. 重置失败事件

```text
POST /internal/mq/outbox/events/retry
```

请求：

```json
{
  "tableName": "bluenote_relation.relation_outbox_event",
  "eventId": "uuid"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "tableName": "bluenote_relation.relation_outbox_event",
    "eventId": "uuid",
    "retried": true
  },
  "traceId": "trace-id"
}
```

约束：

1. 只能重置当前应用配置过的 outbox 表。
2. 只允许重置 `FAILED` 事件。
3. 重置后 `retry_count=0`，`next_retry_at=null`，等待定时 dispatcher 或手动发送。
4. 已 `SENT` 事件不得通过该接口重新发送；后续如果需要重放，必须新增带审计的 replay 接口。
