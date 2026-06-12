# BlueNote 契约目录

版本：v0.3
状态：第一、第二、第三条主链路完成基线，第四条订单链路 foundation 开发基线
更新时间：2026-06-12

## 1. 目录目标

`docs/contracts/` 用来保存前后端、服务间、数据库、Redis、MQ 和安全权限的共享契约。它不是方案讨论文档，而是编码、mock、OpenAPI、DDL、联调和验收时共同对照的基线。

当前已冻结第一、第二、第三条主链路基线，并新增第四条订单链路 foundation 契约。

第一条主链路：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情
```

第二条主链路：

```text
关注用户 -> 发布笔记事件 -> Feed 投递 -> 关注页拉取 -> 点赞/收藏/评论 -> 计数更新 -> 通知生成
```

第三条主链路：

```text
设备注册 -> WebSocket 连接 -> IM 发送消息 -> 消息入库 -> 在线下发 -> 离线 Push 请求
```

第四条主链路：

```text
活动预热 -> 秒杀令牌 -> Redis Lua 预扣库存 -> RocketMQ 异步下单 -> 订单查询 -> 支付/发券 -> 超时关单
```

涉及逻辑服务：

1. `bluenote-auth`
2. `bluenote-user`
3. `bluenote-file`
4. `bluenote-note`
5. `bluenote-relation`
6. `bluenote-comment`
7. `bluenote-counter`
8. `bluenote-feed`
9. `bluenote-notification`
10. `bluenote-push`
11. `bluenote-im`
12. `bluenote-order`

## 2. 目录结构

```text
docs/contracts/
  README.md
  api/
    00-common.md
    01-error-codes.md
    02-auth-api.md
    03-user-api.md
    04-file-api.md
    05-note-api.md
    06-relation-api.md
    07-comment-api.md
    08-counter-api.md
    09-feed-api.md
    10-notification-api.md
    11-mq-admin-api.md
    12-push-api.md
    13-im-api.md
    14-order-api.md
  db/
    01-main-chain-schema.md
    02-social-chain-schema.md
    03-order-chain-schema.md
  redis/
    01-main-chain-keys.md
    02-social-chain-keys.md
    03-order-chain-keys.md
  mq/
    01-main-chain-events.md
    02-social-chain-events.md
    03-order-chain-events.md
  security/
    01-permission-matrix.md
    02-social-chain-permission-matrix.md
    03-order-chain-permission-matrix.md
