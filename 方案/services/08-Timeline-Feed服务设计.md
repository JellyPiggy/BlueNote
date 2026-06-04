# BlueNote Timeline Feed 服务设计

## 1. 背景与目标

Timeline Feed 服务负责 BlueNote 的关注页 Feed 流，也就是用户打开关注页时看到的“我关注的人最近发布的公开笔记”。它不是推荐流，不做兴趣推荐、算法排序和附近内容，只解决基于关注关系的时间线分发和读取。

Timeline Feed 的核心问题是：当作者发布笔记后，如何让关注他的用户高效刷到这篇笔记，并且保证列表按发布时间稳定分页。

典型实现方式有三种：

1. 拉模式：用户读取 Feed 时临时拉取所有关注作者的笔记，写入简单但读扩散严重。
2. 推模式：作者发布时把笔记写入所有粉丝收件箱，读取简单但写扩散严重。
3. 推拉结合：普通作者走推模式，大 V 作者走拉模式或只推给活跃粉丝。

本方案参考 `参考资料/11.Timeline Feed 服务.md` 的主线设计，采用推拉结合模式：

```text
普通作者发布公开笔记
  -> 推送到全部粉丝收件箱

大 V 作者发布公开笔记
  -> 推送到活跃粉丝收件箱
  -> 非活跃粉丝读取 Feed 时从作者发件箱拉取

用户读取关注页 Feed
  -> 读取自己的收件箱
  -> 读取关注的大 V 作者发件箱
  -> 多路归并、去重、过滤、补全详情
```

结合 BlueNote 第一阶段资源和规模，本方案做以下收敛：

1. 第一阶段只做关注页 Feed，不做推荐页 Feed。
2. 收件箱和作者发件箱在线层使用 Redis ZSET。
3. MySQL 保存 Feed 轻量索引、收件箱投递快照、fanout 任务和重建任务，便于恢复和排查。
4. Feed 服务只保存 `noteId`、`authorId`、`publishedAt`、状态等轻量信息，不保存笔记正文和图片列表。
5. 笔记详情、作者资料、计数展示分别调用笔记服务、用户服务、计数服务批量补全。
6. 笔记服务只发布笔记事件，不直接写 Feed 收件箱。
7. Feed 内部使用 `feed-fanout-task-event` 拆分扩散子任务，保证扩散可重试。

设计目标：

1. 支持关注页 Feed 列表查询。
2. 支持下拉刷新和上滑加载更早内容。
3. 支持新公开笔记向粉丝 Feed 扩散。
4. 支持大 V 推拉结合策略，避免极端写扩散。
5. 支持关注新作者后补充作者近期公开笔记。
6. 支持取消关注后清理或过滤对应作者笔记。
7. 支持笔记删除、私密、下架后 Feed 不再展示。
8. 支持 Feed 收件箱 Redis ZSET 和 MySQL 快照恢复。
9. 支持 fanout 任务进度、重试、幂等和补偿。
10. 支持批量组装 Feed 卡片，避免移动端多次请求。

关键约束：

1. 笔记事实归笔记服务，Feed 服务不保存笔记正文最终版本。
2. 关注关系事实归用户关系服务，Feed 服务不直接修改关系数据。
3. 计数归计数服务，Feed 服务不维护点赞数、评论数、收藏数。
4. 用户资料归用户服务，Feed 服务最多保存作者展示快照用于降级。
5. Redis 是在线 Feed 读取层，丢失后必须能通过 MySQL 快照、关系和笔记数据重建。
6. Feed 是最终一致，Feed 扩散失败不能回滚笔记发布。

## 2. 功能范围

### 2.1 第一阶段支持

| 功能 | 说明 |
|---|---|
| 关注页 Feed 查询 | 查询当前用户关注作者的公开笔记流 |
| 下拉刷新 | 获取最新 N 条关注页内容 |
| 上滑分页 | 通过 `publishedAt + noteId` 游标获取更早内容 |
| 新笔记扩散 | 消费 `NotePublished` 后写入粉丝收件箱 |
| 作者发件箱 | 保存作者公开笔记轻量索引，支持大 V 拉模式 |
| 推拉结合 | 普通作者全量推，大 V 推活跃粉丝并支持读取时拉取 |
| fanout 子任务 | 发布扩散拆分为可重试子任务 |
| 投递进度 | 记录每个扩散任务和子任务进度 |
| 新关注补历史 | 消费 `UserFollowed` 后补充作者近期公开笔记 |
| 取关清理 | 消费 `UserUnfollowed` 后清理或读取时过滤作者笔记 |
| 笔记状态同步 | 删除、私密、下架后不再展示 |
| Feed 卡片组装 | 批量获取笔记摘要、作者资料和计数 |
| 收件箱重建 | Redis 丢失或异常时重建用户 Feed |
| 幂等和补偿 | MQ 重复、Redis 失败、任务中断可恢复 |

### 2.2 第一阶段不支持

| 功能 | 暂不支持原因 |
|---|---|
| 推荐页 Feed | 需要推荐算法、召回和排序系统，后续单独设计 |
| 附近 Feed | 依赖地理位置和隐私策略 |
| 个性化排序 | 第一阶段只按发布时间倒序 |
| 混排广告 | 暂无商业化需求 |
| 置顶、屏蔽某作者 | 后续配合用户偏好和运营能力设计 |
| 朋友圈式可见范围 | 当前笔记只支持公开、私密，好友可见后续设计 |
| 无限历史 Feed | 控制存储和查询成本，限制最大读取时间或条数 |
| 完整实时强一致 | Feed 可以最终一致，读时过滤兜底 |
| 复杂本地缓存 | 第一阶段先用 Redis，热点明显后再加本地缓存 |
| 分库分表 | 当前用户量小，先按分片字段设计索引 |

### 2.3 后续扩展

后续可以扩展：

1. 推荐页 Feed，接入推荐召回、排序和特征服务。
2. 好友可见、粉丝可见、黑名单过滤等复杂可见性。
3. 作者分组和特别关注。
4. 屏蔽作者、减少此类内容、兴趣标签混排。
5. 大 V 内容本地缓存。
6. Feed 收件箱冷热分离，只保留近期内容在 Redis。
7. Feed 收件箱按 `userId` 分库分表。
8. Fanout 执行器横向扩展和限速调度。
9. Feed 卡片专用读模型，减少跨服务组装成本。
10. 推荐 Feed 和关注 Feed 的统一客户端协议。

## 3. 服务边界

### 3.1 Feed 服务负责

Feed 服务负责：

1. 关注页 Feed 收件箱。
2. 作者发件箱轻量索引。
3. 笔记发布后的 Feed 扩散。
4. 大 V 推拉结合策略。
5. Feed 列表分页和游标。
6. Feed 内容去重和排序。
7. Feed 项状态过滤。
8. Feed 投递任务、进度和补偿。
9. Feed Redis 重建。
10. Feed 卡片聚合组装。

