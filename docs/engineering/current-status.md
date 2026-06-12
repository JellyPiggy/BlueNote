# BlueNote 当前工程状态

版本：v0.14
状态：第一条主链路完成，第二条社交链路 relation/counter/feed/notification/push 持续落地，第三条实时链路 IM 单聊最小纵切面已接入
更新时间：2026-06-12
当前分支：`codex/im-foundation`
当前基线提交：以本分支最新提交为准

## 1. 文档用途

本文记录 BlueNote 当前已经完成的工程资产、可运行范围、待完成事项和已知风险，用于后续继续开发时快速接上上下文。

本文不替代：

1. `AGENTS.md`：长期协作规则和边界。
2. `docs/contracts/`：API、DDL、Redis、MQ、安全权限契约。
3. `方案/`：架构方案、服务设计和 ADR。

如果后续修改了接口字段、错误码、DDL、Redis Key 或 MQ 事件，必须优先同步 `docs/contracts/`，不能只更新本文。

## 2. 当前总体状态

BlueNote 已经从纯方案阶段推进到“第一条主链路可联调基线”阶段，并开始落地第二条社交链路。

当前主链路目标：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情
```

当前完成度：

| 范围 | 状态 | 说明 |
|---|---|---|
| 契约 | 已建立第一、第二条主链路基线 | API、错误码、DDL、Redis、MQ、安全权限文档已进入 `docs/contracts/` |
| 本地依赖 | 已建立 local Compose | MySQL、Redis、Nacos、RocketMQ、MinIO 使用常用本地端口 |
| 后端基础 | 已搭建 Maven 多模块 | common、gateway、member、content、social 已有可执行结构 |
| member-app | 已从内存占位改为 MySQL | auth/user 注册、登录、Token、用户资料等主链路接口已落库 |
| gateway-app | 已补 JWT 校验 | 网关校验 Access Token 并注入用户上下文 Header，已转发 `/ws/realtime` |
| content-app | 已从占位接口接入 MySQL/MinIO | file/note/comment 可申请上传凭证、发布笔记、查询详情、发布评论，笔记详情和列表计数已切到 counter 优先 |
| social-app | 已新增 relation/counter/feed/notification/push/im 纵切面 | 关注、取关、关注/粉丝列表、关注状态、counter 快照/Redis/重建/自动 MQ 消费、feed 收件箱/fanout/重建/自动 MQ 消费、通知读模型/未读数/自动 MQ 消费、push 设备/偏好/WebSocket 实时投递/投递日志、IM 单聊文字消息/未读/已读/送达/outbox 已落地 |
| MQ/outbox | 已接入基础闭环 | 通用 outbox dispatcher、RocketMQ producer/consumer 容器、counter/feed/notification/push 自动 listener 已落地，IM outbox 已接入通用 dispatcher |
| mobile | 已接入 H5 主链路、通知中心、realtime 和私信最小页 | 登录、首页、发布、详情、我的页、消息页已接真实 API，登录后自动注册 push 设备并连接 WebSocket；私信列表和聊天页已接 IM API |
| UI | 已完成一轮移动端视觉优化 | 信息流、笔记详情、个人页按移动端社区产品风格重做 |

## 3. 已完成内容

### 3.1 契约与文档

已完成第一、第二条主链路契约基线：

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
  db/
    01-main-chain-schema.md
    02-social-chain-schema.md
  redis/
    01-main-chain-keys.md
    02-social-chain-keys.md
  mq/
    01-main-chain-events.md
    02-social-chain-events.md
  security/
    01-permission-matrix.md
    02-social-chain-permission-matrix.md
```

已补工程规范：

```text
docs/engineering/01-engineering-structure-and-coding-guidelines.md
```

本文用于补充“当前做到哪里”的交接视图。

### 3.2 数据库与本地依赖

已完成第一批 DDL：

```text
backend/sql/
  V001__auth.sql
  V002__user.sql
  V003__file.sql
  V004__note.sql
  V005__relation.sql
  V006__comment.sql
  V007__counter.sql
  V008__feed.sql
  V009__notification.sql
  V010__note_interaction_lists.sql
  V011__push.sql
  V012__push_realtime.sql
  V013__im.sql
```

本地依赖已配置：

