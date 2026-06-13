# BlueNote 功能模块设计总览

版本：v0.1
状态：后端实习项目模块讲解材料
更新时间：2026-06-13

## 1. 文档目标

本文按模块解释 BlueNote 的具体架构、流程和方案选型。

它适合两种场景：

1. GitHub 读者想快速知道每个模块到底做了什么。
2. 后端实习面试时，需要把“我做了哪些功能”讲成“我如何设计这些功能”。

本文不替代 `方案/services/` 的详细设计，也不替代 `docs/contracts/` 的接口、DDL、Redis 和 MQ 契约。它是一个更容易阅读的总览层。

## 2. 模块总览

| 模块 | 核心问题 | 主要技术点 |
|---|---|---|
| Gateway | 移动端如何统一访问后端 | JWT、路由、用户上下文 Header、WebSocket 转发 |
| Auth | 用户如何安全登录 | BCrypt、Access Token、Refresh Token、设备会话 |
| User | 用户资料如何维护和展示 | 资料版本、头像/封面文件绑定、主页计数聚合 |
| File | 图片如何上传和绑定业务 | MinIO 预签名直传、上传确认、文件绑定 |
| Note | 笔记如何发布和互动 | 笔记事实、媒体绑定、点赞收藏明细、outbox |
| Comment | 评论和回复如何组织 | 评论层级、状态删除、评论点赞、评论计数来源 |
| Relation | 关注关系如何维护 | 关注/粉丝读模型、幂等关注、关系事件 |
| Counter | 计数如何最终一致 | 事件消费、Redis 在线计数、MySQL 快照、回源重建 |
| Feed | 关注页如何高效读取 | 收件箱、fanout、Redis/MySQL 降级、重建 |
| Notification | 站内通知如何生成 | 读模型、聚合通知、未读数、Push 请求 |
| Push | 在线消息如何投递 | 设备、偏好、在线路由、WebSocket、投递日志 |
| IM | 单聊消息如何可靠收发 | 消息入库、会话序列、未读、送达、已读 |
| Order | 秒杀订单如何防超卖 | 秒杀 token、Redis Lua、异步下单、状态机、补偿 |
| Rank | 榜单如何增量更新 | CounterChanged、Redis ZSet、分数事实、快照重建 |

## 3. Gateway

### 3.1 解决的问题

移动端不能直接感知后端内部服务结构。Gateway 负责统一入口、统一鉴权、统一路由，并向下游服务传递可信用户上下文。

### 3.2 当前实现

| 能力 | 状态 |
|---|---|
| JWT Access Token 校验 | 已实现 |
| 公开路径放行 | 已实现 |
| 认证失败统一响应 | 已实现 |
| 下游 Header 注入 | 已实现 |
| member/content/social/order 路由 | 已实现 |
| `/ws/realtime` WebSocket 转发 | 已实现 |

下游 Header：

```text
X-User-Id
X-Device-Id
X-Session-Id
X-Trace-Id
```

### 3.3 方案选型

使用 Spring Cloud Gateway，而不是让每个业务服务自己处理移动端认证。

理由：

1. 认证逻辑集中，减少重复代码。
2. 移动端只需要知道一个网关地址。
3. 下游服务可以相信网关注入的用户上下文。
4. 后续限流、黑名单、灰度和日志都可以在入口层扩展。

### 3.4 面试讲法

```text
我把移动端外部 API 都收敛到 Gateway。Gateway 校验 Access Token 后，把 userId、deviceId、sessionId 和 traceId 注入到下游 Header。业务服务不直接解析 Token，只读取可信上下文，这样认证和业务逻辑解耦。
```

## 4. Auth

### 4.1 解决的问题

Auth 负责用户登录身份，不负责用户展示资料。

核心对象：

1. 账号。
2. 密码哈希。
3. 设备会话。
4. Access Token。
5. Refresh Token。
6. 登录审计。

### 4.2 当前实现

| 能力 | 状态 |
|---|---|
| 注册 | 已实现 |
| 登录 | 已实现 |
| Refresh Token 轮换 | 已实现 |
| 登出 | 已实现 |
| 修改密码 | 已实现 |
| BCrypt 密码哈希 | 已实现 |
| 设备会话和登录审计 | 已实现 |

### 4.3 核心流程