典型数据包括：

| 数据 | 说明 |
|---|---|
| `feedItemId` | Feed 投递项 ID，可用 `userId + noteId` 唯一定位 |
| `userId` | 收件箱所属用户 |
| `noteId` | 笔记 ID |
| `authorId` | 作者用户 ID |
| `publishedAt` | 笔记发布时间，用于排序 |
| `sourceType` | `PUSH` / `PULL` / `FOLLOW_BACKFILL` |
| `itemStatus` | `VISIBLE` / `HIDDEN` / `DELETED` |
| `authorStrategy` | `NORMAL` / `BIG_AUTHOR` |

### 3.2 笔记服务负责

笔记服务负责：

1. 笔记正文、标题、媒体和版本。
2. 笔记状态和可见性事实。
3. 笔记批量摘要接口。
4. 作者最近公开笔记接口。
5. `NotePublished`、`NoteDeleted`、`NoteVisibilityChanged`、`NoteStatusChanged` 事件。

Feed 服务不直接访问笔记数据库，也不保存笔记正文最终版本。

### 3.3 用户关系服务负责

用户关系服务负责：

1. 关注关系事实。
2. 粉丝列表分页接口。
3. 关注列表分页接口。
4. `UserFollowed`、`UserUnfollowed` 事件。

Feed 服务通过关系服务获取粉丝和关注列表，不直接访问关系数据库。

### 3.4 用户服务负责

用户服务负责：

1. 用户资料。
2. 用户状态。
3. 作者昵称、头像等展示信息。
4. 用户是否允许展示内容的状态。

Feed 服务可以保存作者快照用于降级，但资料事实仍以用户服务为准。

### 3.5 计数服务负责

计数服务负责：

1. 笔记点赞数。
2. 笔记收藏数。
3. 笔记评论数。
4. 批量计数查询。

Feed 服务只调用计数服务查询展示计数，不维护计数字段。

### 3.6 不属于 Feed 服务

Feed 服务不负责：

1. 推荐算法。
2. 排行榜热度计算。
3. 笔记审核。
4. 点赞、收藏、评论行为事实。
5. 关注关系事实。
6. IM 和通知。
7. 搜索索引。

## 4. 核心概念

### 4.1 Timeline 排序

关注页 Feed 按时间线排序：

```text
publishedAt DESC, noteId DESC
```

规则：

1. 发布时间越新越靠前。
2. 发布时间相同，`noteId` 越大越靠前。
3. 游标必须同时携带 `publishedAt` 和 `noteId`，避免同一时间发布的内容分页重复或丢失。

### 4.2 收件箱

收件箱是用户维度的 Feed 列表。

Redis Key：

```text
bluenote:{env}:feed:inbox:{userId}
```

含义：

1. Score 为 `publishedAt` 的毫秒时间戳。
2. Member 为 20 位补零 `noteId` 字符串。
3. 只保存笔记 ID 和排序信息，不保存正文。

收件箱主要保存被推送来的普通作者笔记，以及大 V 推给活跃粉丝的笔记。

### 4.3 作者发件箱

作者发件箱是作者维度的公开笔记轻量列表。

Redis Key：

```text
bluenote:{env}:feed:author:outbox:{authorId}
```

用途：

1. 记录作者已发布且可进入关注页的公开笔记。
2. 支持大 V 拉模式。
3. 支持用户新关注作者时补充近期笔记。
4. 支持 Feed 重建。

作者发件箱不是笔记事实来源，只是 Feed 服务的轻量索引。笔记删除、私密、下架时必须从发件箱移除或标记不可见。

### 4.4 推模式

推模式指作者发布笔记后，Feed 服务主动把该笔记写入粉丝收件箱。

适用场景：

1. 普通作者。
2. 粉丝数较少。
3. 大 V 的活跃粉丝。

优点是读取快，缺点是发布时存在写扩散。

### 4.5 拉模式

拉模式指用户读取 Feed 时，临时读取部分作者发件箱。

适用场景：

1. 大 V 作者。
2. 粉丝数很高，不适合全量推送。
3. 非活跃粉丝不提前写收件箱。

优点是避免大规模写扩散，缺点是读取时要合并多个列表。

### 4.6 推拉结合

第一阶段策略：

```text
粉丝数 <= bigAuthorThreshold:
  NORMAL，推送全部粉丝

粉丝数 > bigAuthorThreshold:
  BIG_AUTHOR，推送活跃粉丝
  非活跃粉丝读取时从作者发件箱拉取
```

建议初始阈值：

| 参数 | 建议值 | 说明 |
|---|---|---|
| `bigAuthorThreshold` | 5000 | 当前小规模项目足够保守 |
| `activeDays` | 30 天 | 最近 30 天访问过关注页或登录过 |
| `fanoutBatchSize` | 500 | 每个扩散子任务粉丝数量 |
| `authorOutboxLimit` | 1000 | 每个作者发件箱保留近期公开笔记 |
| `inboxLimit` | 2000 | 每个用户收件箱保留近期 Feed 项 |

这些值是第一阶段设计值，实现时放到配置中心。

### 4.7 活跃粉丝

活跃粉丝用于大 V 推送筛选。

第一阶段定义：

```text
最近 activeDays 内登录过或访问过关注页的粉丝
```

Feed 服务可维护：

```text
bluenote:{env}:feed:active:user
```

也可以通过用户服务提供的最近活跃时间判断。为了减少依赖，第一阶段建议 Feed 服务在用户访问关注页时记录活跃时间。

### 4.8 Fanout 任务

Fanout 任务表示一篇笔记向粉丝收件箱扩散的过程。

包括：

1. 主任务：对应一篇笔记的一次扩散。
2. 子任务：对应一批粉丝 ID 的收件箱写入。

内部 Topic：

```text
feed-fanout-task-event
```

该 Topic 只在 Feed 服务内部使用，由 Fanout Dispatcher 生成子任务，由 Fanout Executor 执行。

### 4.9 Feed 游标

Feed 游标包含：

```text
publishedAt
noteId
```

编码示例：

```text
1748953800000_90001
```

下拉刷新不需要传游标。上滑加载更早内容时传 `cursor`。

查询更早内容的条件：

```text
publishedAt < cursor.publishedAt
or
publishedAt = cursor.publishedAt and noteId < cursor.noteId
```

### 4.10 Feed 卡片

Feed 服务读取阶段先得到 `noteId` 列表，再组装 Feed 卡片。

卡片内容包括：

1. 笔记摘要。
2. 封面 URL。
3. 作者摘要。
4. 点赞、评论、收藏计数。
5. 当前用户点赞、收藏状态可以由笔记服务详情或后续互动聚合提供，第一阶段 Feed 卡片可不返回当前用户互动状态，进入详情后再查。