| 组件 | 端口 | 说明 |
|---|---:|---|
| MySQL | 3306 | 初始化挂载 `backend/sql/` |
| Redis | 6379 | 本地开发密码见 `deploy/env/local.env.example` |
| Nacos | 8848 | local 注册中心和配置中心 |
| RocketMQ NameServer | 9876 | local MQ |
| RocketMQ Broker | 10911 | local MQ Broker |
| MinIO API | 9000 | 对象存储 API |
| MinIO Console | 9001 | 对象存储控制台 |

启动命令：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

注意：MySQL Docker 初始化脚本只会在数据卷首次创建时自动执行。已有旧数据卷时，如果 DDL 有变化，需要手动执行 SQL 或重建本地数据卷。

### 3.3 后端工程

后端当前模块：

```text
backend/
  bluenote-dependencies/
  bluenote-common/
  bluenote-gateway-app/
  bluenote-member-app/
  bluenote-content-app/
  bluenote-social-app/
  sql/
```

已完成基础能力：

1. Java 21 + Spring Boot 3.5.x + Spring Cloud 2025.0.x Maven 多模块。
2. 统一响应 `ApiResponse`、错误码、业务异常、全局异常处理。
3. traceId 和基础 Web 上下文。
4. common security 中的 JWT Access Token 签发与解析。
5. common mybatis、redis、mq、observability 基础包结构。
6. OpenAPI / Swagger UI 基础接入。
7. common-mq 基础接入：
   - 通用 RocketMQ producer。
   - 通用 RocketMQ consumer 容器。
   - 事件类型到 Topic 的契约映射。
   - 通用 outbox dispatcher，支持 INIT/FAILED 扫描、发送成功标记 SENT、失败重试和 next_retry_at。

### 3.4 Gateway

`bluenote-gateway-app` 已补：

1. JWT Access Token 校验。
2. 公开路径放行。
3. 鉴权失败统一响应。
4. 用户上下文 Header 注入：
   - `X-User-Id`
   - `X-Device-Id`
   - `X-Session-Id`
   - `X-Trace-Id`
5. member/content/social 路由基础配置。
6. `/internal/gateway/probe` 探针。

### 3.5 Member App

`bluenote-member-app` 当前包含 auth/user 第一条主链路能力。

auth 已完成：

1. 注册：`POST /api/auth/register`
2. 登录：`POST /api/auth/login`
3. 刷新 Token：`POST /api/auth/token/refresh`
4. 登出：`POST /api/auth/logout`
5. 修改密码：`POST /api/auth/password/change`
6. 密码 BCrypt 哈希。
7. Refresh Token 哈希存储和轮换。
8. 设备会话表、登录审计表。
9. 注册时写 auth outbox，并创建默认用户资料。

user 已完成：

1. 当前用户资料：`GET /api/users/me`
2. 修改资料：`PUT /api/users/me/profile`
3. 公开资料：`GET /api/users/{userId}/public`
4. 用户主页头部：`GET /api/users/{userId}/home`
5. 内部注册资料：`POST /internal/users/register-profile`
6. 内部批量用户摘要：`POST /internal/users/batch-summary`
7. 内部用户状态校验：`POST /internal/users/status-check`
8. 用户资料修改审计和 user outbox。
9. 用户主页头部计数通过 counter 聚合 relation/note 来源返回，异常时降级展示。

### 3.6 Content App

`bluenote-content-app` 当前包含 file/note 第一条主链路能力。

file 已完成：

1. 上传凭证：`POST /api/files/upload-token`
2. 上传确认：`POST /api/files/{fileId}/confirm`
3. 访问地址：`GET /api/files/{fileId}/access-url`
4. 内部文件校验：`POST /internal/files/validate`
5. 内部批量文件校验：`POST /internal/files/batch-validate`
6. 内部文件绑定：`POST /internal/files/bind`
7. 内部批量文件绑定：`POST /internal/files/batch-bind`
8. 文件元数据、上传会话、绑定关系落 MySQL。
9. MinIO 预签名上传和访问 URL。
10. file outbox 写入。

note 已完成：

