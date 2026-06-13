# BlueNote Backend

BlueNote 后端采用 Java 21、Spring Boot 3.5.x、Spring Cloud 2025.0.x 和 Maven 多模块结构。

当前后端已经支撑四条业务链路的本地联调基线：

```text
第一条主链路：注册/登录 -> 用户资料 -> 图片上传 -> 发布笔记 -> 笔记详情
第二条社交链路：关注 -> Feed -> 互动 -> 计数 -> 通知 -> Push
第三条实时链路：设备注册 -> WebSocket -> IM 单聊 -> 在线投递
第四条订单链路：活动预热 -> 秒杀 -> 订单 -> MOCK 支付/发券 -> 通知
```

接口字段、错误码、DDL、Redis Key、MQ 事件和权限边界以 `docs/contracts/` 为准；本文只记录当前后端运行状态。

## 1. 模块

```text
backend/
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
  bluenote-content-app/
  bluenote-social-app/
  bluenote-order-app/
  sql/
```

物理应用：

| 应用 | 端口 | 当前职责 |
|---|---:|---|
| `bluenote-gateway-app` | 8080 | 网关、JWT 校验、路由、用户上下文 Header 注入、WebSocket 转发 |
| `bluenote-member-app` | 8081 | auth、user、资料编辑和头像/封面文件校验绑定 |
| `bluenote-content-app` | 8082 | file、note、comment、笔记互动列表 |
| `bluenote-social-app` | 8083 | relation、counter、feed、rank、notification、push、im |
| `bluenote-order-app` | 8084 | 神券活动、秒杀、订单、MOCK 支付、发券、订单运维 |

## 2. 当前实现状态

### 2.1 Gateway

`bluenote-gateway-app` 已实现：

1. Access Token JWT 校验和公开接口放行。
2. 认证失败统一响应。
3. 向下游注入 `X-User-Id`、`X-Device-Id`、`X-Session-Id`、`X-Trace-Id`。
4. member/content/social/order 路由。
5. `/ws/realtime` WebSocket 转发到 social-app。

### 2.2 Member App

`bluenote-member-app` 已落 MySQL，包含 auth/user 第一条主链路能力。

auth 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/auth/register` | 注册 |
| `POST /api/auth/login` | 登录 |
| `POST /api/auth/token/refresh` | Refresh Token 轮换 |
| `POST /api/auth/logout` | 登出 |
| `POST /api/auth/password/change` | 修改密码 |

user 外部接口：

| 接口 | 说明 |
|---|---|
| `GET /api/users/me` | 当前用户资料 |
| `PUT /api/users/me/profile` | 修改当前用户资料 |
| `GET /api/users/{userId}/public` | 用户公开资料 |
| `GET /api/users/{userId}/home` | 用户主页头部 |

已接入能力：

1. BCrypt 密码哈希、Access Token 签发、Refresh Token 哈希存储和轮换。
2. 设备会话、登录审计、用户资料审计。
3. auth/user outbox 写库。
4. 资料编辑使用 `profileVersion` 做并发控制。
5. 头像和主页封面会调用 file 内部接口校验归属、场景、状态、类型和大小，保存访问 URL 快照，并绑定 `USER_AVATAR` / `USER_HOME_COVER` 文件。
6. 用户主页头部计数通过 counter 聚合 relation/note 来源返回，异常时降级展示。

### 2.3 Content App

`bluenote-content-app` 已接入 MySQL 和 MinIO，包含 file/note/comment 能力。

file 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/files/upload-token` | 申请预签名上传凭证 |
| `POST /api/files/{fileId}/confirm` | 确认上传完成 |
| `GET /api/files/{fileId}/access-url` | 获取文件访问地址 |

note 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/notes/drafts` | 保存草稿 |
| `POST /api/notes` | 发布笔记 |
| `POST /api/notes/{noteId}/publish` | 草稿发布 |
| `DELETE /api/notes/{noteId}` | 删除笔记 |
| `GET /api/notes/{noteId}` | 笔记详情 |
| `GET /api/notes/users/{userId}` | 用户笔记列表 |
| `GET /api/notes/me` | 我的笔记列表 |
| `GET /api/notes/me/collections` | 我的收藏列表 |
| `GET /api/notes/me/likes` | 我的赞过列表 |
| `POST /api/notes/{noteId}/like` | 笔记点赞 |
| `DELETE /api/notes/{noteId}/like` | 取消笔记点赞 |
| `POST /api/notes/{noteId}/collect` | 笔记收藏 |
| `DELETE /api/notes/{noteId}/collect` | 取消笔记收藏 |

comment 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/comments/notes/{noteId}` | 发布一级评论 |
| `POST /api/comments/{commentId}/replies` | 回复评论 |
| `DELETE /api/comments/{commentId}` | 删除评论 |
| `GET /api/comments/notes/{noteId}` | 查询笔记一级评论 |
| `GET /api/comments/{rootCommentId}/replies` | 查询回复列表 |
| `POST /api/comments/{commentId}/like` | 评论点赞 |
| `DELETE /api/comments/{commentId}/like` | 取消评论点赞 |
| `GET /api/comments/me` | 我的评论 |

