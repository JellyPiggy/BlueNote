# BlueNote 后端实习项目学习路线

版本：v0.1
状态：GitHub 展示与后端实习项目学习指南
更新时间：2026-06-13

## 1. 文档目标

本文面向想把 BlueNote 当作后端实习项目学习、改造、复盘或写进简历的读者。

BlueNote 的代码和文档比较多，如果直接从所有 Controller、Mapper 和 SQL 开始看，很容易迷路。本文提供一条更适合后端实习生的阅读路径：先理解项目为什么这么拆，再按业务链路逐步进入代码，最后整理成可讲清楚的项目经验。

本文不替代：

1. `docs/contracts/`：接口、数据库、Redis、MQ 和权限的编码基线。
2. `方案/services/`：每个服务的详细设计。
3. `docs/engineering/current-status.md`：当前已经完成到哪里。

## 2. 适合的读者

适合：

1. 想准备 Java 后端实习项目的同学。
2. 已经学过 Spring Boot、MySQL、Redis，但缺一个完整业务项目串起来的人。
3. 想学习社区类产品常见后端模块的人。
4. 想理解“个人项目怎么写得像工程项目，而不是一堆接口”的人。

不适合：

1. 只想找一个非常简单 CRUD 项目的人。
2. 希望一上来就是生产级高并发系统的人。
3. 不想读文档、只想复制运行结果的人。

BlueNote 的重点是把真实工程中的边界、契约、事件、缓存、幂等和补偿思想，用个人项目能承受的复杂度落下来。

## 3. 总体学习顺序

推荐按 8 个阶段学习。

| 阶段 | 目标 | 重点目录 |
|---|---|---|
| S0 | 跑起来并建立全局认知 | `README.zh-CN.md`、`backend/README.md`、`docs/engineering/02-local-main-chain-runbook.md` |
| S1 | 理解契约优先 | `docs/contracts/README.md`、`docs/contracts/api/00-common.md`、`docs/contracts/api/01-error-codes.md` |
| S2 | 学身份与用户基础 | `bluenote-member-app`、`docs/contracts/api/02-auth-api.md`、`03-user-api.md` |
| S3 | 学内容发布链路 | `bluenote-content-app`、`04-file-api.md`、`05-note-api.md`、`07-comment-api.md` |
| S4 | 学社交分发链路 | `relation`、`counter`、`feed`、`notification` |
| S5 | 学实时和 IM | `push`、`im`、`12-push-api.md`、`13-im-api.md` |
| S6 | 学订单可靠性 | `bluenote-order-app`、`14-order-api.md`、订单 SQL 和 Redis 契约 |
| S7 | 学排行榜和读模型 | `rank`、`15-rank-api.md`、`rank_*` 表和 Redis ZSet |

不要一开始就从所有代码细节学。更好的方式是：先跑通链路，再看契约，再看某个模块如何把契约变成 Controller、Service、Mapper、Redis 和 MQ。

## 4. S0：先跑起来

第一步不是研究架构图，而是确认项目能在本地跑起来。

推荐阅读：

1. `README.zh-CN.md`
2. `backend/README.md`
3. `docs/engineering/02-local-main-chain-runbook.md`

推荐操作：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

```bash
cd backend
mvn -q -DskipTests compile
```