```text
注册
  -> 校验用户名和密码
  -> 写 auth_account / auth_password
  -> 创建设备会话
  -> 创建默认用户资料
  -> 返回 TokenPair
```

```text
登录
  -> 校验账号状态
  -> BCrypt 校验密码
  -> 创建或刷新设备会话
  -> 记录登录审计
  -> 返回 TokenPair
```

### 4.4 方案选型

| 设计 | 原因 |
|---|---|
| BCrypt 哈希 | 避免明文密码，适合 Java/Spring 体系 |
| Access + Refresh Token | 兼顾安全和登录体验 |
| Refresh Token 哈希存储 | 数据库泄露时不直接暴露可用 Token |
| 设备会话表 | 支持多端登录、登出和后续设备管理 |

### 4.5 面试讲法

```text
Auth 和 User 分开，Auth 保存账号、密码哈希和会话，User 保存展示资料。Access Token 由 Gateway 校验，Refresh Token 在服务端保存哈希并支持轮换。这样既能支持移动端长登录，也能在 Token 泄露或登出时控制设备会话。
```

## 5. User

### 5.1 解决的问题

User 负责用户展示资料和主页信息，包括昵称、头像、简介、地区、主页封面和主页头部计数。

### 5.2 当前实现

| 能力 | 状态 |
|---|---|
| 查询当前用户资料 | 已实现 |
| 修改用户资料 | 已实现 |
| 查询公开资料 | 已实现 |
| 查询用户主页头部 | 已实现 |
| 头像/主页封面文件校验绑定 | 已实现 |
| 主页计数接 counter | 已实现 |

### 5.3 核心流程

```text
修改资料
  -> 读取当前用户上下文
  -> 校验 profileVersion
  -> 如有头像/封面 fileId，调用 file 内部接口校验
  -> 保存 URL 快照和 fileId
  -> 绑定 USER_AVATAR / USER_HOME_COVER
  -> 写用户资料审计和 user outbox
```

### 5.4 方案选型

| 设计 | 原因 |
|---|---|
| user 不保存密码 | 展示资料和安全凭证分离 |
| profileVersion | 避免并发编辑覆盖 |
| 保存头像 URL 快照 | 展示时减少跨服务依赖 |
| 主页计数通过 counter 聚合 | user 不直接维护关注数、粉丝数、作品数 |

### 5.5 面试讲法

```text
User 服务只负责资料事实和主页展示聚合，不负责登录凭证，也不直接维护关注数。头像和封面更新时会调用 File 服务校验文件归属、场景和状态，避免用户绑定别人的文件或未上传完成的文件。
```

## 6. File

### 6.1 解决的问题

图片和视频不应该进入 MySQL，也不应该全部经由后端应用转发。File 服务负责上传凭证、元数据、上传确认、访问 URL 和业务绑定。

### 6.2 当前实现

| 能力 | 状态 |
|---|---|
| 上传凭证 | 已实现 |
| 上传确认 | 已实现 |
| 获取访问 URL | 已实现 |
| 内部文件校验 | 已实现 |
| 内部文件绑定 | 已实现 |
| MinIO 预签名上传 | 已实现 |

### 6.3 核心流程

```text
申请上传凭证
  -> 校验 scene、mimeType、size
  -> 生成 fileId 和 objectKey
  -> 写 file_object 和 file_upload_session
  -> 返回 MinIO presigned PUT URL

确认上传
  -> 校验文件归属
  -> 校验上传会话
  -> 标记 UPLOADED
  -> 写 file outbox
```

### 6.4 方案选型

| 设计 | 原因 |
|---|---|
| MinIO 预签名 PUT | 减少后端带宽压力 |
| 上传确认 | 避免引用未上传完成文件 |
| scene 字段 | 区分 NOTE_IMAGE、USER_AVATAR、USER_HOME_COVER |
| file_binding | 支持一个文件被明确绑定到业务对象 |

### 6.5 面试讲法

```text
文件上传采用申请凭证、对象存储直传、上传确认、业务绑定的流程。File 服务保存元数据和上传状态，业务服务只引用通过校验的 fileId，不直接处理二进制文件。
```

## 7. Note

### 7.1 解决的问题

Note 是笔记事实的主写服务，负责草稿、发布、删除、详情、作者列表、媒体绑定、点赞收藏明细和相关事件。

### 7.2 当前实现