1. 保存草稿：`POST /api/notes/drafts`
2. 发布笔记：`POST /api/notes`
3. 草稿发布：`POST /api/notes/{noteId}/publish`
4. 删除笔记：`DELETE /api/notes/{noteId}`
5. 笔记详情：`GET /api/notes/{noteId}`
6. 用户笔记列表：`GET /api/notes/users/{userId}`
7. 我的笔记列表：`GET /api/notes/me`
8. 我的收藏列表：`GET /api/notes/me/collections`
9. 我的赞过列表：`GET /api/notes/me/likes`
10. 笔记点赞/取消点赞：`POST /api/notes/{noteId}/like`、`DELETE /api/notes/{noteId}/like`
11. 笔记收藏/取消收藏：`POST /api/notes/{noteId}/collect`、`DELETE /api/notes/{noteId}/collect`
12. 内部批量笔记摘要：`POST /internal/notes/batch-summary`
13. 内部评论前校验：`POST /internal/notes/comment-check`
14. 内部计数来源：`POST /internal/notes/counter-source`
15. 内部作者近期公开笔记：`POST /internal/notes/authors/recent`
16. 笔记、媒体、版本、话题、幂等请求、互动明细等表结构已接入。
17. 发布、删除、点赞、取消点赞、收藏、取消收藏写 note outbox。
18. 笔记详情 `counts` 优先通过 counter 返回 `likeCount`、`collectCount`、`commentCount`，笔记列表卡片优先通过 counter 批量返回点赞/收藏计数。

comment 已完成：

1. 发布一级评论：`POST /api/comments/notes/{noteId}`
2. 回复评论：`POST /api/comments/{commentId}/replies`
3. 删除评论：`DELETE /api/comments/{commentId}`
4. 笔记评论列表：`GET /api/comments/notes/{noteId}`
5. 回复列表：`GET /api/comments/{rootCommentId}/replies`
6. 评论点赞：`POST /api/comments/{commentId}/like`
7. 取消评论点赞：`DELETE /api/comments/{commentId}/like`
8. 我的评论：`GET /api/comments/me`
9. 内部批量评论摘要：`POST /internal/comments/batch-summary`
10. 内部评论计数来源：`POST /internal/comments/counter-source`
11. 评论事实、个人评论读模型、评论正文、评论点赞、幂等、操作日志、outbox 已落 MySQL。

计数说明：counter 查询失败不会阻断笔记主内容展示；详情页会返回降级计数并标记 `degraded=true`，列表卡片会回退到 note 互动明细中的点赞/收藏计数。

### 3.7 Social App

`bluenote-social-app` 当前包含 relation/counter/feed/notification/push/im 第二、三条主链路起步能力。

relation 已完成：

1. 关注用户：`POST /api/relations/following/{followeeId}`
2. 取消关注：`DELETE /api/relations/following/{followeeId}`
3. 关注列表：`GET /api/relations/users/{userId}/following`
4. 粉丝列表：`GET /api/relations/users/{userId}/followers`
5. 关注状态：`GET /api/relations/following/{targetUserId}/status`
6. 批量关注状态：`POST /api/relations/following/status/batch`
7. 内部计数来源：`POST /internal/relations/counter-source`

counter 已完成：

1. 内部批量计数：`POST /internal/counters/batch`
2. 内部事件消费核心入口：`POST /internal/counters/events/consume`
3. 计数校准和重建：`POST /internal/counters/reconcile`、`GET /internal/counters/rebuild-tasks/{taskId}`
4. 批量预热计数：`POST /internal/counters/warmup`
5. 已落 `bluenote_counter.counter_snapshot`、`counter_delta_log`、`counter_consume_record`、`counter_rebuild_task`、`counter_outbox_event`
6. 查询优先级为 Redis 在线计数 -> MySQL 快照 -> relation/note/comment 来源服务回源，回源成功后回填快照和 Redis。
7. 事件消费会生成 delta、更新快照、最佳努力更新 Redis，并写出 `CounterDeltaCreated`、`CounterChanged` outbox。
8. 支持 `NOTE.like_count`、`NOTE.collect_count`、`NOTE.comment_count`
9. 支持 `USER.following_count`、`USER.follower_count`、`USER.note_count`、`USER.liked_count`
10. 支持 `COMMENT.like_count`、`COMMENT.reply_count`
11. 已通过 RocketMQ listener 自动消费：
    - `bluenote-counter-note-consumer`：`note-event`、`interaction-event`
    - `bluenote-counter-comment-consumer`：`comment-event`
    - `bluenote-counter-relation-consumer`：`relation-event`

feed 已完成：