已接入能力：

1. MinIO 预签名 PUT 上传。
2. 文件元数据、上传会话、绑定关系落库。
3. 笔记、媒体、版本、话题、幂等请求和互动明细落库。
4. 笔记发布、删除、点赞、收藏写 note outbox。
5. 笔记详情和列表优先通过 counter 返回计数，异常时降级。
6. 评论、回复、删除、评论点赞落库并写 comment outbox。

### 2.4 Social App

`bluenote-social-app` 当前承载 relation/counter/feed/rank/notification/push/im。

已接入能力：

1. relation：关注、取关、关注列表、粉丝列表、关注状态、relation outbox。
2. counter：`counter_snapshot`、delta 流水、消费幂等、Redis 在线计数、重建任务、`CounterChanged` outbox。
3. feed：关注页 Feed、收件箱、fanout 任务、重建任务、Redis/MySQL/回源降级读路径、feed outbox。
4. rank：周热笔记榜、年度创作者成长榜、Redis 在线榜、MySQL 分数事实、快照、重建、`RankChanged` outbox。
5. notification：点赞/收藏聚合通知、评论/回复/关注/系统/订单通知、未读数、重建、notification outbox。
6. push：设备、偏好、投递请求、投递尝试、点击日志、WebSocket 在线投递、ACK 落库、push outbox。
7. im：单聊文字消息、会话列表、消息列表、未读、已读、送达、IM outbox 和 `PushSendRequested`。

当前已启动的 RocketMQ consumer 包括 counter/feed/rank/notification/push/order/IM 相关消费组。真实 uni-push / 厂商 Push 离线通道仍是后续扩展点。

### 2.5 Order App

`bluenote-order-app` 已接入神券订单 foundation、库存可靠性和活动运营最小闭环。

主要外部接口：

| 接口 | 说明 |
|---|---|
| `GET /api/order/coupon-activities/current` | 当前可参与活动 |
| `POST /api/order/seckill/token` | 获取秒杀令牌 |
| `POST /api/order/seckill/orders` | 提交抢券请求 |
| `GET /api/order/seckill/results/{requestId}` | 查询抢券结果 |
| `GET /api/order/orders/{orderId}` | 查询订单详情 |
| `POST /api/order/orders/{orderId}/pay` | MOCK 支付 |
| `POST /api/order/orders/{orderId}/cancel` | 取消待支付订单 |
| `GET /api/order/my-coupons` | 我的神券 |

已接入能力：

1. 活动配置、预热、状态流转、上线前预检查。
2. 秒杀 token、Redis Lua 预扣库存、异步下单。
3. 免费券发券、MOCK 支付、超时关单和库存回补。
4. 活动列表/详情、库存调整、库存对账修复、Redis 库存重建、卡住请求收敛。
5. order outbox、MQ listener、订单通知事件。

### 2.6 MQ 与 Outbox

`bluenote-common-mq` 已提供基础 MQ/outbox 能力：

1. 通用 RocketMQ producer 和 consumer 容器。
2. 事件类型到 Topic 的契约映射。
3. 通用 outbox dispatcher，扫描 `INIT` / `FAILED` 事件并发送 RocketMQ。
4. 发送成功标记 `SENT`，失败标记 `FAILED`、增加 `retry_count` 并写入 `next_retry_at`。
5. 内部运维接口可查询积压、手动触发发送和重置失败事件。

当前接入的 outbox 表：

| 应用 | 表 |
|---|---|
| member | `bluenote_auth.auth_outbox_event`、`bluenote_user.user_outbox_event` |
| content | `bluenote_file.file_outbox_event`、`bluenote_note.note_outbox_event`、`bluenote_comment.comment_outbox_event` |
| social | `bluenote_relation.relation_outbox_event`、`bluenote_counter.counter_outbox_event`、`bluenote_feed.feed_outbox_event`、`bluenote_rank.rank_outbox_event`、`bluenote_notification.notification_outbox_event`、`bluenote_push.push_outbox_event`、`bluenote_im.im_outbox_event` |
| order | `bluenote_order.order_outbox_event` |