```

## 3. 权威来源

本目录从以下方案收口而来：

1. `方案/01-总体技术方案.md`
2. `方案/02-服务划分方案.md`
3. `方案/08-落地实施方案.md`
4. `方案/services/01-登录服务设计.md`
5. `方案/services/02-用户服务设计.md`
6. `方案/services/03-文件服务设计.md`
7. `方案/services/04-笔记发布服务设计.md`
8. `方案/services/05-用户关系服务设计.md`
9. `方案/services/06-评论服务设计.md`
10. `方案/services/07-计数服务设计.md`
11. `方案/services/08-Timeline-Feed服务设计.md`
12. `方案/services/11-通知服务设计.md`
13. `方案/services/10-推送与实时投递服务设计.md`
14. `方案/services/12-IM服务设计.md`
15. `方案/services/13-订单服务设计.md`

如果本目录与旧方案示例冲突，以本目录为开发契约。典型例子：旧方案中部分响应示例使用字符串型 `"code": "OK"`，本目录统一改为数字型 `"code": 0`。

## 4. 契约冻结规则

冻结后允许新增兼容字段，不允许静默破坏已有字段。

兼容变更：

1. 响应对象新增可选字段。
2. 新增错误码。
3. 新增接口，但不改变旧接口语义。
4. MQ payload 新增消费者可忽略字段。
5. 数据表新增可空字段或有默认值字段。

破坏性变更：

1. 删除或重命名接口字段。
2. 改变字段类型，例如 `id` 从 string 改为 number。
3. 改变错误码含义。
4. 改变接口鉴权要求。
5. 改变幂等键语义。
6. 删除表字段、重命名字段、修改唯一约束。
7. MQ 事件 payload 删除字段或改变含义。

破坏性变更必须先写明：

1. 为什么需要改。
2. 影响哪些 API、后端模块、移动端页面、DDL、Redis Key、MQ 事件。
3. 是否需要迁移脚本或兼容期。
4. 是否需要同步修改 `方案/decisions/` 下的 ADR。

## 5. 后端实现要求

后端必须：

1. Controller 响应结构与 `api/00-common.md` 一致。
2. 错误码与 `api/01-error-codes.md` 一致。
3. 外部接口只暴露 `/api/**`。
4. 内部接口只暴露 `/internal/**`，不得给移动端调用。
5. OpenAPI / Knife4j 输出与本目录一致。
6. 关键写接口支持幂等。
7. 数据表、唯一约束和索引符合 `db/01-main-chain-schema.md`、`db/02-social-chain-schema.md` 和 `db/03-order-chain-schema.md`。
8. Redis Key 符合 `redis/01-main-chain-keys.md`、`redis/02-social-chain-keys.md` 和 `redis/03-order-chain-keys.md`。
9. Outbox 和 MQ envelope 符合 `mq/01-main-chain-events.md`、`mq/02-social-chain-events.md` 和 `mq/03-order-chain-events.md`。

## 6. 移动端实现要求

移动端必须：

1. 所有请求集中在 `mobile/src/api/`。
2. 页面不直接拼接后端路径。
3. TypeScript 类型以本目录或后端 OpenAPI 为准。
4. mock 数据必须严格贴合本契约。
5. 统一处理 `code`、`message`、`data`、`traceId`。
6. 对 `20xxx` 认证错误统一刷新 Token 或跳转登录。
7. 发布、上传确认、资料更新等写操作防重复点击。
8. 每个核心页面处理 loading、empty、error、网络失败和 token 过期。

## 7. 联调验收

第一条主链路联调必须通过：

1. 注册成功后返回 TokenPair。
2. 登录成功后可调用 `/api/users/me`。
3. 上传凭证返回 `fileId` 和 `uploadUrl`。
4. 对象存储上传完成后可确认文件。
5. 发布笔记时文件归属和状态校验通过。
6. 查询笔记详情返回作者、媒体、标题、正文和计数结构。
7. 所有失败场景返回统一错误码和 `traceId`。

第二条主链路联调必须通过：

1. 登录用户可以关注、取消关注另一个正常用户，并查询关注状态。
2. 关注页 Feed 可以按游标分页拉取当前用户关注作者的公开笔记。
3. 点赞、收藏、评论、回复等写操作落库后发布对应业务事件。
4. 计数服务可以消费业务事件并返回笔记、用户、评论维度计数。
5. 通知服务可以根据关注、点赞、收藏、评论、回复事件生成站内通知和未读数。
6. 取关、笔记删除、私密、下架后 Feed 读取必须过滤不可见内容。
7. 所有第二链路外部接口必须经过网关鉴权，内部接口不得暴露给移动端。

第四条订单链路 foundation 联调必须通过：

1. 内部接口可以创建活动，并将活动库存预热到 Redis。
2. 登录用户可以获取秒杀 token，并提交抢券请求。
3. Redis Lua 预扣成功后写出 `CouponSeckillAccepted`，订单消费者异步创建订单。
4. 免费券订单直接 `SUCCESS` 并生成 `user_coupon`；付费券订单进入 `WAIT_PAY`。
5. `MOCK` 支付可以把待支付订单推进到 `SUCCESS` 并发券。
6. 超时关单任务可以关闭过期 `WAIT_PAY` 订单并回补库存。
7. 所有订单外部接口必须经过网关鉴权，内部活动配置接口不得暴露给移动端。