## 5. 核心流程

### 5.1 笔记发布扩散

笔记服务发布公开笔记后发送 `NotePublished`。

Feed 服务处理：

```text
消费 NotePublished
  -> 按 eventId 幂等
  -> 校验 noteStatus = PUBLISHED
  -> 校验 visibility = PUBLIC
  -> 写 feed_note_index
  -> 写作者发件箱 Redis ZSET
  -> 查询或计算作者分发策略
  -> 创建 feed_fanout_task
  -> 分页调用关系服务获取粉丝
  -> 普通作者选择全部粉丝
  -> 大 V 作者筛选活跃粉丝
  -> 按 fanoutBatchSize 拆分子任务
  -> 写 feed_fanout_sub_task，message_status = PENDING
  -> 投递 feed-fanout-task-event
  -> 投递成功后 message_status = SENT
```

说明：

1. 私密笔记不进入 Feed。
2. 下架、待审核、删除笔记不进入 Feed。
3. 笔记服务不等待 Feed 扩散。
4. Feed 扩散失败由 Feed 服务任务重试。
5. 如果子任务 MQ 投递失败，补偿任务扫描 `message_status = PENDING/FAILED` 的子任务重新投递。

### 5.2 Fanout 子任务执行

Fanout Executor 消费 `feed-fanout-task-event`。

流程：

```text
消费 FeedFanoutSubTaskCreated
  -> 按 subTaskId 幂等
  -> 查询 feed_fanout_sub_task
  -> 从 progressUserId 后继续处理
  -> 遍历 targetUserIds
  -> 写 Redis 收件箱 ZADD
  -> upsert feed_inbox_item
  -> 更新 progressUserId
  -> 所有用户完成后标记子任务 SUCCESS
  -> 如果所有子任务完成，标记主任务 SUCCESS
```

写入 Redis：

```text
ZADD bluenote:{env}:feed:inbox:{userId} publishedAtMillis paddedNoteId
ZREMRANGEBYRANK bluenote:{env}:feed:inbox:{userId} 0 -(inboxLimit + 1)
```

注意：`ZREMRANGEBYRANK` 裁剪时要保留最新 `inboxLimit` 条。实现时需要按 ZSET 升序索引删除最旧内容。

MySQL 快照：

```text
feed_inbox_item(user_id, note_id, author_id, published_at, source_type)
```

MySQL 快照用于重建、排查和取关清理，不作为在线 Feed 查询首选。

### 5.3 大 V 作者处理

Feed 服务维护作者分发策略：

```text
feed_author_strategy
```

策略刷新时机：

1. 作者发布笔记时，根据粉丝数判断。
2. 计数服务或关系服务事件后异步更新。
3. 定时任务校准大 V 标记。

大 V 发布时：

```text
写作者发件箱
  -> 只推活跃粉丝
  -> 非活跃粉丝不提前写收件箱
```

用户读取 Feed 时：

```text
查询我关注的大 V 作者
  -> 读取这些作者发件箱
  -> 和我的收件箱合并
```

为控制读扩散，建议第一阶段：

1. 单次最多拉取 50 个大 V 作者发件箱。
2. 如果用户关注的大 V 超过 50 个，只取最近活跃或最近发布的大 V，剩余依赖后续推荐或分页优化。
3. 记录 `feed_big_author_pull_limited_total` 指标。

### 5.4 读取关注页 Feed

移动端请求：

```text
GET /api/feed/following?cursor=&size=20
```

流程：

```text
网关校验登录态
  -> Feed 服务记录用户活跃时间
  -> 解析 cursor
  -> 读取用户收件箱候选 noteId
  -> 查询用户关注的大 V 作者列表
  -> 读取大 V 作者发件箱候选 noteId
  -> 多路归并，按 publishedAt DESC, noteId DESC
  -> 去重
  -> 按 feed_note_index 过滤不可见笔记
  -> 必要时调用笔记服务批量摘要做二次可见性过滤
  -> 批量调用计数服务获取计数
  -> 批量调用用户服务获取作者摘要
  -> 组装 Feed 卡片
  -> 返回 nextCursor
```

候选拉取数量建议：

1. 收件箱拉取 `size * 3`。
2. 每个大 V 作者发件箱拉取 `min(size + 1, 30)`。
3. 归并后如果过滤导致不足一页，最多补拉 3 次。

读取时必须二次过滤：

1. `feed_note_index.item_status = VISIBLE`。
2. 笔记服务批量摘要返回仍可见。
3. 如果用户已取消关注作者，不能展示该作者笔记。

### 5.5 下拉刷新

下拉刷新不传 cursor。

流程：

```text
读取收件箱最新候选
  -> 读取关注大 V 作者最新候选
  -> 合并最新 size 条
  -> 返回 nextCursor = 最后一条 Feed 的 publishedAt_noteId
```

如果没有关注任何用户：

```text
返回空列表，并提示前端展示关注引导
```

### 5.6 上滑加载更早

上滑传入 cursor：

```text
cursor = lastPublishedAt_lastNoteId
```

收件箱查询条件：

```text
publishedAt < lastPublishedAt
or
publishedAt = lastPublishedAt and noteId < lastNoteId
```

作者发件箱使用相同条件。

Redis ZSET 处理：

1. Score 使用 `publishedAtMillis`。
2. Member 使用 20 位补零 `noteId`。
3. 同 Score 时，Member 字典序和 noteId 数字顺序一致。
4. 从高到低扫描后，在应用层或 Lua 中过滤同 Score 下 `member < paddedLastNoteId` 的内容。

### 5.7 多路归并

候选列表包括：

1. 用户收件箱列表。
2. 大 V 作者发件箱列表。

每个列表内部已按：

```text
publishedAt DESC, noteId DESC
```

排序。

合并规则：

```text
循环取各列表首元素
  -> 选择 publishedAt 最大的
  -> publishedAt 相同选择 noteId 最大的
  -> 加入结果
  -> 对应列表指针后移
  -> 已出现 noteId 跳过
  -> 满 size 后结束
```

去重原因：

1. 大 V 内容可能已经推给活跃粉丝，同时读取时又从作者发件箱拉到。
2. 新关注补历史可能和正常 fanout 重叠。
3. MQ 重试可能导致重复投递，虽然写入层有幂等，读取层仍做兜底。

### 5.8 新关注作者补历史

关系服务发布 `UserFollowed` 后，Feed 服务可以补充作者近期公开笔记。

流程：

```text
消费 UserFollowed
  -> 按 eventId 幂等
  -> 调用笔记服务 authors/recent 或读取作者发件箱
  -> 获取最近 N 条公开笔记
  -> 写入当前用户收件箱
  -> sourceType = FOLLOW_BACKFILL
```

建议第一阶段：