1. 关注页 Feed：`GET /api/feed/following?cursor=xxx&size=20`
2. 读路径优先读取 Redis 收件箱，再回退 MySQL 收件箱，再降级到关注作者近期公开笔记。
3. Feed 卡片聚合 member 用户摘要、note 摘要、counter 计数。
4. 按契约返回 `publishedAtMillis_noteId` 游标、`PUSH` / `PULL` / `FOLLOW_BACKFILL` 来源、`viewerAction=null` 和降级标记。
5. gateway 已增加 `/api/feed/**` 路由，并保持鉴权访问。
6. 已落 `bluenote_feed.feed_note_index`、`feed_inbox_item`、`feed_fanout_task`、`feed_fanout_sub_task`、`feed_rebuild_task`、`feed_cleanup_task`、`feed_consume_record`、`feed_outbox_event`。
7. 已消费 `NotePublished` 创建作者轻量索引、fanout 主/子任务，并通过 `feed-fanout-task-event` 投递到粉丝收件箱。
8. 已消费 `UserFollowed` 补作者近期公开笔记，消费 `UserUnfollowed` 清理收件箱，消费删除/私密/下架类事件标记不可见。
9. 已新增内部接口：
   - `POST /internal/feed/users/{userId}/rebuild`
   - `GET /internal/feed/rebuild-tasks/{taskId}`
   - `GET /internal/feed/fanout-tasks/{taskId}`
   - `POST /internal/feed/fanout-tasks/{taskId}/retry`
10. 已通过 RocketMQ listener 自动消费：
    - `bluenote-feed-note-consumer`：`note-event`
    - `bluenote-feed-relation-consumer`：`relation-event`
    - `bluenote-feed-fanout-executor`：`feed-fanout-task-event`

限制：feed 已完成最小可用写模型和读模型，但大 V 推拉结合策略、清理任务后台化、失败任务批量补偿和完整运维审计仍待增强。

notification 已完成：

1. 通知外部接口：
   - `GET /api/notifications/unread-count`
   - `GET /api/notifications`
   - `GET /api/notifications/{notificationId}`
   - `POST /api/notifications/{notificationId}/read`
   - `POST /api/notifications/read-all`
   - `DELETE /api/notifications/{notificationId}`
   - `DELETE /api/notifications`
2. 通知内部接口：
   - `POST /internal/notifications/system`
   - `POST /internal/notifications/batch-summary`
   - `POST /internal/notifications/users/{userId}/rebuild-unread`
   - `POST /internal/notifications/events/replay`
3. 已落 `bluenote_notification.notification_record`、`notification_aggregate_actor`、`notification_unread_counter`、`notification_consume_record`、`notification_outbox_event`。
4. 点赞和收藏通知按 `receiverId + notificationType + noteId` 聚合未读通知，已读后新事件创建新聚合通知。
5. 评论、回复、关注和系统通知按明细记录生成通知。
6. 未读数以 MySQL `notification_unread_counter` 为事实来源，Redis `bluenote:{env}:notification:unread:{userId}` 为缓存，可通过内部接口重建。
7. 通知创建、聚合、已读、删除写 `NotificationCreated`、`NotificationAggregated`、`NotificationRead`、`NotificationReadBatch`、`NotificationDeleted` outbox。
8. 通知创建后可靠写出 `PushSendRequested` outbox，push 服务可通过 MQ 消费形成投递请求和投递日志。
9. 已通过 RocketMQ listener 自动消费：
   - `bluenote-notification-interaction-consumer`：`interaction-event`
   - `bluenote-notification-comment-consumer`：`comment-event`
   - `bluenote-notification-relation-consumer`：`relation-event`
   - `bluenote-notification-note-consumer`：`note-event`

限制：notification 已完成站内通知最小闭环，移动端通知页和消息入口未读角标已接入；通知生命周期归档任务仍待增强，Push 真实通道由 push 服务后续接入。

push 已完成：

1. 推送外部接口：
   - `POST /api/push/devices/register`
   - `GET /api/push/devices`
   - `DELETE /api/push/devices/{deviceId}`
   - `GET /api/push/preferences`
   - `PUT /api/push/preferences`
   - `POST /api/push/clicks`
2. 推送内部接口：
   - `POST /internal/push/requests/send`
   - `GET /internal/push/requests/{requestId}`
   - `POST /internal/push/requests/{requestId}/retry`
   - `POST /internal/push/events/replay`
   - `GET /internal/push/users/{userId}/online-state`
   - `POST /internal/push/users/{userId}/kick`