| 能力 | 状态 |
|---|---|
| 保存草稿和发布笔记 | 已实现 |
| 草稿发布 | 已实现 |
| 删除笔记 | 已实现 |
| 查询详情和列表 | 已实现 |
| 点赞/取消点赞 | 已实现 |
| 收藏/取消收藏 | 已实现 |
| 我的收藏/赞过 | 已实现 |
| 内部笔记摘要和计数来源 | 已实现 |
| note outbox | 已实现 |

### 7.3 核心流程

```text
发布笔记
  -> 校验用户上下文
  -> 校验媒体 fileId
  -> 写 note / note_version / note_media / note_topic
  -> 绑定文件
  -> 写 NotePublished outbox
```

```text
点赞笔记
  -> 校验笔记可见
  -> 写 note_like 明细
  -> 重复点赞走幂等
  -> 写 NoteLiked outbox
```

### 7.4 方案选型

| 设计 | 原因 |
|---|---|
| note_like / note_collection 明细表 | 支持取消、用户态展示、计数重建 |
| outbox | 可靠通知 counter/feed/rank/notification |
| 详情计数优先 counter | note 不直接承担所有聚合计数 |
| API ID 使用 string | 避免前端大整数精度问题 |

### 7.5 面试讲法

```text
Note 服务负责笔记事实和互动明细，但最终计数交给 Counter。点赞收藏先写明细表，再通过事件异步更新计数、通知和榜单。这样写路径不会同步耦合多个下游服务。
```

## 8. Comment

### 8.1 解决的问题

Comment 负责一级评论、回复、删除、评论点赞和评论列表。

### 8.2 当前实现

| 能力 | 状态 |
|---|---|
| 发布一级评论 | 已实现 |
| 回复评论 | 已实现 |
| 删除评论 | 已实现 |
| 评论列表和回复列表 | 已实现 |
| 评论点赞/取消点赞 | 已实现 |
| 我的评论 | 已实现 |
| 评论计数来源接口 | 已实现 |

### 8.3 核心流程

```text
发布评论
  -> 调用 note 内部接口校验笔记可评论
  -> 写 content_comment / comment_content / user_comment
  -> 写 CommentCreated outbox
  -> counter 更新评论数
  -> notification 生成评论通知
```

### 8.4 方案选型

| 设计 | 原因 |
|---|---|
| 一级评论和回复分层 | 控制复杂度，不做无限楼中楼 |
| 删除走状态 | 保留审计和回复关系 |
| 评论点赞明细 | 支持取消和计数重建 |
| 评论内容独立表 | 为后续审核、脱敏、冷热拆分预留 |

### 8.5 面试讲法

```text
评论服务发布评论前会校验笔记是否可评论，评论事实落库后通过事件驱动计数和通知。删除评论使用状态变更而不是直接物理删除，避免破坏回复结构和审计链路。
```

## 9. Relation

### 9.1 解决的问题

Relation 负责关注关系事实、关注列表、粉丝列表和关注状态。

### 9.2 当前实现

| 能力 | 状态 |
|---|---|
| 关注 | 已实现 |
| 取关 | 已实现 |
| 关注列表 | 已实现 |
| 粉丝列表 | 已实现 |
| 单个和批量关注状态 | 已实现 |
| relation outbox | 已实现 |

### 9.3 核心流程

```text
关注用户
  -> 校验不能关注自己
  -> 校验双方用户状态
  -> 写 following / follower 读模型
  -> 写 relation_change_log
  -> 写 UserFollowed outbox
```

### 9.4 方案选型

| 设计 | 原因 |
|---|---|
| following 和 follower 双读模型 | 关注列表和粉丝列表都需要高效分页 |
| 关注幂等 | 重复点击不应生成重复关系 |
| UserFollowed 事件 | 驱动 counter、feed、notification |
| relation 不维护计数 | 计数归 counter 聚合 |

### 9.5 面试讲法

```text
Relation 只维护关注关系事实和列表读模型。关注成功后发 UserFollowed，Counter 更新关注数和粉丝数，Feed 补发作者近期笔记，Notification 生成关注通知。
```

## 10. Counter

### 10.1 解决的问题

Counter 负责把点赞、收藏、评论、关注、发笔记等行为转成可展示的聚合计数。

### 10.2 当前实现