1. 补最近 20 条公开笔记。
2. 只补 `publishedAt` 在最近 90 天内的笔记。
3. 如果作者是大 V，也可以只补最近 10 条，避免新关注后刷屏。

### 5.9 取消关注清理

关系服务发布 `UserUnfollowed` 后，Feed 服务处理：

```text
消费 UserUnfollowed
  -> 按 eventId 幂等
  -> 标记或删除该作者在用户收件箱中的近期 Feed 项
  -> 删除 Redis 收件箱中的对应 noteId
```

取关清理可以异步完成，读取时必须兜底：

1. Feed 服务查询当前用户关注列表或关注作者集合。
2. 如果候选笔记作者不在关注集合中，则过滤。

这样即使清理失败，也不会继续展示已取关作者的内容。

### 5.10 笔记删除、私密和下架

Feed 服务消费：

1. `NoteDeleted`
2. `NoteVisibilityChanged`
3. `NoteStatusChanged`

处理：

```text
标记 feed_note_index item_status = HIDDEN/DELETED
  -> 从作者发件箱 Redis ZSET 移除
  -> 写 feed_cleanup_task
  -> 可异步清理部分用户收件箱
```

不建议同步扫描所有粉丝收件箱删除，因为成本高、容易阻塞。读取时依赖 `feed_note_index` 和笔记服务批量摘要二次过滤，保证不可见笔记不展示。

### 5.11 Feed 重建

用户 Feed 收件箱异常或 Redis 丢失时，可以重建。

流程：

```text
创建 feed_rebuild_task(userId)
  -> 查询用户关注列表
  -> 对普通作者读取作者发件箱近期内容
  -> 对大 V 作者读取作者发件箱近期内容
  -> 合并最近 inboxLimit 条
  -> 写 Redis 收件箱
  -> upsert feed_inbox_item
  -> 发布 FeedRebuilt
```

重建期间：

1. 查询接口可以直接走推拉结合读取路径，不强依赖收件箱完整。
2. 如果 Redis 收件箱为空，可从 MySQL `feed_inbox_item` 回填。
3. 重建失败记录任务并告警。

## 6. 异常流程

| 异常 | 处理方式 |
|---|---|
| 重复 `NotePublished` | `feed_event_consume_log` 幂等，`feed_note_index.note_id` 唯一 |
| 私密笔记发布事件 | 不扩散，只记录跳过原因 |
| 笔记发布后 Feed 扩散失败 | fanout task 保留，重试或补偿 |
| 关系服务获取粉丝失败 | fanout task 标记失败，延迟重试 |
| Fanout 子任务中断 | 根据 `progress_user_id` 继续处理 |
| Redis 写入失败 | 子任务失败重试，MySQL 快照不单独视为成功 |
| MySQL 快照写失败 | 子任务失败重试，避免只有 Redis 无法恢复 |
| 用户取关清理失败 | 读取时关注关系过滤兜底 |
| 笔记删除清理失败 | 读取时 `feed_note_index` 和笔记服务摘要过滤兜底 |
| 大 V 作者过多 | 限制单次拉取作者数，记录降级指标 |
| Feed 候选过滤后不足一页 | 补拉候选，最多补拉限定次数 |
| 计数服务不可用 | Feed 卡片计数返回 0 或降级值，标记 degraded |
| 用户服务不可用 | 使用作者快照或返回基础 authorId |
| 笔记服务摘要失败 | 返回空列表或降级，记录告警 |
| Redis 收件箱丢失 | MySQL 快照回填或触发 Feed 重建 |
| 游标非法 | 返回 `FEED_CURSOR_INVALID` |

补偿重点：

1. fanout 主任务和子任务失败。
2. outbox 投递失败。
3. Redis 收件箱与 MySQL 快照不一致。
4. 笔记状态变更后残留在 Feed。
5. 取关后残留在 Feed。
6. 用户 Feed 重建失败。

## 7. 存储设计

### 7.1 MySQL 表清单

| 表名 | 说明 |
|---|---|
| `feed_note_index` | Feed 侧笔记轻量索引 |
| `feed_inbox_item` | 用户收件箱投递快照 |
| `feed_author_strategy` | 作者分发策略 |
| `feed_fanout_task` | Feed 扩散主任务 |
| `feed_fanout_sub_task` | Feed 扩散子任务 |
| `feed_event_consume_log` | MQ 消费幂等表 |
| `feed_outbox_event` | Feed 对外事件 outbox |
| `feed_rebuild_task` | Feed 重建任务 |
| `feed_cleanup_task` | Feed 清理任务 |

### 7.2 feed_note_index

Feed 侧笔记轻量索引。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `note_id` | BIGINT | 笔记 ID |
| `author_id` | BIGINT | 作者 ID |
| `published_at` | DATETIME(3) | 发布时间 |
| `visibility` | VARCHAR(32) | `PUBLIC` / `PRIVATE` |
| `note_status` | VARCHAR(32) | 笔记状态 |
| `item_status` | VARCHAR(32) | `VISIBLE` / `HIDDEN` / `DELETED` |
| `cover_file_id` | BIGINT NULL | 封面文件 ID 快照，可选 |
| `title_snapshot` | VARCHAR(128) NULL | 标题快照，可选 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |
| `deleted` | TINYINT | 逻辑删除 |

说明：

1. `note_id` 唯一。
2. 只保存 Feed 判断和降级展示需要的轻量字段。
3. 笔记详情仍以笔记服务为准。

### 7.3 feed_inbox_item

用户收件箱投递快照。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | 收件箱用户 |
| `note_id` | BIGINT | 笔记 ID |
| `author_id` | BIGINT | 作者 ID |
| `published_at` | DATETIME(3) | 笔记发布时间 |
| `source_type` | VARCHAR(32) | `PUSH` / `BIG_AUTHOR_PUSH` / `FOLLOW_BACKFILL` |
| `item_status` | VARCHAR(32) | `VISIBLE` / `HIDDEN` / `DELETED` |
| `fanout_task_id` | VARCHAR(128) NULL | 来源扩散任务 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |
| `deleted` | TINYINT | 逻辑删除 |

说明：

1. `user_id + note_id` 唯一。
2. 主要用于重建、清理、排查，不作为高频在线查询首选。
3. 第一阶段数据量小，可以保留近期较长时间；后续可按时间归档。

### 7.4 feed_author_strategy

作者分发策略表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `author_id` | BIGINT | 作者 ID |
| `strategy_type` | VARCHAR(32) | `NORMAL` / `BIG_AUTHOR` |
| `follower_count_snapshot` | BIGINT | 粉丝数快照 |
| `big_author_threshold` | BIGINT | 判定阈值 |
| `active_days` | INT | 活跃粉丝窗口 |
| `last_evaluated_at` | DATETIME(3) | 最近评估时间 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

### 7.5 feed_fanout_task