3. 已落 `bluenote_push.push_device`、`push_preference`、`push_delivery_request`、`push_delivery_attempt`、`push_click_log`、`push_consume_record`、`push_outbox_event`，`push_delivery_attempt` 已补 `acked_at`。
4. 已通过 `bluenote-push-request-consumer` 自动消费 `push-request-event` 中的 `PushSendRequested`。
5. 推送请求按 `requestId` 和来源业务去重，投递前执行偏好、免打扰和活跃设备过滤。
6. `/ws/realtime` 已支持 token + deviceId 握手校验、在线设备 Redis 路由、`PING` / `PONG`、`PUSH_MESSAGE` 下行和客户端 `ACK` 落库。
7. 投递策略已升级为 WebSocket 在线优先；离线 uni-push / 厂商 Push 仍是配置关闭的扩展通道。

im 已完成：

1. IM 外部接口：
   - `POST /api/im/conversations/single`
   - `GET /api/im/conversations`
   - `POST /api/im/messages`
   - `GET /api/im/conversations/{conversationId}/messages`
   - `POST /api/im/conversations/{conversationId}/received`
   - `POST /api/im/conversations/{conversationId}/read`
   - `GET /api/im/unread-count`
   - `PUT /api/im/conversations/{conversationId}/settings`
   - `DELETE /api/im/conversations/{conversationId}`
2. IM 内部接口：
   - `POST /internal/im/conversations/batch-summary`
   - `GET /internal/im/conversations/{conversationId}/members/{userId}/push-policy`
   - `POST /internal/im/users/{userId}/rebuild-unread`
3. 已落 `bluenote_im.im_conversation`、`im_conversation_member`、`im_message`、`im_conversation_message`、`im_user_sequence`、`im_user_message`、`im_consume_record`、`im_outbox_event`。
4. 单聊会话按 `minUserId:maxUserId` 幂等创建，消息发送按 `senderId + clientMsgId` 幂等。
5. 发送文字消息会在同一事务内写会话消息链、用户消息链、成员未读数，并写出 `ImMessageSent` 和 `PushSendRequested` outbox。
6. 已读、送达写出 `ImMessageRead`、`ImMessageAcked` outbox，重复上报按最终序号幂等。
7. gateway 已增加 `/api/im/**` 路由；移动端私信列表和聊天页已接真实 API，WebSocket 收到 `IM_MESSAGE` 会刷新 IM 未读。

限制：第一阶段只支持单聊文字消息；群聊、图片/笔记卡片、撤回/编辑、黑名单、push 回执消费和多端增量同步仍待增强。

### 3.8 MQ/outbox 基座

已完成：

1. `bluenote-common-mq` 新增 RocketMQ producer 和 consumer 容器。
2. 事件类型到 Topic 的默认映射与 `docs/contracts/mq/` 对齐。
3. 通用 outbox dispatcher 扫描各应用声明的 outbox 表：
   - member：`auth_outbox_event`、`user_outbox_event`
   - content：`file_outbox_event`、`note_outbox_event`、`comment_outbox_event`
   - social：`relation_outbox_event`、`counter_outbox_event`、`feed_outbox_event`、`notification_outbox_event`、`push_outbox_event`、`im_outbox_event`
4. dispatcher 成功发送后标记 `SENT`，失败后标记 `FAILED`，增加 `retry_count` 并设置 `next_retry_at`。
5. social-app 新增 counter MQ listener，自动消费 note、interaction、comment、relation 事件并复用原有 counter 消费核心。
6. social-app 新增 feed MQ listener，自动消费 note、relation 和 feed fanout 内部任务事件。
7. social-app 新增 notification MQ listener，自动消费 interaction、comment、relation 和 note 状态事件。
8. social-app 新增 push MQ listener，自动消费 `push-request-event` 并写入投递请求、尝试日志和 push 结果 outbox。
9. 新增内部运维接口：
   - `GET /internal/mq/outbox/stats`
   - `POST /internal/mq/outbox/dispatch-once`
   - `POST /internal/mq/outbox/events/retry`
10. 本地默认启用：
   - `BLUENOTE_MQ_ENABLED=true`
   - `BLUENOTE_OUTBOX_DISPATCH_ENABLED=true`
   - `BLUENOTE_ROCKETMQ_NAME_SERVER=127.0.0.1:9876`

限制：

1. 已有失败重试、消费幂等和基础运维入口，但还没有死信告警和完整人工重放审计。
2. 当前自动 listener 已接 counter/feed/notification/push；IM 目前是业务事件和 Push 请求生产方，后续再消费 push 回执。
3. 后续还缺死信告警、人工重放审计和真实 uni-push / 厂商 Push 离线投递通道。

### 3.9 移动端

移动端技术栈：

