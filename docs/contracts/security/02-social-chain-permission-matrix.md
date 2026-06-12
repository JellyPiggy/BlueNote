# 第二条主链路权限矩阵

版本：v0.2
状态：第二条主链路开发基线

本文补充关注、评论、Feed、通知和计数相关接口的权限、归属、幂等和限流要求。网关用户上下文继承 `01-permission-matrix.md`。

## 1. 外部接口权限矩阵

| 方法 | 路径 | 登录 | 资源归属 | 幂等 | 限流 | 说明 |
|---|---|---|---|---|---|---|
| POST | `/api/relations/following/{followeeId}` | 是 | 当前用户 | 业务唯一键 | 用户 / IP | 关注用户 |
| DELETE | `/api/relations/following/{followeeId}` | 是 | 当前用户 | 业务唯一键 | 用户 / IP | 取消关注 |
| GET | `/api/relations/users/{userId}/following` | 可选 | 公开关系列表 | 否 | 用户 / IP | 查询关注列表 |
| GET | `/api/relations/users/{userId}/followers` | 可选 | 公开关系列表 | 否 | 用户 / IP | 查询粉丝列表 |
| GET | `/api/relations/following/{targetUserId}/status` | 是 | 当前用户 | 否 | 用户 | 查询关注状态 |
| POST | `/api/relations/following/status/batch` | 是 | 当前用户 | 否 | 用户 | 批量查询关注状态 |
| POST | `/api/comments/notes/{noteId}` | 是 | 当前用户 | 是 | 用户 / 笔记 | 发布一级评论 |
| POST | `/api/comments/{commentId}/replies` | 是 | 当前用户 | 是 | 用户 / 评论 | 回复评论 |
| DELETE | `/api/comments/{commentId}` | 是 | 评论作者 | 是 | 用户 | 删除评论 |
| GET | `/api/comments/notes/{noteId}` | 可选 | 可见性校验 | 否 | 用户 / IP | 查询笔记评论 |
| GET | `/api/comments/{rootCommentId}/replies` | 可选 | 可见性校验 | 否 | 用户 / IP | 查询回复 |
| POST | `/api/comments/{commentId}/like` | 是 | 当前用户 | 业务唯一键 | 用户 | 评论点赞 |
| DELETE | `/api/comments/{commentId}/like` | 是 | 当前用户 | 业务唯一键 | 用户 | 取消评论点赞 |
| GET | `/api/comments/me` | 是 | 当前用户 | 否 | 用户 | 查询我的评论 |
| GET | `/api/feed/following` | 是 | 当前用户 | 否 | 用户 / IP | 查询关注页 Feed |
| GET | `/api/notifications/unread-count` | 是 | 当前用户 | 否 | 用户 | 查询通知未读数 |
| GET | `/api/notifications` | 是 | 当前用户 | 否 | 用户 | 查询通知列表 |
| GET | `/api/notifications/{notificationId}` | 是 | 通知接收人 | 否 | 用户 | 查询通知详情 |
| POST | `/api/notifications/{notificationId}/read` | 是 | 通知接收人 | 是 | 用户 | 单条已读 |
| POST | `/api/notifications/read-all` | 是 | 当前用户 | 是 | 用户 | 批量已读 |
| DELETE | `/api/notifications/{notificationId}` | 是 | 通知接收人 | 是 | 用户 | 删除通知 |
| DELETE | `/api/notifications` | 是 | 当前用户 | 是 | 用户 | 批量删除通知 |

说明：

1. 移动端不得传入 `userId` 操作别人的关注、评论点赞、通知已读或删除。
2. 用户主页的关注状态优先由 `/api/users/{userId}/home` 聚合返回。
3. 计数服务没有移动端公网接口。

## 2. 内部接口权限矩阵