Feed 扩散主任务。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `task_id` | VARCHAR(128) | 任务 ID |
| `note_id` | BIGINT | 笔记 ID |
| `author_id` | BIGINT | 作者 ID |
| `published_at` | DATETIME(3) | 发布时间 |
| `strategy_type` | VARCHAR(32) | `NORMAL` / `BIG_AUTHOR` |
| `target_mode` | VARCHAR(32) | `ALL_FOLLOWERS` / `ACTIVE_FOLLOWERS` |
| `task_status` | VARCHAR(32) | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` |
| `target_count` | BIGINT | 目标粉丝数 |
| `success_count` | BIGINT | 成功投递数 |
| `fail_count` | BIGINT | 失败数量 |
| `sub_task_count` | INT | 子任务数量 |
| `last_error` | VARCHAR(512) NULL | 最近错误 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

### 7.6 feed_fanout_sub_task

Feed 扩散子任务。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `sub_task_id` | VARCHAR(128) | 子任务 ID |
| `task_id` | VARCHAR(128) | 主任务 ID |
| `note_id` | BIGINT | 笔记 ID |
| `author_id` | BIGINT | 作者 ID |
| `published_at` | DATETIME(3) | 发布时间 |
| `target_user_ids` | JSON | 目标用户 ID 列表 |
| `progress_user_id` | BIGINT NULL | 最近成功处理的用户 ID |
| `sub_task_status` | VARCHAR(32) | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` |
| `message_status` | VARCHAR(32) | `PENDING` / `SENT` / `FAILED` |
| `message_sent_at` | DATETIME(3) NULL | 子任务消息发送时间 |
| `next_retry_at` | DATETIME(3) NULL | 下次投递重试时间 |
| `retry_count` | INT | 重试次数 |
| `last_error` | VARCHAR(512) NULL | 最近错误 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

说明：

1. `target_user_ids` 第一阶段可以用 JSON 保存，子任务规模受控。
2. 后续粉丝量大时可改为范围分片或单独目标表。
3. `feed_fanout_sub_task` 同时作为内部任务 outbox，`message_status` 用于保证 `feed-fanout-task-event` 可重试投递。

### 7.7 feed_event_consume_log

MQ 消费幂等表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `consumer_group` | VARCHAR(64) | 消费组 |
| `topic` | VARCHAR(64) | Topic |
| `event_id` | VARCHAR(128) | 事件 ID |
| `event_type` | VARCHAR(64) | 事件类型 |
| `consume_status` | VARCHAR(32) | `SUCCESS` / `FAILED` |
| `consume_attempts` | INT | 尝试次数 |
| `last_error` | VARCHAR(512) NULL | 最近错误 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

唯一约束：

```text
uk_consumer_event(consumer_group, event_id)
```

### 7.8 feed_outbox_event

Feed 对外事件 outbox。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `event_id` | VARCHAR(128) | 事件 ID |
| `event_type` | VARCHAR(64) | `FeedDelivered` / `FeedRebuilt` |
| `topic` | VARCHAR(64) | `feed-event` |
| `message_key` | VARCHAR(128) | 消息 Key |
| `payload` | JSON | 事件内容 |
| `send_status` | VARCHAR(32) | `PENDING` / `SENT` / `FAILED` |
| `retry_count` | INT | 重试次数 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

### 7.9 feed_rebuild_task

Feed 重建任务。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `task_id` | VARCHAR(128) | 任务 ID |
| `user_id` | BIGINT | 需要重建的用户 |
| `task_status` | VARCHAR(32) | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` |
| `reason` | VARCHAR(64) | 触发原因 |
| `progress` | JSON NULL | 进度 |
| `last_error` | VARCHAR(512) NULL | 最近错误 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

### 7.10 feed_cleanup_task

Feed 清理任务。

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 主键 |
| `task_id` | VARCHAR(128) | 任务 ID |
| `cleanup_type` | VARCHAR(32) | `UNFOLLOW` / `NOTE_DELETED` / `NOTE_HIDDEN` |
| `user_id` | BIGINT NULL | 用户 ID，取关清理时使用 |
| `author_id` | BIGINT NULL | 作者 ID |
| `note_id` | BIGINT NULL | 笔记 ID |
| `task_status` | VARCHAR(32) | `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` |
| `progress` | JSON NULL | 进度 |
| `last_error` | VARCHAR(512) NULL | 最近错误 |
| `created_at` | DATETIME(3) | 创建时间 |
| `updated_at` | DATETIME(3) | 更新时间 |

### 7.11 索引设计

| 表 | 索引 | 用途 |
|---|---|---|
| `feed_note_index` | `uk_note(note_id)` | 笔记唯一索引 |
| `feed_note_index` | `idx_author_time(author_id, published_at, note_id)` | 作者发件箱重建 |
| `feed_note_index` | `idx_status_time(item_status, published_at)` | 扫描可见或异常笔记 |
| `feed_inbox_item` | `uk_user_note(user_id, note_id)` | 投递幂等 |
| `feed_inbox_item` | `idx_user_time(user_id, published_at, note_id)` | MySQL 降级读取和重建 |
| `feed_inbox_item` | `idx_user_author(user_id, author_id, published_at)` | 取关清理 |
| `feed_author_strategy` | `uk_author(author_id)` | 查询作者策略 |
| `feed_fanout_task` | `uk_task(task_id)` | 主任务唯一 |
| `feed_fanout_task` | `idx_status(task_status, updated_at)` | 扫描失败和待处理任务 |
| `feed_fanout_sub_task` | `uk_sub_task(sub_task_id)` | 子任务唯一 |
| `feed_fanout_sub_task` | `idx_task_status(task_id, sub_task_status)` | 汇总主任务进度 |
| `feed_fanout_sub_task` | `idx_message_status(message_status, next_retry_at)` | 扫描待投递子任务消息 |
| `feed_event_consume_log` | `uk_consumer_event(consumer_group, event_id)` | MQ 消费幂等 |
| `feed_rebuild_task` | `idx_status(task_status, updated_at)` | 扫描重建任务 |
| `feed_cleanup_task` | `idx_status(task_status, updated_at)` | 扫描清理任务 |

### 7.12 Redis Key 设计

#### 用户收件箱

```text
Key: bluenote:{env}:feed:inbox:{userId}
Type: ZSET
Score: publishedAtMillis
Member: 20 位补零 noteId
TTL: 不设置短 TTL，按 inboxLimit 裁剪
重建来源: feed_inbox_item、feed_note_index、关系服务
```

#### 作者发件箱

```text
Key: bluenote:{env}:feed:author:outbox:{authorId}
Type: ZSET
Score: publishedAtMillis
Member: 20 位补零 noteId
TTL: 不设置短 TTL，按 authorOutboxLimit 裁剪
重建来源: feed_note_index、笔记服务 authors/recent
```

#### 用户活跃时间

```text
Key: bluenote:{env}:feed:active:user
Type: ZSET
Score: lastActiveAtMillis
Member: userId
TTL: 不设置短 TTL，定期裁剪超过 activeDays 的用户
```

#### 作者策略缓存

```text
Key: bluenote:{env}:feed:author:strategy:{authorId}
Type: String 或 Hash
Fields: strategyType, followerCountSnapshot, evaluatedAt
TTL: 1 小时
重建来源: feed_author_strategy
```

#### 关注大 V 缓存

```text
Key: bluenote:{env}:feed:user:big-authors:{userId}
Type: ZSET 或 Set
Member: authorId
Score: 最近发布时间或策略更新时间
TTL: 10 分钟
重建来源: 关系服务关注列表 + feed_author_strategy
```

#### 消费幂等短缓存

```text
Key: bluenote:{env}:feed:idempotent:{consumerGroup}:{eventId}
Type: String
TTL: 7 天
用途: 减少 MySQL 幂等表查询
```

## 8. 接口设计

### 8.1 移动端接口

#### 8.1.1 查询关注页 Feed

```text
GET /api/feed/following?cursor=&size=20
```

请求 Header：

```text
Authorization: Bearer access_token
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `cursor` | 否 | 上滑加载更早内容时传入 |
| `size` | 否 | 默认 20，最大 50 |

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "items": [
      {
        "noteId": "90001",
        "author": {
          "userId": "10001",
          "nickname": "小蓝",
          "avatarUrl": "https://example.com/a.png"
        },
        "title": "杭州周末咖啡店记录",
        "contentPreview": "这家店的拿铁不错...",
        "coverUrl": "https://example.com/cover.jpg",
        "noteType": "IMAGE_TEXT",
        "counts": {
          "likeCount": 12,
          "collectCount": 3,
          "commentCount": 5
        },
        "publishedAt": "2026-06-03T20:30:00+08:00"
      }
    ],
    "nextCursor": "1748953800000_90001",
    "hasMore": true,
    "degraded": false
  }
}
```

说明：

1. 返回的是关注页 Feed 卡片，不是笔记详情。
2. 当前用户是否点赞、收藏第一阶段可以不在 Feed 列表返回，进入详情后查。
3. 如果计数、用户资料部分降级，`degraded = true`。

#### 8.1.2 刷新关注页 Feed

刷新仍使用同一接口，不传 cursor：

```text
GET /api/feed/following?size=20
```

### 8.2 内部接口

#### 8.2.1 触发用户 Feed 重建

```text
POST /internal/feed/users/{userId}/rebuild
```

请求：

```json
{
  "reason": "REDIS_LOST"
}
```

响应：

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "taskId": "feed_rebuild_10001_20260603",
    "taskStatus": "PENDING"
  }
}
```

