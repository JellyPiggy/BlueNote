# BlueNote Backend

BlueNote 后端采用 Java 21、Spring Boot 3.5.x、Spring Cloud 2025.0.x 和 Maven 多模块结构。

当前后端已经支撑第一条主链路：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情
```

第二条主链路已开始落地，当前完成 relation/comment/counter/feed/notification/push 起步纵切面，并已接入基础 MQ/outbox 闭环：

```text
关注用户 -> 关系事实落库 -> relation-event outbox
评论/回复 -> 评论事实落库 -> comment-event outbox
outbox dispatcher -> RocketMQ -> counter 自动消费 -> counter-event outbox
NotePublished/UserFollowed -> feed 自动消费 -> 收件箱/重建/feed-event outbox
点赞/收藏/评论/关注/笔记状态事件 -> notification 自动消费 -> 站内通知/未读数/notification-event/push-request-event outbox
PushSendRequested -> push 自动消费 -> 设备/偏好过滤 -> 投递请求/尝试日志/push-event outbox
```

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
  sql/
```

物理应用：

| 应用 | 端口 | 当前职责 |
|---|---:|---|
| `bluenote-gateway-app` | 8080 | 网关、JWT 校验、路由、用户上下文 Header 注入 |
| `bluenote-member-app` | 8081 | auth、user |
| `bluenote-content-app` | 8082 | file、note、comment |
| `bluenote-social-app` | 8083 | relation、counter、feed、notification、push，后续承载 rank |

## 2. 当前实现状态

### 2.1 Gateway

`bluenote-gateway-app` 已实现：

1. Access Token JWT 校验。
2. 公开接口放行。
3. 认证失败统一响应。
4. 向下游注入：
   - `X-User-Id`
   - `X-Device-Id`
   - `X-Session-Id`
   - `X-Trace-Id`
5. member/content/social 路由。

### 2.2 Member App

`bluenote-member-app` 已从内存占位改为 MySQL 落库。

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

user 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/users/register-profile` | 注册后创建默认用户资料 |
| `POST /internal/users/batch-summary` | 批量查询用户摘要 |
| `POST /internal/users/status-check` | 批量校验用户状态 |

已接入能力：

1. BCrypt 密码哈希。
2. Access Token 签发。
3. Refresh Token 哈希存储和轮换。
4. 设备会话。
5. 登录审计。
6. 用户资料审计。
7. auth/user outbox 写库。
8. 用户主页头部计数通过 counter 聚合 relation/note 来源返回，异常时降级展示。

### 2.3 Content App

`bluenote-content-app` 已从占位接口接入 MySQL 和 MinIO，并补齐 comment 最小可用链路。

file 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/files/upload-token` | 申请预签名上传凭证 |
| `POST /api/files/{fileId}/confirm` | 确认上传完成 |
| `GET /api/files/{fileId}/access-url` | 获取文件访问地址 |

file 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/files/validate` | 校验文件 |
| `POST /internal/files/batch-validate` | 批量校验文件 |
| `POST /internal/files/bind` | 绑定文件 |
| `POST /internal/files/batch-bind` | 批量绑定文件 |

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

note 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/notes/batch-summary` | 批量笔记摘要 |
| `POST /internal/notes/comment-check` | 评论前校验 |
| `POST /internal/notes/counter-source` | 笔记和用户作品计数来源 |

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

