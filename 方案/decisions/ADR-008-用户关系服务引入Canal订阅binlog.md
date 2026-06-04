# ADR-008-用户关系服务引入 Canal 订阅 binlog

状态：Accepted  
日期：2026-06-03

## 背景

BlueNote 用户关系服务需要支持关注、取关、关注列表、粉丝列表和关注关系查询。

参考资料中的用户关系服务方案以关注方向表作为数据中心，通过 binlog 订阅链路派生粉丝方向表、Redis 缓存和计数等下游数据。这个方案适合用户关系场景，因为一次关注关系变更会影响多份读模型：

1. 关注事实主表。
2. 粉丝方向查询表。
3. Redis 关注列表缓存。
4. Redis 最近粉丝列表缓存。
5. 关注数和粉丝数。
6. Feed 补充和清理。
7. 关注通知。

如果所有派生数据都放在关注接口内同步更新，主链路会变长，也会把关注接口和计数、Feed、通知等下游能力耦合在一起。

## 备选方案

| 方案 | 说明 |
|---|---|
| 本地事务同时写 Following 和 Follower 表 | 实现简单，但无法体现 binlog 派生链路；缓存、计数、Feed 仍需额外事件 |
| 关系服务写库后直接发 RocketMQ 业务事件 | 与现有整体方案一致，但粉丝表和 Redis 缓存派生仍需要应用层显式处理 |
| 使用 Canal 监听 MySQL binlog | 更贴近参考资料的伪从方案，可基于数据库变更驱动派生读模型 |
| 使用 Debezium | CDC 能力完整，但部署和生态对当前项目偏重，且现有方案已选择 RocketMQ |
| 直接让下游服务消费 Canal CDC | 实现链路短，但会让下游服务强耦合关系服务表结构 |

## 决策

BlueNote 第一阶段在用户关系服务中引入 Canal 订阅 MySQL binlog。

使用范围限定为：

```text
bluenote_relation.relation_change_log
```

链路设计为：

```text
关系服务写 relation_following + relation_change_log
  -> MySQL ROW binlog
  -> Canal 监听 relation_change_log INSERT
  -> Canal 投递 RocketMQ relation-cdc
  -> 关系服务内部 CDC 消费器
  -> 派生 relation_follower
  -> 更新 Redis 关系缓存
  -> 写 relation_outbox_event
  -> 发布 relation-event: UserFollowed / UserUnfollowed
  -> 计数、Feed、通知、IM 消费业务事件
```

重要边界：

1. Canal CDC 是关系服务内部同步机制。
2. `relation-cdc` 是内部 Topic，不作为跨服务业务协议。
3. 计数服务、Feed 服务、通知服务、IM 服务不直接消费原始 CDC。
4. 跨服务只消费稳定业务事件 `UserFollowed`、`UserUnfollowed`。
5. 第一阶段不把 Canal 扩散到所有业务服务，只在用户关系服务使用。

## 决策原因

1. 用户关系服务天然存在双向查询模型，适合用主表变更派生粉丝方向表。
2. 关注关系变更会影响缓存、计数、Feed、通知等多个下游，CDC 可以降低主写链路耦合。
3. Canal + RocketMQ 可以和当前已选 MQ 技术栈配合，不额外引入 Kafka。
4. 监听 `relation_change_log` 比直接监听业务主表更稳定，方便幂等、重放和排查。
5. 引入 Canal 能让项目体现 CDC、读模型派生、最终一致等真实企业项目中的工程能力。
6. 将 CDC 限定在关系服务内部，可以避免下游服务依赖数据库表结构。

## 影响

正向影响：

1. 用户关系服务方案与参考资料中“Following 表作为数据中心，Follower、Redis、计数等作为伪从”的思路基本一致。
2. 关注接口只需要写关注事实和变更流水，主链路更短。
3. 粉丝表、Redis 缓存、业务事件可以通过统一 CDC 消费链路派生。
4. 关系派生数据具备按 `changeId` 重放和补偿的基础。
5. 简历和项目说明中可以清晰表达 Canal + RocketMQ CDC 实践。

代价和风险：

1. 第一阶段多引入一个中间件 Canal，部署和排查复杂度上升。
2. Canal、RocketMQ、CDC 消费器都会带来延迟，粉丝表、计数、Feed、通知只能最终一致。
3. CDC 消息可能重复或乱序，需要 `changeId` 幂等和 `relationVersion` 乱序保护。
4. MySQL 必须开启 ROW binlog，并为 Canal 配置复制权限。
5. 2 核 4G 云服务器资源有限，需要限制 Canal JVM 内存和监听范围。

应对措施：

1. Canal 第一阶段只监听 `relation_change_log`，不监听全库全表。
2. `relation_change_log.change_id` 作为 CDC 幂等键。
3. `relation_following.relation_version` 作为乱序保护版本。
4. 关系服务内部维护 `relation_cdc_consume_log`。
5. 对外业务事件使用 `relation_outbox_event` 可靠投递。
6. 建立 CDC 延迟、消费失败、outbox 堆积和 Canal 存活告警。
7. 提供按 `changeId` 范围重放的补偿任务。
8. Redis 缓存必须可从 MySQL 关系表重建。

## 后续重新评估条件

出现以下情况时重新评估 Canal 使用范围或实现方式：

1. Canal 在 2 核 4G 云服务器上资源占用明显影响 MySQL、RocketMQ 或业务服务。
2. CDC 延迟长期超过业务可接受范围。
3. 关系服务规模增长，需要分库分表或更完整的数据同步平台。
4. 项目后续引入 Kafka、Flink、推荐系统或数据湖，需要统一 CDC 数据流平台。
5. Canal 运维复杂度超过它带来的工程收益。
6. 其他服务也出现强 CDC 需求，需要从单服务实践升级为统一数据同步方案。

