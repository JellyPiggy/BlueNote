# BlueNote 工程结构与编码规范

版本：v0.1  
状态：M0 工程基础基线  
更新时间：2026-06-06

## 1. 文档目标

本文用于约束 BlueNote 第一阶段工程落地方式，重点回答：

1. 后端多模块工程如何组织。
2. 公共模块能放什么、不能放什么。
3. Controller、Service、Mapper、DTO、配置和测试如何分层。
4. 统一响应、错误码、日志、鉴权上下文和 OpenAPI 如何对齐契约。
5. 后续移动端、部署和联调如何避免接口字段漂移。

本文不替代 `docs/contracts/`。编码、mock、DDL、Redis Key、MQ 事件和权限判断必须以 `docs/contracts/` 为直接基线。

## 2. 仓库目录

第一阶段按总仓库维护：

```text
BlueNote/
  AGENTS.md
  方案/
  docs/
    contracts/
    engineering/
    testing/
  backend/
    sql/
  mobile/
  deploy/
    compose/
    env/
    nacos/
  scripts/
```

目录职责：

| 目录 | 职责 |
|---|---|
| `方案/` | 架构方案、服务设计、ADR，不放工程代码 |
| `docs/contracts/` | API、DDL、Redis、MQ、安全权限共享契约 |
| `docs/engineering/` | 工程结构、编码规范、联调规范 |
| `backend/` | Java 后端 Maven 多模块工程和 SQL |
| `mobile/` | uni-app + Vue 3 + TypeScript 移动端工程 |
| `deploy/` | Compose、Nacos、反向代理、监控等部署配置 |
| `scripts/` | 运维、备份、部署、压测脚本 |

## 3. 后端模块结构

后端采用 Maven 多模块，逻辑服务边界与物理部署分组分离。

```text
backend/
  pom.xml
  bluenote-dependencies/
  bluenote-common/
    bluenote-common-core/
    bluenote-common-web/
    bluenote-common-security/
    bluenote-common-mybatis/
    bluenote-common-redis/
    bluenote-common-mq/
    bluenote-common-observability/
  bluenote-gateway-app/
  bluenote-member-app/
    bluenote-auth-module/
    bluenote-user-module/
  bluenote-content-app/
    bluenote-file-module/
    bluenote-note-module/
    bluenote-comment-module/
  bluenote-social-app/
    bluenote-relation-module/
    bluenote-counter-module/
    bluenote-feed-module/
    bluenote-rank-module/
    bluenote-notification-module/
    bluenote-push-module/
  bluenote-im-app/
  bluenote-order-app/
  sql/
```

第一条主链路只优先落地：

1. `bluenote-gateway-app`
2. `bluenote-member-app`
3. `bluenote-content-app`
4. `bluenote-auth-module`
5. `bluenote-user-module`
6. `bluenote-file-module`
7. `bluenote-note-module`

## 4. 公共模块边界

`bluenote-common-*` 只能放稳定技术能力。

允许放入：

| 模块 | 内容 |
|---|---|
| `common-core` | 统一响应、错误码接口、分页对象、基础异常、时间工具 |
| `common-web` | 全局异常处理、参数校验响应、请求上下文、Web 配置 |
| `common-security` | 用户上下文对象、内部调用认证基础、脱敏工具 |
| `common-mybatis` | MyBatis-Plus 配置、公共字段填充、类型处理器 |
| `common-redis` | Redis Key 前缀工具、序列化配置、限流基础封装 |
| `common-mq` | 事件 envelope、outbox 基础接口、消费幂等基础能力 |
| `common-observability` | traceId、日志 MDC、Actuator 和指标基础配置 |

禁止放入：

1. 具体业务判断，例如是否能发布笔记、是否已关注。
2. 具体业务 Mapper、Entity、Repository。
3. 具体业务 DTO 的巨型集合。
4. 直接访问其他服务 schema 的工具。
5. 会让多个业务服务互相强耦合的 helper。

## 5. 业务模块分层

每个逻辑模块内部建议使用：

```text
com.bluenote.{domain}
  api/
    external/
    internal/
  application/
  domain/
  infrastructure/
    mapper/
    persistence/
    mq/
    redis/
  config/
```

分层职责：

