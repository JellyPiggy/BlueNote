# 第二条主链路数据库契约

版本：v0.2
状态：第二条主链路开发基线

本文定义关系、评论、计数、Feed、通知、Push 逻辑服务第一阶段必须落地的 schema、核心表、唯一约束和索引。本文不是最终可执行 SQL，后续 `backend/sql/` 的 DDL 必须以本文为基线生成。

## 1. 通用表规则

继承 `01-main-chain-schema.md` 的通用规则，并补充：

1. 所有事件消费幂等表必须用 `consumer_group + event_id` 唯一约束。
2. 所有 outbox 表必须包含 `event_id`、`event_type`、`payload`、`send_status`、`retry_count`、`next_retry_at`。
3. 所有游标分页表必须有稳定排序索引，避免 offset 深分页。
4. Redis、MQ、派生表都不是唯一事实来源，必须可从 MySQL 事实表重建。
5. 禁止跨 schema 外键和跨 schema join。

## 2. Schema 清单

| Schema | 归属服务 | 用途 |
|---|---|---|
| `bluenote_relation` | `bluenote-relation` | 关注关系、粉丝派生、关系事件 |
| `bluenote_comment` | `bluenote-comment` | 评论、回复、评论点赞、评论事件 |
| `bluenote_counter` | `bluenote-counter` | 计数快照、delta、重建任务 |
| `bluenote_feed` | `bluenote-feed` | Feed 轻量索引、收件箱快照、fanout 任务 |
| `bluenote_notification` | `bluenote-notification` | 站内通知、聚合、未读数、通知事件 |
| `bluenote_push` | `bluenote-push` | 推送设备、偏好、投递请求、通道尝试 |

说明：笔记点赞和收藏事实表仍在 `bluenote_note.note_like`、`bluenote_note.note_collection`，见 `01-main-chain-schema.md`。

## 3. bluenote_relation

### 3.1 relation_following

