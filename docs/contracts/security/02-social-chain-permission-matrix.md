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
| POST | `/api/push/devices/register` | 是 | 当前用户设备 | 业务唯一键 | 用户 / 设备 | 注册或刷新推送设备 |
| GET | `/api/push/devices` | 是 | 当前用户 | 否 | 用户 | 查询我的推送设备 |
| DELETE | `/api/push/devices/{deviceId}` | 是 | 当前用户设备 | 是 | 用户 / 设备 | 解绑设备 |
| GET | `/api/push/preferences` | 是 | 当前用户 | 否 | 用户 | 查询推送偏好 |
| PUT | `/api/push/preferences` | 是 | 当前用户 | 是 | 用户 | 更新推送偏好 |
| POST | `/api/push/clicks` | 是 | 当前用户设备 | 是 | 用户 / 设备 | Push 点击回传 |
| POST | `/api/im/conversations/single` | 是 | 当前用户 | 业务唯一键 | 用户 / 目标用户 | 创建或获取单聊会话 |
| GET | `/api/im/conversations` | 是 | 当前用户 | 否 | 用户 | 查询我的会话列表 |
| POST | `/api/im/messages` | 是 | 当前用户参与会话 | `senderId + clientMsgId` | 用户 / 会话 | 发送 IM 消息 |
| GET | `/api/im/conversations/{conversationId}/messages` | 是 | 会话成员 | 否 | 用户 / 会话 | 查询会话消息 |
| POST | `/api/im/conversations/{conversationId}/received` | 是 | 会话成员 | 是 | 用户 / 会话 | 上报送达 |
| POST | `/api/im/conversations/{conversationId}/read` | 是 | 会话成员 | 是 | 用户 / 会话 | 标记会话已读 |
| GET | `/api/im/unread-count` | 是 | 当前用户 | 否 | 用户 | 查询 IM 总未读 |
| PUT | `/api/im/conversations/{conversationId}/settings` | 是 | 会话成员 | 是 | 用户 / 会话 | 更新置顶/免打扰 |
| DELETE | `/api/im/conversations/{conversationId}` | 是 | 会话成员 | 是 | 用户 / 会话 | 当前用户侧隐藏会话 |

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
| POST | `/internal/push/requests/send` | notification / im / order / ops | push | 服务身份认证，按 requestId 幂等 |
| GET | `/internal/push/requests/{requestId}` | notification / im / order / ops | push | 服务身份认证 |
| POST | `/internal/push/requests/{requestId}/retry` | ops / admin | push | 内部管理权限，审计 |
| POST | `/internal/push/events/replay` | ops / admin | push | 内部管理权限，审计 |
| POST | `/internal/im/conversations/batch-summary` | push / notification / admin | im | 服务身份认证，单次最多 100 |
| GET | `/internal/im/conversations/{conversationId}/members/{userId}/push-policy` | push / ops | im | 服务身份认证 |
| POST | `/internal/im/users/{userId}/rebuild-unread` | ops / admin | im | 内部管理权限，频率限制 |
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
| 注册设备 | `deviceId` 最终绑定状态 |
| 更新推送偏好 | `userId` 最终偏好状态 |
| Push 点击回传 | `requestId + userId + deviceId + clickedAt` 日志幂等后续增强 |
| Push 投递请求 | `requestId`，并辅以 `sourceService + sourceBizType + sourceBizId + scene` |
| 创建单聊会话 | `singleKey=minUserId:maxUserId` |
| 发送 IM 消息 | `senderId + clientMsgId` |
| IM 已读 / 送达 | `conversationId + userId` 最终序号状态 |
| 删除 IM 会话 | `conversationId + userId` 最终隐藏状态 |

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
| `deviceId` | 非空，长度不超过 128 |
| `platform` | `IOS` / `ANDROID` / `H5` |
| `pushProvider` | `UNI_PUSH` / `APNS` / `FCM` / `VENDOR_PUSH` / `NOOP` |
| 推送标题 | 不超过 128 字符 |
| 推送正文 | 不超过 512 字符 |
| WebSocket 握手 | 必须同时提供有效 access token 和已绑定 ACTIVE 的 `deviceId` |
| `targetUserId` | IM 单聊对象必须存在，不能是当前用户 |
| `clientMsgId` | 非空，长度不超过 128 |
| IM 文字消息 | 1 到 1000 字符 |
| IM 分页 | 会话 `pageSize` 最大 50，消息 `limit` 最大 100 |

## 5. 可见性和归属规则

1. 评论查询必须校验笔记仍可见。
2. 评论删除必须校验评论作者是当前用户，管理端隐藏走内部接口。
3. Feed 查询只允许当前用户查询自己的关注页。
4. Feed 读取必须二次过滤已取关作者和不可见笔记。
5. 通知查询、已读和删除必须校验 `receiverId == currentUserId`。
6. 关注和评论写操作必须校验当前用户状态为 `NORMAL`。
7. 设备解绑、点击回传必须校验设备属于当前用户或已经是当前用户最近绑定设备。
8. Push WebSocket 握手必须校验 token 中的 `userId`、`deviceId` 与设备事实表一致。
9. Push 内部接口不得让移动端指定操作其他用户设备。
10. IM 会话列表、消息查询、已读、送达、设置和删除必须校验当前用户是会话成员。
11. IM 发送时服务端以当前登录用户作为 `senderId`，不信任移动端传发送人。

## 6. 限流建议

| 操作 | 限流维度 |
|---|---|
| 关注 / 取关 | 用户、IP、目标用户 |
| 评论发布 | 用户、笔记、IP |
| 评论点赞 | 用户、评论 |
| Feed 查询 | 用户、IP |
| 通知列表查询 | 用户 |
| 通知批量已读 / 删除 | 用户 |
| 设备注册 / 解绑 | 用户、设备 |
| Push 点击回传 | 用户、设备 |
| Push 投递请求 | 目标用户、来源服务 |
| IM 发送消息 | 用户、会话、目标用户 |
| IM 会话和消息查询 | 用户 |
| 计数重建 | target、操作者 |
| Feed 重建 | userId、操作者 |

限流失败统一返回 `10005 RATE_LIMITED` 或对应业务细分错误码。

## 7. 日志和审计

必须记录：

1. 关注、取关、评论、删除评论、评论点赞的操作日志。
2. 通知已读、删除、系统通知创建的审计日志。
3. 推送设备注册、解绑、偏好更新、投递请求和通道尝试日志。
4. 内部管理接口调用人、来源服务、请求参数摘要和 `traceId`。
5. 权限失败和资源归属失败，不记录 Access Token、完整评论正文、provider token 或敏感 payload。