| 能力 | 状态 |
|---|---|
| 批量查询计数 | 已实现 |
| 消费业务事件 | 已实现 |
| `counter_snapshot` | 已实现 |
| Redis 在线计数 | 已实现 |
| `counter_delta_log` | 已实现 |
| 消费幂等记录 | 已实现 |
| 重建任务和预热 | 已实现 |
| `CounterChanged` outbox | 已实现 |

### 10.3 核心流程

```text
业务事件
  -> counter event adapter 转成 delta
  -> 记录 consume record
  -> 写 counter_delta_log
  -> 更新 counter_snapshot
  -> 最佳努力更新 Redis Hash
  -> 写 CounterChanged outbox
```

### 10.4 查询降级

```text
Redis
  -> MySQL counter_snapshot
  -> 来源服务 counter-source
  -> 回填快照和 Redis
```

### 10.5 方案选型

| 设计 | 原因 |
|---|---|
| Redis 在线计数 | 高频读写更快 |
| MySQL 快照 | Redis 丢失后可恢复 |
| 来源服务回源 | 快照不存在时仍能从行为事实聚合 |
| CounterChanged | 下游榜单只关心计数变化，不关心所有原始行为 |

### 10.6 面试讲法

```text
Counter 采用最终一致设计。互动服务只写行为事实并发事件，Counter 消费事件更新 Redis 和 MySQL 快照。查询优先 Redis，Redis 不可用就降级 MySQL 或来源服务回源。这样既能支撑高频展示，也能保证数据可恢复。
```

## 11. Feed

### 11.1 解决的问题

Feed 负责关注页内容流。用户关注的人发布笔记后，关注页应该能高效分页读取。

### 11.2 当前实现

| 能力 | 状态 |
|---|---|
| 关注页 Feed | 已实现 |
| feed_note_index | 已实现 |
| fanout 主任务和子任务 | 已实现 |
| feed_inbox_item | 已实现 |
| Redis 收件箱 | 已实现 |
| 关注补发 | 已实现 |
| 取关清理 | 已实现 |
| 重建和重试接口 | 已实现 |

### 11.3 核心流程

```text
NotePublished
  -> 写 feed_note_index
  -> 查询作者粉丝
  -> 创建 fanout_task / fanout_sub_task
  -> 投递 feed_inbox_item
  -> 写 Redis 收件箱
  -> 写 FeedDelivered outbox
```

### 11.4 读路径

```text
GET /api/feed/following
  -> 优先 Redis 收件箱
  -> 降级 MySQL 收件箱
  -> 必要时按关注作者近期笔记回源
  -> 聚合 note/user/counter
```

### 11.5 方案选型

| 设计 | 原因 |
|---|---|
| 收件箱模型 | 关注页读多，分页稳定 |
| fanout 任务 | 发布和投递解耦，可重试 |
| MySQL 收件箱 | Redis 丢失后可恢复 |
| 后续推拉结合 | 大 V 粉丝多时避免全量写扩散 |

### 11.6 面试讲法

```text
Feed 当前是关注页收件箱模型。普通作者发布后写扩散到粉丝收件箱，用户读取时优先 Redis，失败降级 MySQL。大 V 推拉结合是后续演进，这样个人项目先保证最小闭环和可重建能力。
```

## 12. Notification

### 12.1 解决的问题

Notification 负责站内通知列表和未读数，不负责 WebSocket 或厂商 Push。

### 12.2 当前实现

| 能力 | 状态 |
|---|---|
| 未读数 | 已实现 |
| 通知列表和详情 | 已实现 |
| 单条和批量已读 | 已实现 |
| 删除通知 | 已实现 |
| 点赞收藏聚合通知 | 已实现 |
| 评论、回复、关注、系统、订单通知 | 已实现 |
| 未读数重建 | 已实现 |
| PushSendRequested | 已实现 |

### 12.3 核心流程

```text
业务事件
  -> 判断接收人
  -> 点赞/收藏按 note 聚合
  -> 评论/回复/关注/订单生成明细通知
  -> 更新 unread counter
  -> 写 NotificationCreated 或 NotificationAggregated
  -> 写 PushSendRequested
```

### 12.4 方案选型

| 设计 | 原因 |
|---|---|
| 通知读模型 | 列表展示不反查所有业务表 |
| 聚合通知 | 多人点赞收藏同一笔记时减少打扰 |
| 未读数表 | Redis 只是缓存，可从 MySQL 重建 |
| Push 请求事件 | 通知服务不绑定具体投递通道 |