## 3. 本地要求

1. JDK 21。
2. Maven 可用；本地已知旧 Maven 环境下 `mvn -q -DskipTests compile` 可用。
3. Docker Desktop 或可用 Docker Compose。
4. Node.js/npm 用于移动端 H5。

启动 local 依赖：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

## 4. 构建与运行

后端编译：

```bash
cd backend
mvn -q -DskipTests compile
```

本地运行单个应用时，优先进入应用目录启动，避免旧 Maven / Spring Boot 插件在父工程上误判 main class：

```powershell
cd backend/bluenote-member-app
mvn -q -DskipTests spring-boot:run
```

建议分别开终端按顺序启动：

```powershell
cd backend/bluenote-member-app
mvn -q -DskipTests spring-boot:run
```

```powershell
cd backend/bluenote-content-app
mvn -q -DskipTests spring-boot:run
```

```powershell
cd backend/bluenote-social-app
mvn -q -DskipTests spring-boot:run
```

```powershell
cd backend/bluenote-order-app
mvn -q -DskipTests spring-boot:run
```

```powershell
cd backend/bluenote-gateway-app
mvn -q -DskipTests spring-boot:run
```

如果运行时出现 `NoSuchFieldError: COUNTER_*` 一类错误，通常是本地 Maven 仓库里 common 模块旧了。优先重新编译当前源码；确需安装本地模块时再执行 `mvn -q -DskipTests install`，注意旧 Maven/Surefire 组合可能在 install 阶段卡住。

## 5. 本地默认配置

本地默认值在各应用 `application.yml` 中提供，可通过环境变量覆盖。

| 配置 | 默认值 |
|---|---|
| MySQL root 密码 | `bluenote_root_local` |
| member 数据源 | `jdbc:mysql://127.0.0.1:3306/bluenote_auth` |
| content 数据源 | `jdbc:mysql://127.0.0.1:3306/bluenote_file` |
| social 数据源 | `jdbc:mysql://127.0.0.1:3306/bluenote_relation` |
| order 数据源 | `jdbc:mysql://127.0.0.1:3306/bluenote_order` |
| Redis | `127.0.0.1:6379` |
| MinIO endpoint | `http://127.0.0.1:9000` |
| MinIO bucket | `bluenote-files` |
| RocketMQ NameServer | `127.0.0.1:9876` |
| Gateway member URI | `http://127.0.0.1:8081` |
| Gateway content URI | `http://127.0.0.1:8082` |
| Gateway social URI | `http://127.0.0.1:8083` |
| Gateway order URI | `http://127.0.0.1:8084` |
| Gateway social WebSocket URI | `ws://127.0.0.1:8083` |

注意：应用数据源按拥有者 schema 连接，SQL 中使用显式 schema 访问同应用内的逻辑库。例如 social-app 使用 `bluenote_relation` 连接，同时访问 relation/counter/feed/rank/notification/push/im 相关 schema。

## 6. 检查入口

Probe：

| 应用 | URL |
|---|---|
| gateway | `http://127.0.0.1:8080/internal/gateway/probe` |
| member | `http://127.0.0.1:8081/internal/member/probe` |
| content | `http://127.0.0.1:8082/internal/content/probe` |
| social | `http://127.0.0.1:8083/api/social/probe` |
| order | `http://127.0.0.1:8084/internal/order/probe` |

Actuator health：

```text
http://127.0.0.1:{port}/actuator/health
```

OpenAPI UI：

```text
http://127.0.0.1:{port}/swagger-ui.html
```

第一条主链路冒烟：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-main-chain.ps1 -GatewayBaseUrl http://127.0.0.1:8080
```

## 7. 已知待办

1. 为 auth/user/file/note 补 Java 层最小接口或集成测试。
2. 建立 OpenAPI JSON 与 `docs/contracts/api/` 的自动 diff。
3. 补 RocketMQ 死信告警和更完整的人工重放审计。
4. 补真实 uni-push / 厂商 Push 离线投递通道、凭证配置和失败回执处理。
5. 补 feed 大 V 推拉结合策略、清理任务后台化和更完整的补偿审计。
6. 补 rank 定时快照/冻结、历史榜单和运营后台。
7. 补真实支付渠道、退款、订单运营后台和高并发压测。
8. 补生产部署、备份恢复、监控告警配置。