| 分层 | 职责 |
|---|---|
| `api.external` | 移动端可访问的 `/api/**` Controller 和请求响应 DTO |
| `api.internal` | 服务间 `/internal/**` Controller 和内部 DTO |
| `application` | 用例编排、事务边界、幂等处理、调用外部依赖 |
| `domain` | 本服务领域模型、状态机、业务规则 |
| `infrastructure` | Mapper、MyBatis XML、Redis、MQ、对象存储适配 |
| `config` | 本模块配置类 |

Controller 不写业务规则，只做参数校验、用户上下文获取和调用 application service。

## 6. API 与错误码

所有接口必须返回统一响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "trace-id"
}
```

要求：

1. 外部接口只暴露 `/api/**`。
2. 内部接口只暴露 `/internal/**`，网关不得公网转发。
3. DTO 字段以 `docs/contracts/api/` 为准。
4. 错误码以 `docs/contracts/api/01-error-codes.md` 为准。
5. ID 在 API 中统一使用 string，数据库中使用 BIGINT。
6. 时间在 API 中使用 ISO-8601 字符串。
7. 写接口必须使用 Jakarta Validation。

## 7. 安全上下文

网关校验 Access Token 后向后端注入：

| Header | 说明 |
|---|---|
| `X-User-Id` | 当前用户 ID |
| `X-Device-Id` | 当前设备 ID |
| `X-Session-Id` | 当前会话 ID |
| `X-Trace-Id` | 链路 ID |

业务服务优先读取网关注入的上下文，不直接解析移动端 Token。内部服务调用必须透传 `X-Trace-Id`，并使用内部服务认证或内网隔离。

## 8. 数据库规范

DDL 放在 `backend/sql/`，按版本号命名：

```text
V001__auth.sql
V002__user.sql
V003__file.sql
V004__note.sql
```

要求：

1. 每个逻辑服务独立 schema。
2. 不创建跨 schema 外键。
3. 不允许跨 schema join。
4. 唯一约束必须由数据库兜底。
5. 状态字段使用 `VARCHAR(32)`。
6. 时间字段使用 `DATETIME(3)`。
7. JSON 字段只保存 payload、响应快照等扩展数据，不做高频查询条件。

## 9. Redis 与 MQ

Redis Key 必须记录在 `docs/contracts/redis/`，统一格式：

```text
bluenote:{env}:{service}:{biz}:{id}
```

MQ 事件必须使用统一 envelope：

```json
{
  "eventId": "uuid",
  "eventType": "NotePublished",
  "eventVersion": 1,
  "occurredAt": "2026-06-05T10:00:00+08:00",
  "traceId": "trace-id",
  "producer": "bluenote-note",
  "bizKey": "800001",
  "payload": {}
}
```

核心写操作优先使用本地事务 + outbox。消费者使用 `event_id + consumer_group` 做幂等。

## 10. 日志与可观测性

最低要求：

1. 所有请求日志包含 `traceId`。
2. 用户相关操作日志包含脱敏后的 `userId`、`deviceId`。
3. 登录、Token、密码、Refresh Token 不打印完整值。
4. MQ 日志包含 `eventId`、`eventType`、`topic`、`consumerGroup`。
5. 异常日志记录内部原因，对外响应不暴露堆栈、SQL、内部地址。
6. 应用暴露 Actuator 健康检查和基础指标。

## 11. 测试基线

第一阶段至少准备：

| 类型 | 覆盖 |
|---|---|
| 单元测试 | 密码规则、参数校验、状态机、Redis Key、事件 envelope |
| Mapper 测试 | 核心查询、唯一约束、分页索引方向 |
| 接口测试 | 注册、登录、刷新 Token、用户资料、上传确认、发布笔记、详情 |
| 幂等测试 | 注册、登出、上传确认、发布笔记、点赞、收藏 |
| 安全测试 | 未登录、Token 过期、资源归属、内部接口不可外部访问 |

测试数据和 mock 必须贴合 `docs/contracts/`。

## 12. 变更规则

修改共享契约前必须说明：

1. 为什么需要修改。
2. 影响哪些 API、DDL、Redis Key、MQ 事件、后端模块、移动端页面。
3. 是否破坏兼容。
4. 是否需要迁移脚本或 ADR。

业务代码不能静默改变契约字段、错误码、权限要求或数据语义。
