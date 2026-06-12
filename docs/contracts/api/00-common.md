# API 通用契约

版本：v0.2
状态：第一、第二条主链路开发基线

## 1. 入口边界

移动端只访问网关暴露的 `/api/**`。

内部服务间接口使用 `/internal/**`，不暴露给移动端，不进入移动端 API 封装。

```text
mobile -> bluenote-gateway -> backend apps
```

禁止移动端直接访问：

1. `/internal/**`
2. 具体后端应用端口。
3. MySQL、Redis、RocketMQ、MinIO Console 等基础设施管理入口。

## 2. 数据格式

| 项 | 约定 |
|---|---|
| 请求体 | `application/json; charset=utf-8` |
| 响应体 | `application/json; charset=utf-8` |
| 时间 | ISO-8601 字符串，例如 `2026-06-05T10:00:00+08:00` |
| 日期 | `yyyy-MM-dd` |
| ID | JSON 中统一使用 string，数据库可使用 BIGINT |
| 金额 | 后续订单接口统一使用分为单位的整数 |
| 空值 | 必返可空字段返回 `null`，不要省略 |
| 枚举 | 使用大写下划线或大写单词，例如 `PUBLISHED` |

对象存储直传不受 JSON 请求体限制。移动端按文件服务返回的 `uploadMethod`、`uploadUrl` 和 `headers` 上传文件。

## 3. 请求头

| Header | 必填 | 说明 |
|---|---|---|
| `Authorization: Bearer {accessToken}` | 登录后接口必填 | Access Token |
| `X-Request-Id` | 建议 | 移动端生成的请求 ID |
| `Idempotency-Key` | 关键写接口必填 | 幂等键 |
| `X-App-Version` | 建议 | App 版本 |
| `X-Device-Id` | 建议 | 设备 ID |

网关负责生成或透传 `traceId`。后端响应体必须返回 `traceId`。

## 4. 统一响应结构

所有接口响应必须包一层统一结构。

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "trace-20260605100000-abc123"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `code` | number | 是 | 错误码，`0` 表示成功 |
| `message` | string | 是 | 用户可读提示，不暴露内部细节 |
| `data` | object / array / null | 是 | 业务数据 |
| `traceId` | string | 是 | 链路追踪 ID |

无业务数据时，接口必须固定返回 `data: null` 或固定结构，不能同一接口时而返回 null、时而返回空对象。

## 5. 错误响应

```json
{
  "code": 20001,
  "message": "登录已过期，请重新登录",
  "data": {
    "reason": "ACCESS_TOKEN_EXPIRED"
  },
  "traceId": "trace-id"
}
```

错误响应要求：

1. `message` 面向移动端展示。
2. `data.reason` 用于移动端分支判断。
3. 不返回 SQL、堆栈、内部服务地址、完整 Token。
4. 后端日志记录详细内部原因，并包含 `traceId`。

## 6. 游标分页

第一阶段列表接口统一使用游标分页。

请求参数：

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `cursor` | string / null | null | 首次请求不传 |
| `size` | number | 20 | 范围 1 到 50 |

响应结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [],
    "nextCursor": "2026-06-05T10:00:00+08:00_800001",
    "hasMore": true
  },
  "traceId": "trace-id"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `items` | array | 是 | 当前页数据 |
| `nextCursor` | string / null | 是 | 下一页游标 |
| `hasMore` | boolean | 是 | 是否还有下一页 |

## 7. 幂等

关键写接口必须支持幂等。

| 场景 | 幂等依据 |
|---|---|
| 注册 | `username` 唯一约束 |
| 刷新 Token | Refresh Token 轮换和会话状态 |
| 登出 | 会话最终状态 |
| 更新资料 | `profileVersion` 或当前状态 |
| 上传确认 | `fileId` + 文件状态 |
| 发布笔记 | `clientRequestId` 或 `Idempotency-Key` |
| 编辑笔记 | `clientRequestId` + `baseVersion` |
| 点赞收藏 | `userId` + `noteId` 唯一业务键 |
| 关注取关 | `followerId` + `followeeId` 最终状态 |
| 发布评论或回复 | `clientRequestId` 或 `Idempotency-Key` |
| 评论点赞 | `userId` + `commentId` 唯一业务键 |
| 通知已读和删除 | `receiverId` + `notificationId` 最终状态 |

重复请求已经成功时，应返回同一业务结果或当前最终状态。不能重复创建业务对象。

`Idempotency-Key` 建议格式：

```text
{deviceId}:{operation}:{uuid}
```

## 8. 可选登录接口

部分查询接口支持可选登录。规则：

1. 未登录时只返回公开信息。
2. 已登录时可额外返回当前用户视角字段，例如 `viewerAction`、`relation`。
3. Token 无效时不要当作未登录静默降级，应返回认证错误。

## 9. 内部接口约定

内部接口也使用统一响应结构，但有额外要求：

1. 只能服务间调用。
2. 必须有服务身份认证或内网隔离。
3. 支持批量查询，避免 N+1。
4. 单次批量大小默认不超过 100。
5. 内部 DTO 与移动端 DTO 分开定义。