#### 8.2.2 查询重建任务

```text
GET /internal/feed/rebuild-tasks/{taskId}
```

#### 8.2.3 查询 Feed 投递状态

```text
GET /internal/feed/fanout-tasks/{taskId}
```

用途：

1. 排查某篇笔记是否扩散完成。
2. 查看子任务失败原因。
3. 运维重试失败任务。

#### 8.2.4 手动重试 Fanout 任务

```text
POST /internal/feed/fanout-tasks/{taskId}/retry
```

第一阶段可只作为内部运维接口设计，后续管理端再接入。

### 8.3 错误码

| 错误码 | 说明 |
|---|---|
| `FEED_CURSOR_INVALID` | 游标格式非法 |
| `FEED_SIZE_EXCEEDED` | 请求数量超过限制 |
| `FEED_QUERY_DEGRADED` | Feed 查询降级 |
| `FEED_REBUILD_TASK_NOT_FOUND` | 重建任务不存在 |
| `FEED_FANOUT_TASK_NOT_FOUND` | 扩散任务不存在 |
| `FEED_INTERNAL_DEPENDENCY_FAILED` | 内部依赖失败 |
| `FEED_UNAUTHORIZED` | 未登录或登录态无效 |

## 9. 安全与风控设计

### 9.1 身份和权限

要求：

1. 查询关注页 Feed 必须登录。
2. `userId` 只能来自网关注入的用户上下文。
3. 移动端不能传入任意 `userId` 查询他人关注页 Feed。
4. 内部接口必须服务间鉴权。
5. fanout、rebuild、cleanup 管理接口不能暴露公网。

### 9.2 可见性安全

Feed 展示必须满足：

1. 笔记状态为 `PUBLISHED`。
2. 笔记可见性为 `PUBLIC`。
3. 当前用户仍关注作者。
4. 作者和笔记未被系统隐藏。

即使收件箱里残留旧数据，读取时也必须过滤。

### 9.3 防刷和限流

建议限制：

| 操作 | 限制 |
|---|---|
| 查询关注页 Feed | 网关按用户和 IP 限流 |
| 单次 Feed size | 最大 50 |
| 单用户上滑深度 | 最多读取近期 2000 条或 90 天 |
| 重建任务 | 同一用户短时间只能触发一次 |
| Fanout 重试 | 失败任务指数退避 |

### 9.4 数据安全

Feed 服务不保存手机号、邮箱、密码、完整正文、完整图片列表。

日志中不记录：

1. Access Token。
2. 内部服务签名。
3. 大批量粉丝 ID 全量列表。
4. 过长笔记内容。

### 9.5 活跃用户策略

活跃用户用于大 V 推送优化，不用于隐私判断。

要求：

1. 活跃时间只作为系统分发策略。
2. 不对普通用户展示“谁是活跃粉丝”。
3. 活跃数据可以过期删除。

## 10. 前后端实现要点

### 10.1 移动端实现

移动端页面：

1. 关注页 Feed 列表。
2. 下拉刷新。
3. 上滑加载更多。
4. 空状态关注引导。

实现要点：

1. 首次进入和下拉刷新不传 cursor。
2. 上滑使用服务端返回的 `nextCursor`。
3. 不自己拼接时间和 noteId 游标。
4. 列表项点击进入笔记详情。
5. Feed 计数可能最终一致，不要依赖本地计数作为事实。
6. 接口降级时仍展示可用内容，计数或作者信息可使用占位。
7. 如果返回空列表且用户没有关注，展示关注引导。

### 10.2 后端模块

后端模块建议：

```text
bluenote-feed
  ├── api
  ├── application
  ├── domain
  ├── infrastructure
  │   ├── mysql
  │   ├── redis
  │   ├── mq
  │   └── client
  ├── fanout
  ├── merge
  ├── mq
  └── job
```

职责：

| 模块 | 职责 |
|---|---|
| `api` | Feed 查询和内部运维接口 |
| `application` | 编排查询、重建、扩散 |
| `domain` | Feed 游标、排序、分发策略 |
| `fanout` | 分发器和执行器 |
| `merge` | 多路归并、去重、过滤 |
| `infrastructure` | MySQL、Redis、RocketMQ、下游服务客户端 |
| `job` | 失败任务重试、Redis 重建、清理任务 |