comment 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/comments/batch-summary` | 批量评论摘要 |
| `POST /internal/comments/counter-source` | 评论计数来源 |

已接入能力：

1. MinIO 预签名 PUT 上传。
2. 文件元数据、上传会话、绑定关系落库。
3. 笔记、媒体、版本、话题、幂等请求落库。
4. 发布、删除、点赞、取消点赞、收藏、取消收藏写 note outbox。
5. 笔记详情聚合作者、媒体、counter 计数结构和 viewerAction，counter 异常时降级展示。
6. 评论、回复、删除、评论点赞落 MySQL。
7. 评论发布/删除/点赞写 comment outbox。
8. 评论列表聚合作者、正文、计数快照和 viewerAction。

限制：note/comment/file outbox 已可由通用 dispatcher 投递 RocketMQ；feed 已消费 note/relation 事件生成关注页读模型，notification 已消费 interaction/comment/relation/note 状态事件生成站内通知。

### 2.4 Social App

`bluenote-social-app` 已新增 relation/counter/feed/notification/push 最小可用链路。

relation 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/relations/following/{followeeId}` | 关注用户 |
| `DELETE /api/relations/following/{followeeId}` | 取消关注 |
| `GET /api/relations/users/{userId}/following` | 查询关注列表 |
| `GET /api/relations/users/{userId}/followers` | 查询粉丝列表 |
| `GET /api/relations/following/{targetUserId}/status` | 查询单个关注状态 |
| `POST /api/relations/following/status/batch` | 批量查询关注状态 |

relation 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/relations/following/status/batch` | 内部批量查询关注状态 |
| `GET /internal/relations/users/{userId}/followers/page` | Feed 扩散分页查询粉丝 |
| `GET /internal/relations/users/{userId}/following/page` | Feed 查询和重建分页查询关注 |
| `POST /internal/relations/counter-source` | 计数服务校准来源 |

counter 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/counters/batch` | 批量聚合 NOTE / USER / COMMENT 计数 |
| `POST /internal/counters/events/consume` | 内部事件消费入口，可用于 MQ listener、补偿和本地联调 |
| `POST /internal/counters/reconcile` | 计数校准和重建 |
| `GET /internal/counters/rebuild-tasks/{taskId}` | 查询重建任务 |
| `POST /internal/counters/warmup` | 批量预热计数 |

feed 外部接口：

| 接口 | 说明 |
|---|---|
| `GET /api/feed/following` | 关注页 Feed，优先读取 Redis/MySQL 收件箱，降级时回源关注作者近期公开笔记 |

feed 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/feed/users/{userId}/rebuild` | 触发用户 Feed 收件箱重建 |
| `GET /internal/feed/rebuild-tasks/{taskId}` | 查询 Feed 重建任务 |
| `GET /internal/feed/fanout-tasks/{taskId}` | 查询笔记 Feed 投递任务 |
| `POST /internal/feed/fanout-tasks/{taskId}/retry` | 手动重试 Feed 投递任务 |

notification 外部接口：

| 接口 | 说明 |
|---|---|
| `GET /api/notifications/unread-count` | 查询总未读数和分类未读数 |
| `GET /api/notifications` | 按分类和游标查询通知列表 |
| `GET /api/notifications/{notificationId}` | 查询通知详情 |
| `POST /api/notifications/{notificationId}/read` | 标记单条已读，幂等 |
| `POST /api/notifications/read-all` | 按分类或全部标记已读 |
| `DELETE /api/notifications/{notificationId}` | 删除单条通知 |
| `DELETE /api/notifications` | 批量删除通知 |

notification 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/notifications/system` | 创建系统通知，按 requestId 幂等 |
| `POST /internal/notifications/batch-summary` | 批量查询通知摘要 |
| `POST /internal/notifications/users/{userId}/rebuild-unread` | 从通知事实表重建用户未读数 |
| `POST /internal/notifications/events/replay` | 基于消费记录重放事件 |

push 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/push/devices/register` | 登录后注册或更新当前设备 |
| `GET /api/push/devices` | 查询当前用户设备列表 |
| `DELETE /api/push/devices/{deviceId}` | 解绑当前用户设备 |
| `GET /api/push/preferences` | 查询当前用户推送偏好 |
| `PUT /api/push/preferences` | 更新当前用户推送偏好 |
| `POST /api/push/clicks` | 记录 Push 点击回传 |

push 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/push/requests/send` | 创建或复用推送请求并执行投递 |
| `GET /internal/push/requests/{requestId}` | 查询推送请求和尝试日志 |
| `POST /internal/push/requests/{requestId}/retry` | 重试推送请求 |
| `POST /internal/push/events/replay` | 基于消费记录重放 Push 事件 |