1. uni-app
2. Vue 3
3. TypeScript
4. Pinia
5. `uni.request` 封装
6. H5 已作为当前主要验收目标

已完成目录：

```text
mobile/src/
  api/
  components/
  pages/
    home/
    login/
    im/
    notifications/
    note/detail/
    profile/
    publish/
  stores/
  utils/
```

已完成能力：

1. API 请求统一封装在 `mobile/src/api/`。
2. `code/message/data/traceId` 统一解析。
3. `20001` Token 过期时自动刷新并重试。
4. Pinia 管理登录态、Token、当前用户资料。
5. 注册/登录页面。
6. 首页信息流。
7. 发布笔记：图片选择、上传、确认、发布。
8. 笔记详情：图片轮播、作者、正文、计数展示、底部操作区。
9. 我的页面：封面背景、头像、账号信息、关注/粉丝/获赞、笔记/收藏/赞过 tab、账号侧边菜单。
10. 笔记详情页评论区：评论列表、回复列表、发布评论、回复、评论点赞、删除自己的评论。
11. 笔记详情页点赞、取消点赞、收藏、取消收藏已接真实 API。
12. 通知 API 已接 `GET /api/notifications/unread-count`、`GET /api/notifications`、单条已读、分类全部已读和单条删除。
13. 消息页已按互动、关注、系统三类 Tab 展示通知，支持分页加载、下拉刷新、空态、错误态、未登录态。
14. 首页和我的页已接消息入口未读角标，App 回到前台会刷新未读数。
15. 点击通知会先标记已读，再按 `jump.page` 跳转到笔记详情；用户主页和系统公告目标当前先做提示兜底。
16. 我的页“笔记 / 收藏 / 赞过”三 tab 已接真实数据，收藏和赞过列表按互动时间游标分页。
17. 私信列表和聊天页已接 `GET /api/im/conversations`、`GET /api/im/conversations/{conversationId}/messages`、发送消息、已读/送达和 IM 未读数。
18. WebSocket 收到 `scene=IM_MESSAGE` 的 `PUSH_MESSAGE` 后会刷新 IM 未读；通知类 Push 仍刷新通知未读。
19. H5 真实浏览器验收已做过多轮视觉检查。

当前 UI 风格已经从早期工程占位调整为移动端社区产品风格，但不使用小红书品牌元素。

## 4. 最近提交脉络

当前分支近期关键提交：

| 提交 | 内容 |
|---|---|
| `68d9da1` | 第一条主链路契约 |
| `e986667` | 后端基础工程 |
| `f74cea6` | 本地 MySQL/Redis 改回常用端口 |
| `70f2eee` | member auth/user MySQL 落库 |
| `90e2d40` | gateway JWT 鉴权 |
| `dd615e2` | content file/note MySQL/MinIO 落库 |
| `547afc5` | 移动端主链路 |
| `8d5e632` | 移动端整体视觉优化 |
| `bc5cfcd` | 笔记详情阅读布局优化 |
| `56a950f` | 详情页关注按钮文字居中 |
| `5feeae7` | 我的页封面区重做 |
| `36da5ed` | 我的页账号侧边菜单 |
| `2774283` | 我的页顶部贴边、统计和 tab 重排 |
| `7c35f3f` | 第二条社交链路契约 |
| `d1a86fc` | relation 后端最小纵切面 |
| `3b1021d` | counter 事件状态流水线 |
| `b0f216b` | feed fanout 基座 |
| `8588a24` | notification 通知读模型和 MQ 消费 |
| `718db8e` | 合并 notification foundation |
| `0275b5f` | 移动端通知中心 |
| `7d7df07` | 合并移动端通知中心 |
| `011c465` | 我的页互动列表 |
| `0d5b330` | 合并我的页互动列表 |
| `b4f66c2` | content 笔记读计数接 counter |
| `b03fd15` | 合并 content 笔记读计数 |
| `a454a35` | Push 投递基座 |

## 5. 当前运行与验证方式

本地依赖：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

后端常用检查：

```bash
cd backend
mvn -q -DskipTests compile
```

移动端常用检查：

```bash
cd mobile
npm run typecheck
npm run build:h5
npm run dev:h5
```

当前已知验证记录：

