# 第二条主链路 Redis Key 契约

版本：v0.2
状态：第二条主链路开发基线

Redis 只作为缓存、短锁、限流、在线计数和 Feed 在线读模型。所有 Key 丢失后必须能从 MySQL、对象存储、关系服务或笔记服务重建。

命名继承：

```text
bluenote:{env}:{service}:{biz}:{id}
```

## 1. relation keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:relation:following:{userId}` | ZSET | 30 分钟 + 抖动 | relation | relation / feed | 关注列表缓存，score 为 followedAtMillis，member 为 followeeId |
| `bluenote:{env}:relation:follower:{userId}` | ZSET | 30 分钟 + 抖动 | relation | relation / feed | 粉丝列表缓存，score 为 followedAtMillis，member 为 followerId |
| `bluenote:{env}:relation:status:{followerId}:{followeeId}` | String | 10 分钟 | relation | relation / user | 单个关注状态 |
| `bluenote:{env}:relation:batch-status:{followerId}` | Hash | 5 分钟 | relation | relation | 当前用户批量关注状态短缓存 |
| `bluenote:{env}:relation:rate:follow:{userId}` | String | 10 秒 | relation / gateway | relation | 关注操作限流 |
| `bluenote:{env}:relation:idempotent:{userId}:{targetUserId}` | String | 24 小时 | relation | relation | 关注/取关短期幂等缓存 |

重建方式：

1. `following` 从 `relation_following` 按 `follower_id` 重建。
2. `follower` 从 `relation_follower` 或 `relation_following` 按 `followee_id` 重建。
3. 状态缓存未命中时查询 `relation_following`。

## 2. comment keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:comment:hot:{noteId}` | ZSET | 10 分钟 | comment | comment | 热门一级评论，score 为热度分，member 为 commentId |
| `bluenote:{env}:comment:time:{noteId}:level1` | ZSET | 10 分钟 | comment | comment | 一级评论时间序列表 |
| `bluenote:{env}:comment:replies:{rootCommentId}` | ZSET | 10 分钟 | comment | comment | 二级回复列表 |
| `bluenote:{env}:comment:meta:{commentId}` | String(JSON) | 10 分钟 | comment | comment / notification | 评论元信息和摘要缓存 |
| `bluenote:{env}:comment:liked:{userId}:{commentId}` | String | 10 分钟 | comment | comment | 当前用户是否点赞评论 |
| `bluenote:{env}:comment:liked:batch:{userId}` | Hash | 5 分钟 | comment | comment | 批量点赞状态短缓存 |
| `bluenote:{env}:comment:rate:create:{userId}` | String | 10 秒 | comment / gateway | comment | 评论发布限流 |
| `bluenote:{env}:comment:rate:like:{userId}` | String | 10 秒 | comment / gateway | comment | 评论点赞限流 |
| `bluenote:{env}:comment:rate:note:{noteId}` | String | 10 秒 | comment | comment | 热点笔记评论限流 |

失效策略：

1. 评论发布后删除或增量更新 `hot`、`time`、`replies`。
2. 评论删除后删除 `meta`，并从列表 ZSET 移除。
3. 评论点赞后删除 `liked` 和批量状态缓存，热门分可异步调整。

## 3. counter keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:counter:{targetType}:{targetId}` | Hash | 不设置短 TTL | counter | counter | 在线计数 Hash |
| `bluenote:{env}:counter:dirty` | ZSET | 不设置 | counter | counter | 待刷盘对象集合 |
| `bluenote:{env}:counter:dedupe:{yyyyMMdd}` | Set | 30 天 | counter | counter | Redis 应用 delta 幂等 |
| `bluenote:{env}:counter:lock:rebuild:{targetType}:{targetId}` | String | 5 分钟 | counter | counter | 重建锁 |
| `bluenote:{env}:counter:rate:rebuild:{targetType}:{targetId}` | String | 10 分钟 | counter | counter | 重建频率限制 |

Hash 字段：

| targetType | 字段 |
|---|---|
| `NOTE` | `like_count`、`collect_count`、`comment_count` |
| `USER` | `following_count`、`follower_count`、`note_count`、`liked_count` |
| `COMMENT` | `like_count`、`reply_count` |

重建方式：

1. 优先从 `counter_snapshot` 回填。
2. 快照缺失或异常时调用来源服务的 `counter-source` 内部接口。

## 4. feed keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:feed:inbox:{userId}` | ZSET | 不设置短 TTL | feed | feed | 用户收件箱，score 为 publishedAtMillis，member 为补零 noteId |
| `bluenote:{env}:feed:author:outbox:{authorId}` | ZSET | 不设置短 TTL | feed | feed | 作者公开笔记发件箱 |
| `bluenote:{env}:feed:active:user` | ZSET | 不设置短 TTL | feed | feed | 用户最近活跃时间 |
| `bluenote:{env}:feed:author:strategy:{authorId}` | String(JSON) | 1 小时 | feed | feed | 作者分发策略 |
| `bluenote:{env}:feed:user:big-authors:{userId}` | ZSET | 10 分钟 | feed | feed | 用户关注的大 V 作者缓存 |
| `bluenote:{env}:feed:idempotent:{consumerGroup}:{eventId}` | String | 7 天 | feed | feed | MQ 消费短期幂等 |
| `bluenote:{env}:feed:lock:rebuild:{userId}` | String | 5 分钟 | feed | feed | 用户 Feed 重建锁 |