### 10.3 笔记服务集成

Feed 服务依赖：

```text
POST /internal/notes/batch-summary
POST /internal/notes/authors/recent
```

要求：

1. 批量摘要接口必须返回 `noteStatus` 和 `visibility`。
2. 不可见笔记不应返回给普通用户。
3. 作者近期笔记接口只用于补历史和重建，不用于每次在线读所有普通作者。

### 10.4 关系服务集成

Feed 服务依赖：

```text
GET /internal/relations/users/{userId}/followers/page
GET /internal/relations/users/{userId}/following/page
```

用途：

1. 发布扩散时分页获取粉丝。
2. 读取时获取关注的大 V 作者。
3. 用户 Feed 重建时获取关注列表。
4. 取关后清理对应作者内容。

### 10.5 计数服务集成

Feed 卡片需要批量查询：

```text
POST /internal/counters/batch
```

字段：

```text
NOTE.like_count
NOTE.collect_count
NOTE.comment_count
```

计数服务不可用时，Feed 列表可以返回 0 或隐藏计数，并标记降级。

### 10.6 用户服务集成

Feed 卡片需要作者摘要：

```text
POST /internal/users/batch-summary
```

用户服务不可用时，Feed 服务可以使用 `feed_note_index` 或笔记摘要中的作者快照降级。

## 11. 数据一致性与事件

### 11.1 本地事务边界

消费 `NotePublished` 时，本地事务负责：

```text
写 feed_event_consume_log
写 feed_note_index
写 feed_fanout_task
写 feed_fanout_sub_task(message_status = PENDING)
```

Redis 写入和 fanout 子任务执行不在同一事务中，通过任务状态和重试保证最终一致。
`feed_fanout_sub_task` 本身承担内部任务 outbox，投递任务扫描 `message_status = PENDING/FAILED` 的记录发送 `feed-fanout-task-event`。

### 11.2 订阅事件

| Topic | 事件 | 用途 |
|---|---|---|
| `note-event` | `NotePublished` | 写作者发件箱并创建扩散任务 |
| `note-event` | `NoteDeleted` | 标记 Feed 项不可见并清理作者发件箱 |
| `note-event` | `NoteVisibilityChanged` | 公开和私密切换时扩散或隐藏 |
| `note-event` | `NoteStatusChanged` | 下架、恢复时更新 Feed 可见性 |
| `relation-event` | `UserFollowed` | 补充作者近期公开笔记 |
| `relation-event` | `UserUnfollowed` | 清理或过滤已取关作者笔记 |

### 11.3 内部事件

内部 Topic：

```text
Topic: feed-fanout-task-event
Tag: FeedFanoutSubTaskCreated
Producer: bluenote-feed Fanout Dispatcher
Consumer: bluenote-feed Fanout Executor
```

该 Topic 不作为跨业务服务协议。

事件示例：

```json
{
  "eventId": "evt_feed_fanout_sub_task_90001_1",
  "eventType": "FeedFanoutSubTaskCreated",
  "eventVersion": 1,
  "occurredAt": "2026-06-03T20:30:00+08:00",
  "producer": "bluenote-feed",
  "traceId": "trace-id",
  "payload": {
    "taskId": "feed_fanout_90001",
    "subTaskId": "feed_fanout_90001_1",
    "noteId": "90001",
    "authorId": "10001",
    "publishedAt": "2026-06-03T20:30:00+08:00",
    "targetUserIds": ["20001", "20002"]
  }
}
```

### 11.4 发布事件

Topic：

```text
feed-event
```

事件：

| 事件 | 触发时机 |
|---|---|
| `FeedDelivered` | 笔记投递到用户收件箱，第一阶段可只对批量任务聚合发布 |
| `FeedRebuilt` | 用户 Feed 重建完成 |

第一阶段 `FeedDelivered` 可以不逐用户发布，避免事件量过大。更建议只作为运维监控事件或任务完成事件。

### 11.5 NotePublished 处理要求

`NotePublished` 必须携带：

1. `noteId`
2. `authorId`
3. `visibility`
4. `noteStatus`
5. `publishedAt`
6. `coverFileId`
7. `title` 或摘要，可选

Feed 服务只处理：

```text
visibility = PUBLIC
noteStatus = PUBLISHED
```

### 11.6 NoteVisibilityChanged 处理

场景：

| 变化 | Feed 处理 |
|---|---|
| `PRIVATE -> PUBLIC` | 等同新公开，写作者发件箱并扩散 |
| `PUBLIC -> PRIVATE` | 标记不可见，移除作者发件箱，读取过滤 |

事件必须携带 `fromVisibility` 和 `toVisibility`。如果缺少前后状态，Feed 服务只触发校准或重建，不直接扩散。

### 11.7 幂等策略

| 场景 | 幂等方式 |
|---|---|
| 外部事件消费 | `feed_event_consume_log(consumer_group, event_id)` |
| 笔记索引 | `feed_note_index.note_id` 唯一 |
| 收件箱投递 | `feed_inbox_item(user_id, note_id)` 唯一，Redis ZADD 天然覆盖 |
| fanout 主任务 | `task_id` 唯一 |
| fanout 子任务 | `sub_task_id` 唯一 |
| Feed 重建 | 同一用户同一时间窗口只允许一个 RUNNING 任务 |

### 11.8 最终一致性

一致性级别：

1. 笔记事实以笔记服务为准。
2. 关注关系以关系服务为准。
3. Feed 收件箱最终一致。
4. Redis 和 MySQL 快照最终一致。
5. Feed 查询读取时做可见性和关注关系兜底过滤。

用户可能短时间遇到：

1. 新发布笔记几秒后才出现在关注页。
2. 取关后 Feed 里短暂残留旧笔记，但刷新或过滤后消失。
3. 笔记删除后收件箱残留，但不会展示。

这些都属于可接受的最终一致窗口。

## 12. 日志、指标与告警

### 12.1 日志

必须记录：

1. Feed 查询成功和失败。
2. Feed 查询降级。
3. `NotePublished` 消费和跳过原因。
4. fanout 主任务创建。
5. fanout 子任务执行成功和失败。
6. Redis 收件箱写入失败。
7. 关系服务粉丝分页失败。
8. 新关注补历史成功和失败。
9. 取关清理成功和失败。
10. 笔记状态变更清理成功和失败。
11. Feed 重建成功和失败。
12. outbox 投递失败。

日志字段：

| 字段 | 说明 |
|---|---|
| `traceId` | 链路 ID |
| `eventId` | MQ 事件 ID |
| `userId` | 当前用户或收件箱用户 |
| `authorId` | 作者 ID |
| `noteId` | 笔记 ID |
| `taskId` | fanout 或 rebuild 任务 |
| `subTaskId` | fanout 子任务 |
| `operation` | 操作类型 |
| `result` | 成功或失败 |
| `errorCode` | 错误码 |
| `costMs` | 耗时 |