1. 移动端 `npm run typecheck` 通过。
2. 移动端 `npm run build:h5` 通过。
3. H5 页面通过浏览器验收过首页、发布、详情、我的页关键视觉。
4. `build:h5` 当前会输出 Dart Sass legacy JS API deprecation warning，暂不影响构建。
5. 后端 `mvn compile` 已通过，覆盖 gateway/member/content/social 模块。
6. `POST /internal/notes/counter-source`、`POST /internal/comments/counter-source`、`POST /internal/relations/counter-source` 已通过本地依赖验证。
7. `POST /internal/counters/batch` 已能聚合 NOTE / USER / COMMENT 计数并返回 `degraded=false`。
8. `GET /api/users/{userId}/home` 已通过 gateway 验证，可返回真实 `followingCount`、`followerCount`、`noteCount`、`likedCount`。
9. 新增 feed 读路径后，`cd backend && mvn -q -DskipTests compile` 通过。
10. 本轮新增 counter 快照、Redis 在线计数、事件消费入口、重建任务和 outbox 后，`cd backend && mvn -q -DskipTests compile` 通过。
11. 本轮新增 MQ/outbox 基座、RocketMQ producer/consumer 容器、通用 outbox dispatcher 和 counter 自动 listener 后，`cd backend && mvn -q -DskipTests compile` 通过。
12. 本轮新增 feed 写模型、收件箱快照、Redis 读模型、fanout 任务、重建任务、feed outbox 和 feed MQ listener 后，`cd backend && mvn -q -DskipTests compile` 通过。
13. 本地依赖启动后，已执行 `V008__feed.sql` 并启动 member/content/social，验证：
    - `feed_note_index`、`feed_fanout_task`、`feed_fanout_sub_task`、`feed_inbox_item`、`feed_outbox_event`、`feed_consume_record` 均有真实写入。
    - `GET /api/feed/following?size=5` 返回真实收件箱卡片，`sourceType=FOLLOW_BACKFILL`，`degraded=false`。
    - `POST /internal/feed/users/{userId}/rebuild` 返回 `SUCCESS`。
    - `GET /internal/feed/fanout-tasks/{taskId}` 返回 fanout/subTask `SUCCESS`。
    - `feed_outbox_event` 中 `FeedDelivered`、`FeedRebuilt` 已由通用 dispatcher 标记 `SENT`。
14. 本轮新增 notification 通知读模型、未读数、聚合、outbox 和 notification MQ listener 后，`cd backend && mvn -q -DskipTests compile` 通过。
15. 本地依赖启动后，已执行 `V009__notification.sql` 并启动 social，验证：
    - 自动消费历史 `interaction-event`、`comment-event`、`relation-event`、`note-event`，写入 `notification_record`、`notification_consume_record`、`notification_outbox_event`。
    - `GET /api/notifications/unread-count` 返回真实总未读和分类未读。
    - `GET /api/notifications?size=5` 返回真实通知列表，包含点赞/收藏聚合、评论明细和关注通知。
    - `POST /internal/notifications/system` 可创建系统通知，重复 `requestId` 不重复新增通知。
    - `GET /api/notifications/{notificationId}`、`POST /api/notifications/{notificationId}/read`、`DELETE /api/notifications`、`POST /internal/notifications/users/{userId}/rebuild-unread` 均通过。
    - `notification_outbox_event` 中 `NotificationCreated`、`NotificationRead`、`NotificationDeleted`、`PushSendRequested` 已由通用 dispatcher 标记 `SENT`。
16. 本轮新增移动端消息页和未读角标后，移动端 `npm run typecheck`、`npm run build:h5` 通过。
17. 本轮已启动 H5 预览并通过内置浏览器检查通知页未登录态和首页消息入口渲染，未发现明显重叠或空白。
18. 本轮新增我的收藏/赞过后端接口、移动端我的页真实 tab 和 `V010__note_interaction_lists.sql` 后，`cd backend && mvn -q -DskipTests compile`、`cd mobile && npm run typecheck` 通过。
19. 本轮新增 content 笔记详情和列表读计数 counter 优先查询后，`cd backend && mvn -q -DskipTests compile` 通过。
20. 本轮新增 push 设备注册、偏好、投递请求、投递尝试、点击日志、`push-request-event` 消费和 `push_outbox_event` 后，`cd backend && mvn -q -DskipTests compile` 通过。
21. 本轮新增 WebSocket 实时投递、在线设备 Redis 路由、ACK 落库、移动端自动设备注册和 realtime 连接后，`cd backend && mvn -q -DskipTests compile`、`cd mobile && npm run typecheck` 通过。
22. 本轮新增 IM 单聊文字消息、会话列表、消息列表、已读/送达、未读数、IM outbox、IM `PushSendRequested`、移动端私信列表和聊天页后，`cd backend && mvn -q -DskipTests compile`、`cd mobile && npm run typecheck`、`cd mobile && npm run build:h5` 通过。