裁剪规则：

1. `feed:inbox:{userId}` 保留最新 `inboxLimit` 条，初始建议 2000。
2. `feed:author:outbox:{authorId}` 保留最新 `authorOutboxLimit` 条，初始建议 1000。
3. `feed:active:user` 定期裁剪超过活跃窗口的用户。

重建方式：

1. 收件箱从 `feed_inbox_item`、作者发件箱、关系服务和笔记服务重建。
2. 作者发件箱从 `feed_note_index` 或 `/internal/notes/authors/recent` 重建。

## 5. notification keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:notification:unread:{userId}` | Hash | 30 分钟 | notification | notification / gateway 后续 | 用户总未读和分类未读缓存 |
| `bluenote:{env}:notification:dedupe:event:{eventId}` | String | 24 小时 | notification | notification | 事件短期去重 |
| `bluenote:{env}:notification:lock:aggregate:{hash}` | String | 10 秒 | notification | notification | 聚合通知更新短锁 |
| `bluenote:{env}:notification:template:{notificationType}` | Hash | 30 分钟 | notification | notification | 通知模板缓存 |
| `bluenote:{env}:notification:rate:push:{userId}:{type}:{minute}` | String | 2 分钟 | notification | notification | Push 请求前轻量限流 |
| `bluenote:{env}:notification:rebuild:unread:lock:{userId}` | String | 5 分钟 | notification | notification | 未读数重建锁 |

未读 Hash 字段：

```json
{
  "total": 12,
  "INTERACTION": 8,
  "FOLLOW": 3,
  "SYSTEM": 1,
  "ORDER": 0
}
```

重建方式：

1. 未读数以 `notification_unread_counter` 为准。
2. Redis 未读缓存丢失后回源 MySQL。
3. 聚合短锁失败时可退化为 MySQL 唯一约束冲突重试。

## 6. push keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:push:device:active:{userId}` | ZSET | 30 天 | push | push | 用户活跃设备，score 为 lastActiveAtMillis，member 为 deviceId |
| `bluenote:{env}:push:preference:{userId}` | Hash | 30 分钟 | push | push | 用户推送偏好缓存 |
| `bluenote:{env}:push:online:user:{userId}` | Set | 2 分钟滚动 | push | push | 在线设备集合，WebSocket 连接建立和心跳刷新 |
| `bluenote:{env}:push:online:device:{deviceId}` | Hash | 2 分钟滚动 | push | push | 设备在线连接路由，包含 userId、deviceId、connectionId、nodeId、lastSeenAt |
| `bluenote:{env}:push:idempotent:{consumerGroup}:{eventId}` | String | 7 天 | push | push | MQ 消费短期幂等缓存 |
| `bluenote:{env}:push:rate:user:{userId}:{minute}` | String | 2 分钟 | push | push | 用户维度推送限流 |

重建方式：

1. 活跃设备缓存从 `push_device` 中 `ACTIVE` 设备重建。
2. 偏好缓存从 `push_preference` 重建，缺失时使用默认开启策略。
3. 在线连接 key 丢失视为用户离线，不影响业务事实。

## 7. im keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:im:unread:{userId}` | String | 30 分钟 | im | im / gateway 后续 | 用户 IM 总未读缓存 |
| `bluenote:{env}:im:conversation:list:{userId}` | ZSET | 10 分钟 | im | im | 用户会话列表短缓存，score 为最近消息时间毫秒，member 为 conversationId |
| `bluenote:{env}:im:dedupe:client:{senderId}:{clientMsgId}` | String | 24 小时 | im | im | 发送消息短期幂等缓存，MySQL 唯一约束兜底 |
| `bluenote:{env}:im:rate:send:{userId}` | String | 10 秒 | im / gateway | im | 发送私信限流 |
| `bluenote:{env}:im:lock:conversation:{conversationId}` | String | 5 秒 | im | im | 热点会话序号分配短锁，MySQL 行锁兜底 |

重建方式：

1. `im:unread:{userId}` 从 `im_conversation_member.unread_count` 汇总重建。
2. `im:conversation:list:{userId}` 从 `im_conversation_member` 和 `im_conversation` 按最近消息时间重建。
3. 幂等和限流 Key 丢失不影响事实一致性。

## 8. Key 变更规则

新增或修改 Key 必须同步说明：

1. Key 模板和所属服务。
2. 数据结构、TTL、写入方、读取方。
3. 失效策略和重建方式。
4. 是否影响移动端可见体验。
5. 是否需要更新后端配置、测试或运维脚本。