如果后端和依赖已经启动，再跑：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-main-chain.ps1 -GatewayBaseUrl http://127.0.0.1:8080
```

这个阶段你要弄明白：

1. 为什么后端不是一个单体应用，而是 gateway/member/content/social/order 五个应用。
2. 为什么逻辑服务比物理应用更多。
3. MySQL、Redis、RocketMQ、MinIO 分别承担什么职责。
4. 主链路冒烟脚本证明了哪些真实能力。

自查问题：

1. gateway-app 为什么要先启动或最后启动都可以，但移动端为什么必须走 gateway？
2. member-app 和 content-app 为什么不能直接互查对方数据库？
3. MinIO 在笔记图片上传中负责什么，MySQL 又保存什么？

## 5. S1：理解契约优先

BlueNote 很适合用来学习“契约优先”。

推荐阅读：

1. `docs/contracts/README.md`
2. `docs/contracts/api/00-common.md`
3. `docs/contracts/api/01-error-codes.md`
4. `docs/contracts/security/01-permission-matrix.md`

要重点理解：

1. 外部接口统一是 `/api/**`。
2. 内部接口统一是 `/internal/**`。
3. 移动端不直接调用内部接口。
4. 所有响应都使用统一结构：`code`、`message`、`data`、`traceId`。
5. 写接口需要考虑幂等、权限和参数校验。
6. 事件、Redis Key、DDL 都是契约的一部分，不只是 HTTP API 才叫契约。

这个阶段建议你不要急着改代码。先挑一个接口，例如 `POST /api/notes`，从 API 契约看到 Controller，再看到 Service、Mapper、SQL 和 outbox 写入。

自查问题：

1. 为什么 ID 在 API 中用 string，而数据库中用 BIGINT？
2. 为什么内部接口不应该暴露给移动端？
3. 如果一个接口字段要改名，应该同步改哪些地方？

## 6. S2：身份与用户基础

对应应用：`backend/bluenote-member-app`

对应逻辑服务：

1. auth
2. user

推荐阅读：

1. `docs/contracts/api/02-auth-api.md`
2. `docs/contracts/api/03-user-api.md`
3. `方案/services/01-登录服务设计.md`
4. `方案/services/02-用户服务设计.md`

重点学习点：

| 能力 | 学习价值 |
|---|---|
| 注册/登录 | 账号、密码哈希、Token 签发、统一错误码 |
| Refresh Token | 为什么 Access Token 要短有效期，Refresh Token 要可轮换 |
| 设备会话 | 多端登录、登出、会话失效的基础 |
| 用户资料 | 登录身份和展示资料分离 |
| 头像/封面绑定 | user 服务如何调用 file 服务校验文件归属和场景 |
| 用户主页计数 | user 服务如何通过 counter 聚合关注数、粉丝数、作品数和获赞数 |

建议看代码路径：

```text
backend/bluenote-member-app/src/main/java/com/bluenote/member/auth
backend/bluenote-member-app/src/main/java/com/bluenote/member/user
backend/sql/V001__auth.sql
backend/sql/V002__user.sql
```

可以尝试的小改造：

1. 给登录失败增加更清楚的审计字段。
2. 给用户资料增加一个兼容字段，例如个人主页展示地区。
3. 为修改资料补一组单元测试或接口测试。

面试可讲：

1. 密码不明文保存，使用 BCrypt 哈希。
2. Access Token 由网关校验，下游服务读取网关注入的用户上下文 Header。
3. 用户资料和登录凭证分 schema 维护，避免用户展示信息与安全凭证耦合。
4. 资料修改涉及头像/封面时，会调用文件服务做归属、场景和状态校验。

## 7. S3：内容发布链路

对应应用：`backend/bluenote-content-app`

对应逻辑服务：

1. file
2. note
3. comment

推荐阅读：

1. `docs/contracts/api/04-file-api.md`
2. `docs/contracts/api/05-note-api.md`
3. `docs/contracts/api/07-comment-api.md`
4. `方案/services/03-文件服务设计.md`
5. `方案/services/04-笔记发布服务设计.md`
6. `方案/services/06-评论服务设计.md`

重点学习点：

| 能力 | 学习价值 |
|---|---|
| 预签名上传 | 移动端直传对象存储，后端只管凭证和元数据 |
| 上传确认 | 避免业务直接引用未完成上传的文件 |
| 文件绑定 | 头像、封面、笔记图片使用不同业务场景 |
| 笔记发布 | 文件归属校验、媒体绑定、幂等和 outbox |
| 点赞收藏 | 行为明细归 note，聚合计数归 counter |
| 评论回复 | 一级评论和回复的层级控制、状态删除、事件发布 |

建议看代码路径：

```text
backend/bluenote-content-app/src/main/java/com/bluenote/content/file
backend/bluenote-content-app/src/main/java/com/bluenote/content/note
backend/bluenote-content-app/src/main/java/com/bluenote/content/comment
backend/sql/V003__file.sql
backend/sql/V004__note.sql
backend/sql/V006__comment.sql
backend/sql/V010__note_interaction_lists.sql
```

核心流程：

```text
移动端申请上传凭证
  -> file 保存上传会话
  -> 移动端直传 MinIO
  -> file 确认上传
  -> note 发布时校验文件归属和状态
  -> note 保存笔记、媒体、版本和 outbox
  -> 后续 Feed、Counter、Rank、Notification 订阅事件
```

自查问题：

1. 为什么不能让移动端直接把任意 fileId 塞进发布笔记接口？
2. 为什么点赞/收藏必须有明细表，不能只存一个数字？
3. 评论删除为什么通常是状态变更，而不是直接物理删除？

面试可讲：

1. 文件上传使用“申请凭证 -> 对象存储直传 -> 上传确认 -> 业务绑定”的流程。
2. 笔记服务是笔记事实的主写服务，但不直接维护最终计数。
3. 点赞收藏明细放在 note，计数变化通过事件交给 counter，这样能避免详情页和互动写路径强耦合。

## 8. S4：社交分发链路

对应应用：`backend/bluenote-social-app`

对应逻辑服务：

1. relation
2. counter
3. feed
4. notification

推荐阅读：

1. `docs/contracts/api/06-relation-api.md`
2. `docs/contracts/api/08-counter-api.md`
3. `docs/contracts/api/09-feed-api.md`
4. `docs/contracts/api/10-notification-api.md`
5. `docs/contracts/mq/02-social-chain-events.md`
6. `docs/contracts/redis/02-social-chain-keys.md`

重点学习点：

| 模块 | 学习价值 |
|---|---|
| relation | 关注关系的行为事实、关注/粉丝列表、关系状态 |
| counter | 事件驱动计数、Redis 在线计数、MySQL 快照、重建和回源 |
| feed | 关注页收件箱、fanout 任务、Redis/MySQL 降级、关注补发 |
| notification | 通知读模型、未读数、点赞收藏聚合、评论关注明细 |

建议看代码路径：

```text
backend/bluenote-social-app/src/main/java/com/bluenote/social/relation
backend/bluenote-social-app/src/main/java/com/bluenote/social/counter
backend/bluenote-social-app/src/main/java/com/bluenote/social/feed
backend/bluenote-social-app/src/main/java/com/bluenote/social/notification
backend/sql/V005__relation.sql
backend/sql/V007__counter.sql
backend/sql/V008__feed.sql
backend/sql/V009__notification.sql
```

社交链路可以这样理解：

```text
用户关注作者
  -> relation 写关注事实
  -> 发布 UserFollowed
  -> counter 更新关注数和粉丝数
  -> feed 补发作者近期公开笔记
  -> notification 生成关注通知

作者发布笔记
  -> note 发布 NotePublished
  -> feed 创建 fanout 任务并投递粉丝收件箱
  -> rank 建立候选索引
  -> counter 增加作者作品数
```

自查问题：

1. counter 为什么要有 `counter_snapshot`，而不是只用 Redis？
2. Feed 为什么需要 MySQL 持久化或重建任务，而不是只用 Redis ZSet？
3. 点赞通知为什么可以聚合，评论通知为什么更适合保留明细？

面试可讲：

1. 计数服务查询优先级是 Redis 在线计数、MySQL 快照、来源服务回源，回源成功后再回填。
2. Feed 服务使用收件箱读模型，普通作者发布后可以写扩散到粉丝，后续大 V 可演进为推拉结合。
3. 通知服务消费互动、评论、关注和订单事件，构建自己的通知读模型，不反查各业务表拼列表。

## 9. S5：实时投递和 IM

对应应用：`backend/bluenote-social-app`

对应逻辑服务：

1. push
2. im

推荐阅读：

1. `docs/contracts/api/12-push-api.md`
2. `docs/contracts/api/13-im-api.md`
3. `方案/services/10-推送与实时投递服务设计.md`
4. `方案/services/12-IM服务设计.md`

重点学习点：

| 模块 | 学习价值 |
|---|---|
| push | 设备注册、偏好、投递请求、WebSocket 在线投递、ACK 和投递日志 |
| im | 会话、消息、未读、送达、已读、消息 outbox、Push 请求 |

建议看代码路径：

```text
backend/bluenote-social-app/src/main/java/com/bluenote/social/push
backend/bluenote-social-app/src/main/java/com/bluenote/social/im
backend/sql/V011__push.sql
backend/sql/V012__push_realtime.sql
backend/sql/V013__im.sql
```

要理解的边界：

1. push 负责设备和投递通道，不负责生成通知内容。
2. notification 负责站内通知，不负责 WebSocket 连接。
3. im 负责会话和消息事实，不直接维护通用设备 token。
4. 离线 Push 当前是扩展点，项目已经完成的是在线 WebSocket 投递和投递请求基础。

自查问题：

1. IM 消息为什么必须先入库再投递？
2. PushSendRequested 为什么适合作为通知、订单、IM 到 push 的统一事件？
3. WebSocket 在线投递失败后，客户端为什么仍可以通过通知或 IM 拉取接口补偿？

面试可讲：

1. IM 写路径保存消息、会话关系和用户消息链，再写出事件和 Push 请求。
2. Push 服务维护在线设备 Redis 路由，在线时走 WebSocket，下游投递结果写投递日志。
3. 当前未接真实厂商离线通道，这是有意控制个人项目复杂度，后续可按 provider adapter 扩展。

## 10. S6：订单可靠性

对应应用：`backend/bluenote-order-app`

对应逻辑服务：

1. order

推荐阅读：

1. `docs/contracts/api/14-order-api.md`
2. `docs/contracts/db/03-order-chain-schema.md`
3. `docs/contracts/redis/03-order-chain-keys.md`
4. `docs/contracts/mq/03-order-chain-events.md`
5. `方案/services/13-订单服务设计.md`

重点学习点：

| 能力 | 学习价值 |
|---|---|
| 活动配置和预热 | 秒杀活动上线前准备 |
| 秒杀 token | 防止提前刷接口和直接压抢券接口 |
| Redis Lua 预扣 | 原子扣库存、限购和请求受理 |
| RocketMQ 异步下单 | 削峰，把高并发入口和订单写库解耦 |
| MOCK 支付 | 不接真实厂商也能跑通购买逻辑 |
| 超时关单 | WAIT_PAY 订单过期关闭和库存回补 |
| 库存对账 | MySQL 与 Redis 库存不一致时检测和修复 |

建议看代码路径：

```text
backend/bluenote-order-app/src/main/java/com/bluenote/order
backend/sql/V014__order.sql
backend/sql/V015__order_event_id_width.sql
backend/sql/V016__order_ops_stock_log.sql
```

核心流程：

```text
查询活动
  -> 获取秒杀 token
  -> Redis Lua 校验 token、库存和用户限购
  -> 预扣 Redis 库存
  -> 写出 CouponSeckillAccepted
  -> MQ 消费异步创建订单
  -> 免费券直接 SUCCESS 并发券
  -> 付费券 WAIT_PAY
  -> MOCK 支付成功后发券
  -> 超时未支付则 CAS 关单并回补库存
```

自查问题：

1. Redis 预扣成功后，MySQL 扣库存失败怎么办？
2. 为什么要有用户参与集合或唯一约束防重复抢？
3. 支付成功和超时关单并发时，为什么要用状态机 CAS？

面试可讲：

1. 秒杀入口用 Redis Lua 做原子预扣和限购，订单写库异步化。
2. MySQL 是订单和库存事实来源，Redis 库存可通过运维接口从 MySQL 重建。
3. 订单状态流转通过状态机和 CAS 控制，超时关单和支付成功并发时只能有一个成功。

## 11. S7：排行榜和读模型

对应应用：`backend/bluenote-social-app`

对应逻辑服务：

1. rank

推荐阅读：

1. `docs/contracts/api/15-rank-api.md`
2. `docs/contracts/db/02-social-chain-schema.md`
3. `docs/contracts/mq/02-social-chain-events.md`
4. `方案/services/09-排行榜服务设计.md`

重点学习点：

| 能力 | 学习价值 |
|---|---|
| 周热笔记榜 | 内容发现类榜单 |
| 年度创作者成长榜 | 用户激励类榜单 |
| CounterChanged 驱动 | 榜单不直接感知点赞收藏行为明细，只消费计数变化 |
| Redis ZSet | 在线榜单读写 |
| MySQL 分数事实 | 可排查、可重建、可快照 |
| 快照和重建 | Redis 丢失或策略变化时恢复 |

建议看代码路径：

```text
backend/bluenote-social-app/src/main/java/com/bluenote/social/rank
backend/sql/V017__rank.sql
```

自查问题：

1. 为什么排行榜不应该直接消费所有点赞、评论、收藏明细？
2. 为什么 Redis ZSet 适合在线榜单，但 MySQL 仍要保留分数事实？
3. 如果笔记被删除或改为私密，榜单贡献应该怎么处理？

面试可讲：

1. 排行榜通过 `NotePublished` 建立候选索引，通过 `CounterChanged` 更新在线分数。
2. 周热笔记榜和年度创作者成长榜共用分数贡献模型，但 rankType、周期和成员类型不同。
3. 查询优先 Redis，Redis 空或异常时降级 MySQL，并通过 content 摘要校验笔记仍然可展示。

## 12. 推荐练习题

如果你想把 BlueNote 改造成自己的实习项目，可以做下面这些练习。建议从小改开始，不要一上来重构服务。

| 难度 | 练习 | 价值 |
|---|---|---|
| 入门 | 给某个接口补参数校验和错误码文档 | 熟悉契约优先和统一响应 |
| 入门 | 给用户资料或笔记增加一个兼容字段 | 熟悉 API、DTO、SQL、移动端同步修改 |
| 中等 | 给 auth/user/file/note 补集成测试 | 弥补当前项目测试短板 |
| 中等 | 给 Feed 增加清理任务或失败重试列表 | 学任务补偿和读模型维护 |
| 中等 | 给 rank 增加定时快照入口 | 学榜单快照和降级 |
| 进阶 | 给 push 增加一个假的 provider adapter | 学真实厂商 Push 的适配器边界 |
| 进阶 | 给 order 增加退款状态机草案和契约 | 学交易域状态设计 |
| 进阶 | 建立 OpenAPI 与 `docs/contracts/api` 的差异检查 | 学工程质量保障 |

每做一个练习，都应该同步考虑：

1. 是否改了 API 契约。
2. 是否需要新 DDL 或兼容迁移。
3. 是否影响 Redis Key。
4. 是否新增或修改 MQ 事件。
5. 是否需要移动端同步。
6. 是否需要更新当前状态文档。

## 13. 简历使用建议

简历不要写成“我做了一个小红书”，这太泛。建议写成“社区类多模块后端项目”，并突出具体工程能力。

可以写：

```text
BlueNote 社区类后端项目
- 使用 Java 21 + Spring Boot 搭建 gateway/member/content/social/order 多应用后端，覆盖用户、内容、关注、Feed、通知、IM、订单和排行榜等模块。
- 采用契约优先方式维护 API、错误码、DDL、Redis Key、RocketMQ 事件和权限矩阵，移动端 H5 与后端按契约联调。
- 使用 RocketMQ + outbox 实现计数、Feed、榜单、通知、Push 和订单事件链路，并通过消费记录、业务唯一约束和状态机保证幂等。
- 在订单模块中实现秒杀 token、Redis Lua 预扣库存、异步下单、MOCK 支付、超时关单、库存重建和对账修复。
```

不要写：

```text
支撑百万 QPS。
实现完整生产级支付。
实现完整推荐系统。
实现真实厂商 Push 全链路。
```

面试时诚实说清楚项目定位，反而更可信：

```text
这是一个个人可运行的后端实习项目，我重点实现了社区业务的核心链路和工程边界。对于生产级能力，比如真实支付、厂商 Push、完善监控、自动化测试和压测，我在文档中标为后续演进，没有夸大成已上线能力。
```

## 14. 最后建议

学习这个项目时，不要只背“用了 Redis、RocketMQ、MySQL”。面试官更关心的是：

1. 为什么这个模块需要 Redis？
2. Redis 丢了怎么办？
3. MQ 重复消费怎么办？
4. 写库成功但消息没发出去怎么办？
5. 用户重复点击怎么办？
6. 跨服务数据怎么查？
7. 为什么这个能力没有拆成独立服务？
8. 现在的方案离生产还差什么？

BlueNote 的价值就在这些问题里。把这些问题讲清楚，比多堆两个新功能更像一个成熟的后端实习项目。