| 方法 | 路径 | 调用方 | 被调用方 | 要求 |
|---|---|---|---|---|
| POST | `/internal/relations/following/status/batch` | user / note / feed / notification / im | relation | 服务身份认证，单次最多 100 |
| GET | `/internal/relations/users/{userId}/followers/page` | feed | relation | 服务身份认证，分页大小最大 1000 |
| GET | `/internal/relations/users/{userId}/following/page` | feed / notification / im | relation | 服务身份认证，分页大小最大 1000 |
| POST | `/internal/relations/counter-source` | counter | relation | 服务身份认证，字段白名单 |
| POST | `/internal/comments/batch-summary` | notification / admin | comment | 服务身份认证，单次最多 100 |
| POST | `/internal/comments/counter-source` | counter | comment | 服务身份认证，字段白名单 |
| POST | `/internal/counters/batch` | user / note / comment / feed / rank | counter | 服务身份认证，单次最多 100 |
| POST | `/internal/counters/reconcile` | ops / admin | counter | 内部管理权限，频率限制 |
| GET | `/internal/counters/rebuild-tasks/{taskId}` | ops / admin | counter | 内部管理权限 |
| POST | `/internal/counters/warmup` | feed / rank / ops | counter | 服务身份认证，单次最多 500 |
| POST | `/internal/feed/users/{userId}/rebuild` | ops / admin | feed | 内部管理权限，频率限制 |
| GET | `/internal/feed/rebuild-tasks/{taskId}` | ops / admin | feed | 内部管理权限 |
| GET | `/internal/feed/fanout-tasks/{taskId}` | ops / admin | feed | 内部管理权限 |
| POST | `/internal/feed/fanout-tasks/{taskId}/retry` | ops / admin | feed | 内部管理权限，审计 |
| POST | `/internal/notifications/system` | admin / system | notification | 内部管理权限，写审计 |
| POST | `/internal/notifications/batch-summary` | push / admin | notification | 服务身份认证，单次最多 100 |
| POST | `/internal/notifications/users/{userId}/rebuild-unread` | ops / admin | notification | 内部管理权限，频率限制 |
| POST | `/internal/notifications/events/replay` | ops / admin | notification | 内部管理权限，审计 |
| POST | `/internal/notes/authors/recent` | feed | note | 服务身份认证，单次最多 50 作者 |
| POST | `/internal/notes/counter-source` | counter | note | 服务身份认证，字段白名单 |

网关不得暴露 `/internal/**` 到公网。

## 3. 写操作幂等基线

| 场景 | 幂等依据 |
|---|---|
| 关注用户 | `followerId + followeeId` 唯一关系 |
| 取消关注 | `followerId + followeeId` 最终状态 |
| 发布评论 | `Idempotency-Key` 或 `clientRequestId` |
| 回复评论 | `Idempotency-Key` 或 `clientRequestId` |
| 删除评论 | `commentId` 最终状态 |
| 评论点赞 | `userId + commentId` 唯一业务键 |
| 取消评论点赞 | `userId + commentId` 最终状态 |
| 通知单条已读 | `notificationId` 最终状态 |
| 通知批量已读 | `receiverId + category` 当前未读集合 |
| 删除通知 | `receiverId + notificationId` 最终可见状态 |

重复请求已经成功时，应返回当前最终状态，不重复发布真实业务事件。

## 4. 参数校验基线

| 对象 | 规则 |
|---|---|
| `followeeId` | 必须是正常用户，不能是当前用户 |
| `targetUserIds` | 单次最多 100 |
| 评论正文 | 1 到 1000 字符，需内容安全检查 |
| `commentId` | 必须存在且可见 |
| `rootCommentId` | 必须是一级评论 |
| `sort` | `HOT` / `TIME_DESC` / `TIME_ASC` |
| Feed `size` | 1 到 50 |
| Feed `cursor` | `publishedAtMillis_noteId` |
| 通知 `category` | `INTERACTION` / `FOLLOW` / `SYSTEM` / `ORDER` |
| 通知批量删除 | 单次最多 50 条 |

## 5. 可见性和归属规则

1. 评论查询必须校验笔记仍可见。
2. 评论删除必须校验评论作者是当前用户，管理端隐藏走内部接口。
3. Feed 查询只允许当前用户查询自己的关注页。
4. Feed 读取必须二次过滤已取关作者和不可见笔记。
5. 通知查询、已读和删除必须校验 `receiverId == currentUserId`。
6. 关注和评论写操作必须校验当前用户状态为 `NORMAL`。

## 6. 限流建议

| 操作 | 限流维度 |
|---|---|
| 关注 / 取关 | 用户、IP、目标用户 |
| 评论发布 | 用户、笔记、IP |
| 评论点赞 | 用户、评论 |
| Feed 查询 | 用户、IP |
| 通知列表查询 | 用户 |
| 通知批量已读 / 删除 | 用户 |
| 计数重建 | target、操作者 |
| Feed 重建 | userId、操作者 |

限流失败统一返回 `10005 RATE_LIMITED` 或对应业务细分错误码。

## 7. 日志和审计

必须记录：

1. 关注、取关、评论、删除评论、评论点赞的操作日志。
2. 通知已读、删除、系统通知创建的审计日志。
3. 内部管理接口调用人、来源服务、请求参数摘要和 `traceId`。
4. 权限失败和资源归属失败，不记录 Access Token、完整评论正文或敏感信息。