关注事实主表，以关注者方向查询为主。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `follower_id` | BIGINT | NOT NULL | 关注人 |
| `followee_id` | BIGINT | NOT NULL | 被关注人 |
| `relation_status` | VARCHAR(32) | NOT NULL | `ACTIVE` / `CANCELED` |
| `followed_at` | DATETIME(3) | NULL | 最近关注时间 |
| `canceled_at` | DATETIME(3) | NULL | 最近取消时间 |
| `relation_version` | BIGINT | NOT NULL | 状态版本 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(id)` | 主键 |
| `uk_following_pair(follower_id, followee_id)` | 一对用户只有一条关系 |
| `idx_following_list(follower_id, relation_status, followed_at, followee_id)` | 查询关注列表 |
| `idx_following_updated(updated_at)` | 补偿扫描 |

### 3.2 relation_follower

粉丝方向派生表，以被关注者方向查询为主。

关键字段与 `relation_following` 一致，字段方向为 `followee_id`、`follower_id`。

索引：

| 索引 | 说明 |
|---|---|
| `uk_follower_pair(followee_id, follower_id)` | 粉丝关系唯一 |
| `idx_follower_list(followee_id, relation_status, followed_at, follower_id)` | 查询粉丝列表 |
| `idx_follower_updated(updated_at)` | 补偿扫描 |

### 3.3 relation_change_log

每次关注状态真实变化写一条流水，用于事件派生、审计和补偿。

| 字段 | 类型 | 说明 |
|---|---|---|
| `change_id` | BIGINT | 变更 ID |
| `follower_id` | BIGINT | 关注人 |
| `followee_id` | BIGINT | 被关注人 |
| `action_type` | VARCHAR(32) | `FOLLOW` / `UNFOLLOW` |
| `before_status` | VARCHAR(32) | 变化前状态 |
| `after_status` | VARCHAR(32) | 变化后状态 |
| `relation_version` | BIGINT | 关系版本 |
| `trace_id` | VARCHAR(64) | 链路 ID |
| `created_at` | DATETIME(3) | 创建时间 |

索引：`idx_relation_change_pair(follower_id, followee_id, created_at)`、`idx_relation_change_time(created_at)`。

### 3.4 relation_outbox_event / relation_consume_record

必须支持：

1. `UserFollowed`
2. `UserUnfollowed`

索引：

| 表 | 索引 |
|---|---|
| `relation_outbox_event` | `uk_relation_outbox_event(event_id)` |
| `relation_outbox_event` | `idx_relation_outbox_status_retry(send_status, next_retry_at)` |
| `relation_consume_record` | `uk_relation_consumer_event(consumer_group, event_id)` |

## 4. bluenote_comment

### 4.1 content_comment

评论元信息主表，服务笔记评论区和回复列表查询。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `comment_id` | BIGINT | NOT NULL | 评论 ID |
| `note_id` | BIGINT | NOT NULL | 笔记 ID |
| `note_author_id` | BIGINT | NOT NULL | 笔记作者 ID |
| `user_id` | BIGINT | NOT NULL | 评论用户 |
| `root_id` | BIGINT | NOT NULL | 一级评论 ID，一级评论为自身 |
| `parent_comment_id` | BIGINT | NOT NULL DEFAULT 0 | 父评论 ID |
| `reply_to_user_id` | BIGINT | NULL | 被回复用户 |
| `level` | TINYINT | NOT NULL | 1 / 2 |
| `comment_status` | VARCHAR(32) | NOT NULL | `VISIBLE` / `DELETED` / `HIDDEN` |
| `like_count_snapshot` | BIGINT | NOT NULL DEFAULT 0 | 降级展示快照 |
| `reply_count_snapshot` | BIGINT | NOT NULL DEFAULT 0 | 降级展示快照 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `uk_content_comment_id(comment_id)` | 评论 ID 唯一 |
| `idx_note_level_time(note_id, level, comment_status, created_at, comment_id)` | 查询一级评论 |
| `idx_root_level_time(root_id, level, comment_status, created_at, comment_id)` | 查询回复 |
| `idx_reply_comment(parent_comment_id)` | 排查回复关系 |
| `idx_note_status(note_id, comment_status, updated_at)` | 管理和补偿扫描 |

### 4.2 user_comment

用户评论历史派生表，第一阶段与 `content_comment` 同事务写入。

核心字段：`comment_id`、`user_id`、`note_id`、`root_id`、`parent_comment_id`、`comment_status`、`content_preview`、`note_title_snapshot`、`note_cover_url_snapshot`、`created_at`。

索引：`uk_user_comment_id(comment_id)`、`idx_user_time(user_id, created_at, comment_id)`。

### 4.3 comment_content

评论正文表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `comment_id` | BIGINT | PK | 评论 ID |
| `content` | VARCHAR(1000) | NOT NULL | 评论正文 |
| `content_preview` | VARCHAR(128) | NOT NULL | 摘要 |
| `audit_status` | VARCHAR(32) | NOT NULL | `SKIPPED` / `PENDING` / `PASSED` / `REJECTED` |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

### 4.4 comment_like

评论点赞明细表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `comment_id` | BIGINT | 评论 ID |
| `comment_user_id` | BIGINT | 评论作者 ID |
| `user_id` | BIGINT | 点赞用户 |
| `like_status` | VARCHAR(32) | `ACTIVE` / `CANCELED` |
| `liked_at` | DATETIME(3) | 最近点赞时间 |
| `canceled_at` | DATETIME(3) | 最近取消时间 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

索引：`uk_comment_like_user(comment_id, user_id)`、`idx_like_user_time(user_id, liked_at)`。

### 4.5 comment_operation_log / comment_idempotent_request

`comment_operation_log` 记录创建、删除、隐藏、点赞等操作审计。

`comment_idempotent_request` 支持评论创建和回复创建幂等，主键为 `idempotent_key`，保存 `request_hash`、`result_comment_id` 和响应快照。

### 4.6 comment_outbox_event / comment_consume_record

必须支持：

1. `CommentCreated`
2. `CommentDeleted`
3. `CommentLiked`
4. `CommentUnliked`
5. `CommentStatusChanged`

索引：

| 表 | 索引 |
|---|---|
| `comment_outbox_event` | `uk_comment_outbox_event(event_id)` |
| `comment_outbox_event` | `idx_comment_outbox_status_retry(send_status, next_retry_at)` |
| `comment_consume_record` | `uk_comment_consumer_event(consumer_group, event_id)` |

## 5. bluenote_counter

### 5.1 counter_snapshot

计数 MySQL 快照表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `target_type` | VARCHAR(32) | NOT NULL | `NOTE` / `USER` / `COMMENT` |
| `target_id` | BIGINT | NOT NULL | 目标 ID |
| `counter_field` | VARCHAR(64) | NOT NULL | 计数字段 |
| `counter_value` | BIGINT | NOT NULL DEFAULT 0 | 当前值 |
| `snapshot_version` | BIGINT | NOT NULL | 快照版本 |
| `flushed_at` | DATETIME(3) | NOT NULL | 刷盘时间 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

索引：`uk_counter_target_field(target_type, target_id, counter_field)`、`idx_counter_updated(updated_at)`。

### 5.2 counter_delta_log

标准计数 delta 流水。

| 字段 | 类型 | 说明 |
|---|---|---|
| `delta_id` | VARCHAR(128) | delta 唯一 ID |
| `source_event_id` | VARCHAR(64) | 来源事件 ID |
| `source_event_type` | VARCHAR(64) | 来源事件类型 |
| `target_type` | VARCHAR(32) | 计数对象 |
| `target_id` | BIGINT | 目标 ID |
| `counter_field` | VARCHAR(64) | 计数字段 |
| `delta_value` | BIGINT | 增量，可为负 |
| `apply_status` | VARCHAR(32) | `PENDING` / `APPLIED` / `FAILED` |
| `occurred_at` | DATETIME(3) | 事件发生时间 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

索引：`PRIMARY KEY(delta_id)`、`idx_counter_delta_status(apply_status, updated_at)`、`idx_counter_delta_target(target_type, target_id, counter_field)`。

### 5.3 counter_rebuild_task

计数校准和重建任务。

核心字段：`task_id`、`task_type`、`target_type`、`target_id`、`fields_json`、`task_status`、`progress_json`、`last_error`、`created_at`、`updated_at`。

索引：`PRIMARY KEY(task_id)`、`idx_counter_rebuild_status(task_status, updated_at)`。

### 5.4 counter_outbox_event / counter_consume_record

必须支持：

1. 内部 `CounterDeltaCreated`
2. 对外 `CounterChanged`
3. 对外 `CounterRebuilt`

索引同通用 outbox 和消费记录要求。

## 6. bluenote_feed

### 6.1 feed_note_index

Feed 服务的笔记轻量索引。

| 字段 | 类型 | 说明 |
|---|---|---|
| `note_id` | BIGINT | 笔记 ID |
| `author_id` | BIGINT | 作者 ID |
| `title_snapshot` | VARCHAR(128) | 标题快照 |
| `cover_url_snapshot` | VARCHAR(512) | 封面快照 |
| `visibility` | VARCHAR(32) | 可见性 |
| `note_status` | VARCHAR(32) | 笔记状态 |
| `item_status` | VARCHAR(32) | `VISIBLE` / `HIDDEN` / `DELETED` |
| `published_at` | DATETIME(3) | 发布时间 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

索引：`uk_feed_note(note_id)`、`idx_feed_author_time(author_id, published_at, note_id)`、`idx_feed_status_time(item_status, published_at)`。

### 6.2 feed_inbox_item

用户收件箱 MySQL 快照。

字段：`id`、`user_id`、`note_id`、`author_id`、`published_at`、`source_type`、`item_status`、`delivered_at`、`created_at`、`updated_at`。

索引：`uk_feed_inbox_user_note(user_id, note_id)`、`idx_feed_inbox_user_time(user_id, published_at, note_id)`、`idx_feed_inbox_user_author(user_id, author_id, published_at)`。

### 6.3 feed_author_strategy

作者分发策略表。

字段：`author_id`、`strategy_type`、`follower_count_snapshot`、`evaluated_at`、`created_at`、`updated_at`。

索引：`PRIMARY KEY(author_id)`、`idx_feed_author_strategy(strategy_type, evaluated_at)`。

### 6.4 feed_fanout_task / feed_fanout_sub_task

`feed_fanout_task` 记录一篇笔记的一次扩散主任务。

`feed_fanout_sub_task` 记录一批粉丝的扩散子任务，字段包含 `sub_task_id`、`task_id`、`target_user_ids_json`、`progress_user_id`、`sub_task_status`、`message_status`、`retry_count`、`next_retry_at`。

索引：

| 表 | 索引 |
|---|---|
| `feed_fanout_task` | `PRIMARY KEY(task_id)` |
| `feed_fanout_task` | `idx_feed_fanout_status(task_status, updated_at)` |
| `feed_fanout_sub_task` | `PRIMARY KEY(sub_task_id)` |
| `feed_fanout_sub_task` | `idx_feed_sub_task_status(task_id, sub_task_status)` |
| `feed_fanout_sub_task` | `idx_feed_sub_message_status(message_status, next_retry_at)` |

### 6.5 feed_rebuild_task / feed_cleanup_task / feed_consume_record / feed_outbox_event

重建、清理和消费幂等表。

`feed_rebuild_task` 字段：`task_id`、`user_id`、`reason`、`task_status`、`progress_json`、`last_error`、`created_at`、`updated_at`。

`feed_cleanup_task` 字段：`task_id`、`cleanup_type`、`user_id`、`author_id`、`note_id`、`task_status`、`progress_json`、`last_error`、`created_at`、`updated_at`。

`feed_consume_record` 字段：`id`、`consumer_group`、`event_id`、`topic`、`event_type`、`biz_key`、`consume_status`、`retry_count`、`error_message`、`consumed_at`、`created_at`、`updated_at`。

`feed_outbox_event` 必须支持：

1. `FeedDelivered`
2. `FeedRebuilt`

索引：`idx_status(task_status, updated_at)`、`uk_feed_consumer_event(consumer_group, event_id)`、`idx_feed_outbox_status_retry(send_status, next_retry_at)`。

## 7. bluenote_notification

### 7.1 notification_record

通知主体表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `notification_id` | BIGINT | 通知 ID |
| `receiver_id` | BIGINT | 接收用户 |
| `actor_id` | BIGINT NULL | 最近触发用户 |
| `category` | VARCHAR(32) | 通知分类 |
| `notification_type` | VARCHAR(64) | 通知类型 |
| `target_type` | VARCHAR(32) | 目标类型 |
| `target_id` | VARCHAR(64) | 目标 ID |
| `source_type` | VARCHAR(32) | 来源类型 |
| `source_id` | VARCHAR(64) | 来源业务 ID |
| `aggregate` | TINYINT | 是否聚合 |
| `aggregate_key` | VARCHAR(128) NULL | 聚合 Key |
| `aggregate_unread_key` | VARCHAR(128) NULL | 未读聚合唯一键，已读后置空 |
| `actor_count` | INT | 触发人数 |
| `title` | VARCHAR(128) | 标题 |
| `content` | VARCHAR(512) | 摘要 |
| `snapshot_json` | JSON | 展示快照 |
| `jump_json` | JSON | 跳转参数 |
| `read_status` | TINYINT | 0 未读，1 已读 |
| `visible_status` | TINYINT | 1 正常，2 删除，3 归档 |
| `last_event_at` | DATETIME(3) | 最近触发时间 |
| `read_at` | DATETIME(3) NULL | 已读时间 |
| `expire_at` | DATETIME(3) NULL | 过期时间 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(notification_id)` | 主键 |
| `idx_notify_receiver_category_time(receiver_id, category, visible_status, last_event_at, notification_id)` | 通知列表 |
| `uk_notify_aggregate_unread(receiver_id, aggregate_unread_key)` | 未读聚合通知唯一，`aggregate_unread_key` 为空时不冲突 |
| `idx_notify_receiver_source(receiver_id, source_type, source_id, notification_type)` | 系统通知和业务来源幂等查询 |
| `idx_notify_expire(expire_at, visible_status)` | 归档清理 |