### 12.5 面试讲法

```text
Notification 维护站内通知事实和未读数。它消费互动、评论、关注和订单事件，生成通知后再写 PushSendRequested。站内通知和 Push 通道分离，Push 失败不会影响通知事实。
```

## 13. Push

### 13.1 解决的问题

Push 负责统一投递请求、设备、用户偏好、在线连接和投递日志。

### 13.2 当前实现

| 能力 | 状态 |
|---|---|
| 设备注册和解绑 | 已实现 |
| 推送偏好 | 已实现 |
| 点击回传 | 已实现 |
| 内部投递请求 | 已实现 |
| WebSocket 在线连接 | 已实现 |
| Redis 在线设备路由 | 已实现 |
| ACK 落库 | 已实现 |
| 投递日志 | 已实现 |

### 13.3 核心流程

```text
PushSendRequested
  -> push 消费请求
  -> 检查用户偏好和设备状态
  -> 查询 Redis 在线路由
  -> 在线则 WebSocket 投递
  -> 记录 delivery_attempt
  -> 客户端 ACK 后更新投递结果
```

### 13.4 方案选型

| 设计 | 原因 |
|---|---|
| PushSendRequested | 通知、IM、订单统一接入 |
| 设备和偏好独立 | 用户可以控制打扰和隐私 |
| Redis 在线路由 | 快速判断设备在线状态 |
| 投递日志 | 便于排查消息是否送达 |

### 13.5 面试讲法

```text
Push 服务不生成业务通知，只负责投递。通知、IM、订单通过 PushSendRequested 接入，Push 根据设备、偏好和在线状态决定是否 WebSocket 下发。真实厂商离线 Push 是后续 provider adapter 扩展点。
```

## 14. IM

### 14.1 解决的问题

IM 负责单聊会话和消息事实，保证消息可持久化、可拉取、可标记送达和已读。

### 14.2 当前实现

| 能力 | 状态 |
|---|---|
| 创建或获取单聊会话 | 已实现 |
| 会话列表 | 已实现 |
| 发送文本消息 | 已实现 |
| 消息列表 | 已实现 |
| 送达上报 | 已实现 |
| 已读上报 | 已实现 |
| 总未读数 | 已实现 |
| 会话设置和删除 | 已实现 |
| PushSendRequested | 已实现 |

### 14.3 核心流程

```text
发送消息
  -> 校验发送者和会话成员
  -> clientMsgId 幂等
  -> 写 im_message
  -> 写 im_conversation_message
  -> 写 im_user_message
  -> 更新会话最后消息和未读
  -> 写 ImMessageSent 和 PushSendRequested outbox
```

### 14.4 方案选型

| 设计 | 原因 |
|---|---|
| 消息先入库再投递 | 避免消息丢失 |
| clientMsgId | 移动端重试幂等 |
| 用户消息链 | 支持用户维度拉取和未读 |
| Push 负责下行提醒 | IM 不直接维护设备通道 |

### 14.5 面试讲法

```text
IM 的核心是消息可靠性。发送消息时先落库，再写事件请求 Push 投递。在线下发失败不代表消息丢失，接收方仍然可以通过消息列表拉取。当前实现是单聊文字消息 foundation。
```

## 15. Order

### 15.1 解决的问题

Order 负责神券秒杀活动、库存、抢券请求、订单、支付状态、发券和补偿。

### 15.2 当前实现

| 能力 | 状态 |
|---|---|
| 活动创建、列表、详情 | 已实现 |
| 活动预热、暂停、恢复、结束 | 已实现 |
| 上线前预检查 | 已实现 |
| 秒杀 token | 已实现 |
| Redis Lua 预扣 | 已实现 |
| 异步下单 | 已实现 |
| 免费券发券 | 已实现 |
| MOCK 支付 | 已实现 |
| 取消待支付订单 | 已实现 |
| 超时关单 | 已实现 |
| 库存回补、重建、对账修复 | 已实现 |

### 15.3 核心流程

```text
活动预热
  -> MySQL 活动库存写入 Redis

抢券
  -> 获取秒杀 token
  -> Redis Lua 校验 token、库存、限购
  -> 预扣 Redis 库存
  -> 写 CouponSeckillAccepted
  -> MQ 异步创建订单

支付/关单
  -> 免费券直接发券
  -> 付费券 WAIT_PAY
  -> MOCK 支付成功后发券
  -> 超时未支付 CAS 关单并回补库存
```