## 6. 待完成事项

### 6.1 第一条主链路收尾

优先级最高：

1. 补一次完整本地端到端验收：网关 -> member/content -> MySQL/MinIO。
2. 更新 `backend/README.md`，它当前仍有早期“内存占位”的过时描述。
3. 整理 local 启动顺序和常见问题到 `docs/engineering/` 或 `deploy/compose/README.md`。
4. 为 auth/user/file/note 补最小接口测试或集成测试。
5. 补 OpenAPI 与 `docs/contracts/api/` 的字段核对记录。
6. 确认 H5 真机或手机浏览器适配，尤其是安全区、底部 tab、图片上传。

### 6.2 移动端体验继续完善

下一批移动端任务：

1. 编辑主页真实页面和 `PUT /api/users/me/profile` 联调。
2. 首页从“我的笔记”过渡到更真实的推荐/发现数据源。
3. 图片上传失败重试、上传进度、重复发布保护再打磨。
4. 空态、错误态、弱网态和 token 过期后的提示继续统一。

### 6.3 第二条社交链路

下一条主链路：

```text
关注用户 -> 发布笔记事件 -> Feed 投递 -> 关注页拉取 -> 点赞/收藏/评论 -> 计数更新 -> 通知生成
```

待落地：

1. RocketMQ 死信告警和更完整的人工重放审计。
2. feed 大 V 推拉结合策略、清理任务后台化、失败任务批量补偿和完整运维审计。
3. push 真实 uni-push / 厂商 Push 离线投递通道、通道凭证配置和失败回执处理。
4. IM 群聊、富媒体消息、撤回/编辑、黑名单、多端增量同步和 push 回执消费。

### 6.4 后续链路

第三条实时链路当前已完成 push 设备、偏好、投递请求、WebSocket 在线下行和 ACK，以及 IM 单聊文字消息最小纵切面；真实 uni-push 离线通道和 IM 高级能力仍待落地：

```text
设备注册 -> WebSocket 连接 -> IM 发送消息 -> 消息入库 -> 在线下发 -> 离线 Push 请求
```

第四条交易链路待完成：

```text
活动预热 -> 秒杀令牌 -> Redis Lua 预扣库存 -> RocketMQ 异步下单 -> 订单查询 -> 支付/发券 -> 超时关单
```

第四条交易链路目前只在方案方向上有设计基础，工程代码还没有开始落地。

## 7. 已知风险与注意事项

1. Outbox 已接通 RocketMQ dispatcher、失败重试和基础运维入口，但还缺死信告警和完整人工重放审计。
2. counter 消费核心、快照、Redis、outbox 和 RocketMQ listener 已落地。
3. 笔记发布、关注事件已可驱动 feed 写模型；点赞、收藏、评论、关注和笔记状态事件已可驱动 notification 读模型；notification/im 的 `PushSendRequested` 已可驱动 push WebSocket 在线投递和投递日志。
4. 自动化测试基本还没建立，后续改动风险会越来越高。
5. social-app 当前已完成 relation、counter、feed、notification、push 和 im 最小纵切面。
6. MySQL 初始化依赖 Docker 首次建卷行为，重建本地环境时要注意数据卷状态。
7. 当前工作区存在未归属本轮任务的改动或临时目录时，不要误提交。
8. 正式部署、备份恢复、监控告警、Nginx/Caddy HTTPS 还没有落地。

## 8. 当前工作区提示

创建本文前，工作区仍存在以下非本文任务内容：

```text
?? .m2/
?? out/
```

这些内容是本地运行/验证产物，不属于当前功能提交。后续提交时应继续避免误带入，除非确认它们确实需要进入版本库。

## 9. 更新规则

建议在以下场景更新本文：

1. 一个主链路阶段完成。
2. 新增或移除后端应用、移动端页面、部署组件。
3. 完成一次端到端联调。
4. 发现重要风险或临时约束。
5. 进入第二条、第三条、第四条主链路前。

建议不要在本文记录过细的日常流水账。已经稳定下来的规则放 `AGENTS.md` 或 `docs/engineering/` 规范文档；接口、DDL、Redis、MQ、安全权限变化放 `docs/contracts/`。