已接入能力：

1. `relation_following` 关注事实落库。
2. `relation_follower` 粉丝方向读表同步。
3. `relation_change_log` 变更流水。
4. `relation_outbox_event` 写出 `UserFollowed` / `UserUnfollowed`。
5. 关注列表、粉丝列表和关注状态走 MySQL 查询。
6. 通过 member 内部接口校验用户状态和补全用户摘要。
7. counter 查询聚合 relation、note、comment 的 `counter-source`，供用户主页等接口使用。
8. counter 已有快照表、delta 流水、消费幂等、Redis 在线计数、重建任务和 `CounterChanged` outbox。
9. social-app 已启动 RocketMQ counter consumer，自动消费 `note-event`、`interaction-event`、`comment-event`、`relation-event`。
10. feed 已落 `bluenote_feed.feed_note_index`、`feed_inbox_item`、`feed_fanout_task`、`feed_fanout_sub_task`、`feed_rebuild_task`、`feed_consume_record`、`feed_outbox_event`。
11. feed 已启动 RocketMQ consumer，自动消费：
   - `bluenote-feed-note-consumer`：`note-event`
   - `bluenote-feed-relation-consumer`：`relation-event`
   - `bluenote-feed-fanout-executor`：`feed-fanout-task-event`
12. Feed 读取优先级为 Redis 收件箱 -> MySQL 收件箱 -> 关注作者近期公开笔记回源，并在读取时过滤已取关作者和不可见笔记。
13. notification 已落 `bluenote_notification.notification_record`、`notification_aggregate_actor`、`notification_unread_counter`、`notification_consume_record`、`notification_outbox_event`。
14. notification 支持点赞/收藏聚合通知、评论/回复明细通知、关注通知、系统通知、未读数 Redis 缓存和 MySQL 重建。
15. notification 已启动 RocketMQ consumer，自动消费：
   - `bluenote-notification-interaction-consumer`：`interaction-event`
   - `bluenote-notification-comment-consumer`：`comment-event`
   - `bluenote-notification-relation-consumer`：`relation-event`
   - `bluenote-notification-note-consumer`：`note-event`
16. push 已落 `bluenote_push.push_device`、`push_preference`、`push_delivery_request`、`push_delivery_attempt`、`push_click_log`、`push_consume_record`、`push_outbox_event`。
17. push 已启动 `bluenote-push-request-consumer`，自动消费 `push-request-event` 中的 `PushSendRequested`。
18. push 当前投递通道为 `NOOP` 基座，可完成幂等、偏好过滤、免打扰过滤、活跃设备过滤、投递尝试日志和 `PushDelivered` / `PushFiltered` outbox；真实 WebSocket / uni-push / 厂商 Push 后续接入。

### 2.5 MQ 与 Outbox

`bluenote-common-mq` 已提供基础 MQ/outbox 能力：

1. 通用 RocketMQ producer。
2. 通用 RocketMQ consumer 容器。
3. 事件类型到 Topic 的契约映射。
4. 通用 outbox dispatcher，扫描 `INIT` / `FAILED` 事件并发送 RocketMQ。
5. 发送成功标记 `SENT`，发送失败标记 `FAILED`、增加 `retry_count` 并写入 `next_retry_at`。
6. 内部运维接口可查询积压、手动触发发送和重置失败事件。

当前接入的 outbox 表：

| 应用 | 表 |
|---|---|
| member | `bluenote_auth.auth_outbox_event`、`bluenote_user.user_outbox_event` |
| content | `bluenote_file.file_outbox_event`、`bluenote_note.note_outbox_event`、`bluenote_comment.comment_outbox_event` |
| social | `bluenote_relation.relation_outbox_event`、`bluenote_counter.counter_outbox_event`、`bluenote_feed.feed_outbox_event`、`bluenote_notification.notification_outbox_event`、`bluenote_push.push_outbox_event` |

本地默认启用 MQ/outbox，可通过环境变量关闭：