### 15.4 方案选型

| 设计 | 原因 |
|---|---|
| 秒杀 token | 降低提前刷接口和恶意请求 |
| Redis Lua | 原子扣库存和限购 |
| 异步下单 | 削峰，减少入口等待 |
| MySQL 唯一约束 | 防重复下单兜底 |
| 状态机 CAS | 处理支付和关单并发 |
| 库存对账 | 修复 Redis 和 MySQL 不一致 |

### 15.5 面试讲法

```text
订单模块用 Redis Lua 做入口预扣和限购，用 MQ 异步下单削峰。MySQL 是最终事实，通过唯一约束和状态机 CAS 兜底。超时关单会回补库存，Redis 库存异常时可以从 MySQL 重建和对账修复。
```

## 16. Rank

### 16.1 解决的问题

Rank 负责本周热门笔记榜和本年创作者成长榜。

### 16.2 当前实现

| 能力 | 状态 |
|---|---|
| 周热笔记榜 | 已实现 |
| 年度创作者成长榜 | 已实现 |
| 当前创作者本人排名 | 已实现 |
| CounterChanged 增量计分 | 已实现 |
| Redis ZSet 在线榜 | 已实现 |
| MySQL 分数事实和贡献记录 | 已实现 |
| 快照和重建 | 已实现 |

### 16.3 核心流程

```text
NotePublished
  -> 建立 rank_note_index

CounterChanged
  -> 根据 rank_definition 权重计算分数
  -> 写 rank_score_contribution
  -> 更新 rank_member_score
  -> 写 rank_score_change_log
  -> 更新 Redis ZSet
  -> 写 RankChanged outbox
```

### 16.4 方案选型

| 设计 | 原因 |
|---|---|
| CounterChanged 驱动 | 不直接耦合点赞、评论、收藏明细 |
| Redis ZSet | 天然适合 Top 排名 |
| MySQL 分数事实 | 支持排查、重建、快照 |
| 贡献记录 | 支持删除、私密、下架后的扣回和恢复 |

### 16.5 面试讲法

```text
排行榜通过 NotePublished 建候选索引，通过 CounterChanged 增量计分。Redis ZSet 用于在线查询，MySQL 保留分数事实和贡献记录。笔记删除或不可见后，可以暂停贡献并从榜单移除。
```

## 17. 模块之间的事件关系

| 事件 | 生产方 | 主要消费者 | 用途 |
|---|---|---|---|
| `UserRegistered` | auth | user | 创建或补偿用户资料 |
| `UserProfileUpdated` | user | 未来读模型 | 用户资料变更同步 |
| `FileUploaded` | file | note 等未来模块 | 文件上传完成 |
| `NotePublished` | note | counter、feed、rank | 作者作品数、Feed 投递、榜单候选 |
| `NoteLiked` / `NoteCollected` | note | counter、notification | 计数和通知 |
| `CommentCreated` | comment | counter、notification | 评论数和评论通知 |
| `UserFollowed` | relation | counter、feed、notification | 关注计数、关注补发、关注通知 |
| `CounterChanged` | counter | rank | 榜单增量计分 |
| `PushSendRequested` | notification、im、order | push | 在线或离线提醒 |
| `ImMessageSent` | im | push / 后续订阅方 | 消息提醒 |
| `CouponSeckillAccepted` | order | order | 异步下单 |
| `CouponIssued` | order | notification | 订单通知 |
| `RankChanged` | rank | 后续订阅方 | 榜单变化 |

## 18. 如何把模块讲成项目亮点

不要这样讲：

```text
我做了登录、笔记、评论、关注、订单、排行榜。
```

更好的讲法：

```text
我按社区业务拆了身份、用户、内容、互动、分发、触达、实时消息、交易和榜单模块。每个模块都有明确事实归属：比如点赞收藏明细归 note，聚合计数归 counter；通知事实归 notification，在线投递归 push；订单库存和状态归 order。跨模块状态变化通过 RocketMQ 事件和 outbox 解耦，Redis 读模型都保留 MySQL 快照、行为事实或重建路径。
```

这句话能体现：

1. 你不是只会写接口。
2. 你知道数据归属。
3. 你理解缓存和 MQ 的边界。
4. 你能诚实说明个人项目的复杂度控制。