### 12.2 指标

| 指标 | 说明 |
|---|---|
| `feed_query_total` | Feed 查询次数 |
| `feed_query_fail_total` | Feed 查询失败次数 |
| `feed_query_degraded_total` | Feed 查询降级次数 |
| `feed_query_latency_ms` | Feed 查询延迟 |
| `feed_inbox_hit_total` | 收件箱读取次数 |
| `feed_big_author_pull_total` | 大 V 发件箱拉取次数 |
| `feed_merge_candidate_total` | Feed 候选数量 |
| `feed_merge_filtered_total` | 被过滤候选数量 |
| `feed_note_published_consume_total` | 笔记发布事件消费次数 |
| `feed_fanout_task_total` | fanout 主任务数量 |
| `feed_fanout_sub_task_total` | fanout 子任务数量 |
| `feed_fanout_fail_total` | fanout 失败次数 |
| `feed_fanout_lag_ms` | 发布到投递完成延迟 |
| `feed_relation_query_fail_total` | 关系服务调用失败次数 |
| `feed_note_summary_fail_total` | 笔记摘要调用失败次数 |
| `feed_counter_degraded_total` | 计数服务降级次数 |
| `feed_rebuild_total` | Feed 重建任务数量 |
| `feed_rebuild_fail_total` | Feed 重建失败次数 |
| `feed_big_author_pull_limited_total` | 大 V 拉取受限次数 |

### 12.3 告警

建议告警：

1. Feed 查询失败率异常升高。
2. Feed 查询 P95 延迟升高。
3. fanout 任务积压超过阈值。
4. fanout 失败持续增加。
5. `feed-fanout-task-event` 消费延迟升高。
6. Redis 收件箱写入失败。
7. 关系服务粉丝分页失败率升高。
8. 笔记摘要接口失败率升高。
9. 大 V 拉取受限次数异常升高。
10. Feed 重建任务连续失败。
11. 笔记删除后过滤数量异常升高，可能说明清理任务滞后。

## 13. 测试重点

### 13.1 单元测试

1. Feed 游标解析和非法游标处理。
2. `publishedAt DESC, noteId DESC` 排序比较。
3. 多路归并逻辑。
4. 重复 noteId 去重。
5. 普通作者和大 V 策略判断。
6. 活跃粉丝筛选。
7. `NotePublished` 事件过滤私密笔记。
8. `NoteVisibilityChanged` 前后状态映射。

### 13.2 集成测试

1. 消费 `NotePublished` 后写 `feed_note_index`。
2. 普通作者发布后创建 fanout task 和子任务。
3. Fanout Executor 写 Redis 收件箱和 `feed_inbox_item`。
4. 子任务失败后按进度继续。
5. 大 V 作者只推活跃粉丝。
6. 查询 Feed 时合并收件箱和大 V 发件箱。
7. `UserFollowed` 后补充作者近期笔记。
8. `UserUnfollowed` 后清理或读取过滤作者笔记。
9. `NoteDeleted` 后 Feed 不再展示。
10. Redis 丢失后可以重建用户收件箱。

### 13.3 接口测试

1. 未登录不能查询关注页 Feed。
2. 首次查询不传 cursor 返回最新内容。
3. 上滑传 cursor 返回更早内容。
4. 同一发布时间多条笔记不重复、不丢失。
5. size 超过限制返回错误。
6. cursor 非法返回错误。
7. 用户未关注任何人返回空列表。
8. 下游计数服务不可用时 Feed 降级返回。

### 13.4 MQ 测试

1. `NotePublished` 重复投递幂等。
2. `feed-fanout-task-event` 重复投递幂等。
3. Fanout 子任务处理到一半失败后重试。
4. RocketMQ 暂停后恢复，fanout 继续执行。
5. `UserFollowed` 和 `UserUnfollowed` 重复消费幂等。
6. `NoteDeleted` 与 `NotePublished` 乱序时最终不可见。

### 13.5 性能测试

重点压测：

1. 关注页 Feed 查询。
2. Redis 收件箱读取。
3. 大 V 作者发件箱合并。
4. fanout 子任务写入吞吐。
5. 笔记摘要、计数、用户摘要批量调用。
6. Redis 收件箱裁剪。

第一阶段目标建议：

1. Feed 查询 Redis 正常、下游正常时 P95 小于 300ms。
2. 单次 Feed 查询最多返回 20 到 50 条。
3. Fanout 普通作者 5000 粉丝以内分钟级完成。
4. 大 V 内容对活跃粉丝投递延迟小于 1 分钟。
5. Redis 不可用时允许 Feed 查询降级或短暂失败，但必须告警。

## 14. 风险与后续演进

| 风险 | 说明 | 应对 |
|---|---|---|
| Feed 扩散延迟 | 新笔记不会立即出现在粉丝关注页 | fanout 任务监控、重试、积压告警 |
| 大 V 读扩散 | 用户关注很多大 V 时读取变慢 | 限制拉取大 V 数量、缓存大 V 发件箱 |
| Redis 内存增长 | 收件箱和发件箱都在 Redis | 按 inboxLimit、authorOutboxLimit 裁剪 |
| 取关后内容残留 | 异步清理可能失败 | 读取时关注关系过滤兜底 |
| 删除后内容残留 | 不同步扫全部收件箱 | `feed_note_index` 过滤 + 笔记服务二次校验 |
| Fanout 任务过大 | 粉丝多时任务执行慢 | 子任务拆分、进度记录、限速 |
| MySQL 快照膨胀 | `feed_inbox_item` 长期增长 | 后续按时间归档或只保留近期 |
| 下游聚合接口慢 | 笔记、用户、计数接口影响 Feed 延迟 | 批量查询、超时降级、局部返回 |
| 推拉结合逻辑复杂 | 收件箱和发件箱合并容易重复或漏数据 | 统一排序游标、多路归并单元测试 |
| 2 核 4G 资源有限 | RocketMQ、Redis、MySQL 资源受限 | 控制 fanout 并发、批量大小和保留条数 |

后续演进：

1. 增加 Feed 卡片缓存，减少笔记、用户、计数批量调用。
2. 大 V 作者发件箱增加本地缓存。
3. 收件箱从全量 MySQL 快照演进为近期快照 + 冷数据归档。
4. Fanout Executor 横向扩容并增加动态限速。
5. 接入推荐流后，把关注页和推荐页抽象为统一 Feed 协议。
6. 支持好友可见、黑名单和隐私关系过滤。
7. 对 Feed 查询增加预取和客户端缓存策略。
8. 对异常 fanout 任务提供管理端重试和跳过能力。