### 7.2 notification_aggregate_actor

聚合通知最近触发人表。

字段：`id`、`notification_id`、`actor_id`、`source_biz_id`、`acted_at`、`created_at`。

索引：`uk_notify_actor(notification_id, actor_id, source_biz_id)`、`idx_notify_actor_time(notification_id, acted_at)`。

### 7.3 notification_unread_counter

通知未读数事实表。

字段：`user_id`、`category`、`unread_count`、`updated_at`。

索引：`uk_notify_unread_user_category(user_id, category)`。

### 7.4 notification_outbox_event / notification_consume_record

必须支持：

1. `NotificationCreated`
2. `NotificationAggregated`
3. `NotificationRead`
4. `NotificationReadBatch`
5. `NotificationDeleted`
6. `PushSendRequested`

`notification_consume_record` 除通用消费幂等字段外，允许保存 `envelope_json` 原始事件快照，用于内部事件重放和排障。

索引同通用 outbox 和消费记录要求。

## 8. bluenote_push

Push 第一阶段物理部署在 `bluenote-social-app`，但使用独立 schema 和包结构。

### 8.1 push_device

用户设备绑定事实表。

字段：`device_id`、`user_id`、`platform`、`push_provider`、`provider_client_id`、`app_version`、`os_version`、`device_model`、`device_status`、`registered_at`、`last_active_at`、`unbound_at`、`created_at`、`updated_at`。

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(device_id)` | 设备唯一 |
| `idx_push_device_user_status(user_id, device_status, last_active_at)` | 查询用户活跃设备 |
| `idx_push_device_provider(push_provider, provider_client_id)` | 排查通道 token |

### 8.2 push_preference

用户推送偏好表。

字段：`user_id`、`global_enabled`、`interaction_enabled`、`follow_enabled`、`system_enabled`、`order_enabled`、`im_enabled`、`show_im_detail`、`quiet_hours_enabled`、`quiet_start`、`quiet_end`、`created_at`、`updated_at`。

索引：`PRIMARY KEY(user_id)`。

### 8.3 push_delivery_request

投递请求事实表。来源可以是 MQ `PushSendRequested` 或内部接口。

字段：`request_id`、`source_service`、`source_biz_type`、`source_biz_id`、`scene`、`target_user_id`、`target_device_policy`、`delivery_strategy`、`priority`、`title`、`body`、`data_json`、`request_status`、`filtered_reason`、`delivered_device_count`、`expire_at`、`completed_at`、`created_at`、`updated_at`。

索引：

| 索引 | 说明 |
|---|---|
| `PRIMARY KEY(request_id)` | 请求幂等 |
| `uk_push_source_biz(source_service, source_biz_type, source_biz_id, scene)` | 来源业务幂等 |
| `idx_push_request_user_time(target_user_id, created_at)` | 用户维度排查 |
| `idx_push_request_status(request_status, updated_at)` | 运维扫描 |

### 8.4 push_delivery_attempt

通道尝试日志表。

字段：`attempt_id`、`request_id`、`target_user_id`、`device_id`、`channel`、`attempt_status`、`skip_reason`、`provider_message_id`、`error_message`、`attempted_at`、`created_at`、`updated_at`。

索引：`idx_push_attempt_request(request_id, attempted_at)`、`idx_push_attempt_device(device_id, attempted_at)`、`idx_push_attempt_status(attempt_status, attempted_at)`。

### 8.5 push_click_log

移动端点击系统通知回传日志。

字段：`id`、`request_id`、`user_id`、`device_id`、`data_json`、`clicked_at`、`created_at`。

索引：`idx_push_click_request(request_id, clicked_at)`、`idx_push_click_user(user_id, clicked_at)`。

### 8.6 push_consume_record / push_outbox_event

`push_consume_record` 用于 MQ 消费幂等和事件重放，允许保存 `envelope_json`。

`push_outbox_event` 支持：

1. `PushDelivered`
2. `PushFiltered`
3. `PushFailed`

索引同通用 outbox 和消费记录要求。

## 9. DDL 生成要求

后续生成 `backend/sql/` 时：

1. 每个 schema 独立创建，按服务边界拆分版本文件。
2. 写接口幂等依赖的唯一约束必须在第一版 DDL 中落地。
3. 所有事件消费表必须保留失败原因和重试次数，便于补偿。
4. 对 cursor 分页的列表必须包含时间和 ID 的复合索引。
5. JSON 字段不能作为高频查询条件。