| 环境变量 | 说明 | 默认 |
|---|---|---|
| `BLUENOTE_MQ_ENABLED` | 是否启动 RocketMQ producer/consumer | `true` |
| `BLUENOTE_OUTBOX_DISPATCH_ENABLED` | 是否启动 outbox dispatcher | `true` |
| `BLUENOTE_ROCKETMQ_NAME_SERVER` | RocketMQ NameServer 地址 | `127.0.0.1:9876` |

MQ/outbox 内部运维接口：

| 接口 | 说明 |
|---|---|
| `GET /internal/mq/outbox/stats` | 查询当前应用声明的 outbox 表积压、可重试失败、死信和已发送数量 |
| `POST /internal/mq/outbox/dispatch-once` | 手动触发当前应用扫描发送一次 |
| `POST /internal/mq/outbox/events/retry` | 将指定失败事件重置为可重试状态 |

## 3. 本地要求

1. JDK 21。
2. Maven，建议 3.6.3 或更高版本。
3. Docker Desktop 或可用 Docker Compose。
4. 先启动 local 依赖：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

## 4. 构建与运行

后端编译：

```bash
cd backend
mvn -q -DskipTests compile
```

如果单独运行某个应用时提示找不到本仓库内的 common 模块，可先安装本地模块：

```bash
mvn -q -DskipTests install
```

分别启动应用：

```bash
cd backend
mvn -pl bluenote-member-app spring-boot:run
mvn -pl bluenote-content-app spring-boot:run
mvn -pl bluenote-social-app spring-boot:run
mvn -pl bluenote-gateway-app spring-boot:run
```

建议启动顺序：

1. 本地依赖。
2. `bluenote-member-app`。
3. `bluenote-content-app`。
4. `bluenote-social-app`。
5. `bluenote-gateway-app`。
6. 移动端 H5。

## 5. 本地默认配置

本地默认值在各应用 `application.yml` 中提供，可通过环境变量覆盖。

| 配置 | 默认值 |
|---|---|
| MySQL root 密码 | `bluenote_root_local` |
| member 数据源 | `jdbc:mysql://127.0.0.1:3306/bluenote_auth` |
| content 数据源 | `jdbc:mysql://127.0.0.1:3306/bluenote_file` |
| social 数据源 | `jdbc:mysql://127.0.0.1:3306/bluenote_relation` |
| MinIO endpoint | `http://127.0.0.1:9000` |
| MinIO bucket | `bluenote-files` |
| RocketMQ NameServer | `127.0.0.1:9876` |
| Gateway member URI | `http://127.0.0.1:8081` |
| Gateway content URI | `http://127.0.0.1:8082` |
| Gateway social URI | `http://127.0.0.1:8083` |

注意：member/content/social 当前各自只配置一个 datasource URL，但 DDL 中按逻辑 schema 拆分为 `bluenote_auth`、`bluenote_user`、`bluenote_file`、`bluenote_note`、`bluenote_comment`、`bluenote_relation`、`bluenote_counter`、`bluenote_feed`、`bluenote_notification`、`bluenote_push`。应用内 SQL 使用显式 schema 名访问本应用拥有的逻辑 schema。

## 6. 检查入口

Probe：

| 应用 | URL |
|---|---|
| gateway | `http://127.0.0.1:8080/internal/gateway/probe` |
| member | `http://127.0.0.1:8081/internal/member/probe` |
| content | `http://127.0.0.1:8082/internal/content/probe` |
| social | `http://127.0.0.1:8083/api/social/probe` |

Actuator health：

```text
http://127.0.0.1:{port}/actuator/health
```

OpenAPI UI：

```text
http://127.0.0.1:{port}/swagger-ui.html
```

## 7. 已知待办

1. 补后端自动化测试和接口集成测试。
2. 补 RocketMQ 死信告警和更完整的人工重放审计。
3. 补真实 WebSocket / uni-push 投递通道、ACK 和移动端设备注册联调。
4. 补 feed 大 V 推拉结合策略、清理任务后台化和更完整的补偿审计。
5. 补生产部署、备份恢复、监控告警配置。
