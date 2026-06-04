# BlueNote 总仓库协作准则

本文是 BlueNote 仓库根目录的总协作规则，适用于所有 Codex、人工开发者和后续自动化任务。

进入本仓库工作时，必须先阅读本文。若任务涉及方案、后端、移动端、部署、数据库、MQ、接口契约或测试，还必须继续阅读对应目录下的补充规则和方案文档。

## 1. 项目定位

BlueNote 是一个简易版小红书项目，面向移动端用户，核心能力包括登录注册、用户资料、笔记发布、图片上传、评论、点赞、收藏、关注、Feed、排行榜、通知、IM，以及后续可扩展的神券订单能力。

本项目按真实企业项目方式落地。第一阶段可以控制部署复杂度，但代码结构、服务边界、数据一致性、接口契约、安全认证、消息事件、可观测性和部署约束必须从一开始按长期演进设计。

## 2. 权威上下文

任何 Codex 开始工作前，应按下面顺序读取上下文：

1. `AGENTS.md`
2. `方案/AGENTS.md`
3. `方案/01-总体技术方案.md`
4. `方案/02-服务划分方案.md`
5. `方案/08-落地实施方案.md`
6. 与当前任务直接相关的服务设计文档，例如 `方案/services/04-笔记发布服务设计.md`
7. `方案/decisions/` 下相关 ADR

如果当前任务只涉及 `方案/services/` 下的服务设计，还要阅读 `方案/services/AGENTS.md`。

后续如果新增 `backend/AGENTS.md`、`mobile/AGENTS.md`、`deploy/AGENTS.md` 或 `docs/AGENTS.md`，对应目录内工作必须同时遵守这些局部规则。

## 3. 已确定技术路线

### 3.1 移动端

1. uni-app
2. Vue 3
3. TypeScript
4. Pinia
5. `uni.request` 封装
6. Vite，随 uni-app 体系
7. 目标平台优先 Android / iOS / H5，后续可扩展小程序

### 3.2 后端

1. Java 21 LTS
2. Spring Boot 3.5.x
3. Spring Cloud 2025.0.x
4. Spring MVC
5. Spring Security
6. MyBatis-Plus + MyBatis XML
7. Jakarta Validation
8. OpenAPI / Knife4j
9. Maven

### 3.3 基础设施

1. Spring Cloud Gateway
2. Nacos 注册中心和配置中心
3. MySQL 8.x
4. Redis 7.x
5. RocketMQ 5.x
6. MinIO 或云 OSS/COS/R2
7. Docker Compose
8. Nginx 或 Caddy
9. Prometheus / Grafana / Loki
10. Spring Boot Actuator / Micrometer Tracing

第一阶段不主动引入 Kubernetes、Kafka、Seata、Elasticsearch、Flink、多机房容灾或完整推荐系统，除非先更新方案并形成 ADR。

## 4. 目录规划

推荐总仓库结构：

```text
BlueNote/
  AGENTS.md
  方案/
  backend/
  mobile/
  deploy/
  docs/
  scripts/
```

目录职责：

| 目录 | 职责 |
|---|---|
| `方案/` | 架构方案、服务设计、ADR，不直接放工程代码 |
| `backend/` | Java 后端 Maven 多模块工程、SQL、后端测试 |
| `mobile/` | uni-app 移动端工程 |
| `deploy/` | Docker Compose、Nacos、Nginx、Prometheus、Grafana 等部署配置 |
| `docs/contracts/` | API、MQ、Redis Key、错误码等契约 |
| `docs/engineering/` | 工程结构、编码规范、联调规范 |
| `docs/testing/` | 测试用例、验收清单 |
| `scripts/` | 运维、部署、备份、压测等脚本 |

不要把工程代码写进 `方案/`。方案文档和可执行工程产物要分开维护。

## 5. 后端落地边界

后端采用 Maven 多模块。第一阶段按逻辑服务设计，物理应用可以合并部署。

推荐物理应用分组：

| 物理应用 | 包含逻辑服务 |
|---|---|
| `bluenote-gateway-app` | gateway |
| `bluenote-member-app` | auth、user |
| `bluenote-content-app` | file、note、comment |
| `bluenote-social-app` | relation、counter、feed、rank、notification |
| `bluenote-im-app` | im |
| `bluenote-order-app` | order |

合并部署不等于合并边界。每个逻辑服务仍要保持独立包结构、独立 schema、独立接口契约和独立事件语义。

禁止：

1. 一个服务直接访问另一个服务的数据库 schema。
2. 跨 schema join。
3. 把业务判断放进 `bluenote-common-*`。
4. 让移动端直接调用内部服务。
5. 把 Redis 或 MQ 当成唯一事实来源。

## 6. 移动端落地边界

移动端负责用户可见页面、状态管理、请求封装、上传体验、登录态恢复、弱网与错误态处理。

移动端必须遵守：

1. 所有接口调用集中在 `mobile/src/api/`。
2. 登录态、用户信息、设备信息使用 Pinia 管理。
3. 页面不得散写后端请求路径。
4. API 字段优先以 `docs/contracts/api/` 或后端 OpenAPI 为准。
5. 后端未完成时可以使用 mock，但 mock 必须贴近契约，不能自造最终接口。
6. 每个核心页面都要处理 loading、empty、error、token 过期、网络失败、重复点击和分页结束。

## 7. 契约优先

多个 Codex 并行时，先冻结共享契约，再分别实现。

共享契约包括：

1. 统一响应结构和分页结构。
2. 错误码。
3. 移动端 API。
4. 内部服务接口。
5. MySQL DDL。
6. Redis Key。
7. RocketMQ Topic、事件 Envelope、payload、consumer group。
8. OpenAPI / Knife4j 输出。

修改共享契约时必须说明影响范围。影响移动端、多个后端模块、数据库或 MQ 的变更，不能只在某个业务模块里悄悄修改。

## 8. 多 Codex 分工建议

推荐从两个 Codex 开始：

| Codex | 分支 | 负责范围 |
|---|---|---|
| 后端基础 | `codex/backend-base` | `backend/` 骨架、公共能力、第一批 DDL、local compose、API 契约 |
| 移动端 | `codex/mobile-app` | `mobile/` 工程、页面骨架、请求封装、登录态、第一条主链路页面 |

第一条主链路稳定后，再增加：

| Codex | 分支 | 负责范围 |
|---|---|---|
| 内容域 | `codex/backend-content` | file、note、comment |
| 社交分发域 | `codex/backend-social` | relation、counter、feed、rank、notification |
| 实时通信域 | `codex/backend-im` | push、im |
| 交易域 | `codex/backend-order` | order |
| 部署运维 | `codex/deploy-ops` | test/prod compose、Nacos、监控、备份、发布回滚 |

不要一开始让 13 个 Codex 分别实现 13 个逻辑服务。那会让公共依赖、接口契约、错误码和 DDL 很快分叉。

## 9. Git 协作规则

默认主干为 `main`。

每个 Codex 必须基于最新 `main` 创建自己的分支：

```text
codex/backend-base
codex/mobile-app
codex/backend-content
codex/backend-social
codex/backend-im
codex/backend-order
codex/deploy-ops
```

分支协作原则：

1. 每个 Codex 只改自己负责的目录和模块。
2. 共享目录由后端基础或指定负责人先建立。
3. 其他 Codex 如需改共享契约，先在提交说明或 PR 描述中明确影响范围。
4. 合并顺序优先为：基础契约 -> 后端基础 -> 移动端骨架 -> 内容域 -> 社交域 -> IM -> 订单 -> 部署运维。
5. 合并前必须确保当前分支能通过相关构建或测试。

高冲突目录：

1. `backend/pom.xml`
2. `backend/bluenote-common-*`
3. `docs/contracts/**`
4. `backend/sql/**`
5. `deploy/**`

这些目录不建议多个 Codex 同时随意修改。

## 10. 开发顺序

优先补齐第一批可执行产物：

1. `docs/engineering/` 工程结构与编码规范。
2. `docs/contracts/api/` API 通用规范、错误码和第一批接口契约。
3. `backend/sql/` 第一批 DDL：auth、user、file、note。
4. `deploy/compose/` 本地依赖一键启动。
5. `backend/` 后端基础工程和第一条主链路。
6. `mobile/` 移动端第一条主链路页面。

第一条主链路：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情
```

第二条主链路：

```text
关注用户 -> 发布笔记事件 -> Feed 投递 -> 关注页拉取 -> 点赞/收藏/评论 -> 计数更新 -> 通知生成
```

第三条主链路：

```text
设备注册 -> WebSocket 连接 -> IM 发送消息 -> 消息入库 -> 在线下发 -> 离线 Push 请求
```

第四条主链路：

```text
活动预热 -> 秒杀令牌 -> Redis Lua 预扣库存 -> RocketMQ 异步下单 -> 订单查询 -> 支付/发券 -> 超时关单
```

## 11. 变更方案的规则

如果实现过程中发现现有方案明显不合理，应先输出变更建议，说明：

1. 为什么需要修改。
2. 会影响哪些文档、接口、DDL、事件或模块。
3. 替代方案是什么。
4. 是否需要新增或修改 `方案/decisions/` 下的 ADR。

未经确认，不要直接推翻已确定的总体技术路线、服务边界、部署原则和核心基础设施。

## 12. 交付检查

每个 Codex 完成任务时，至少说明：

1. 改了哪些目录或模块。
2. 是否修改了共享契约。
3. 是否需要其他 Codex 同步调整。
4. 运行了哪些构建、测试或校验。
5. 还有哪些未完成风险。

后端服务至少检查：

1. 外部接口经过网关。
2. 写接口有参数校验。
3. 关键写接口支持幂等。
4. 业务表有唯一约束和必要索引。
5. MQ 消费有幂等记录。
6. 日志包含 `traceId`。
7. 错误响应使用统一错误码。
8. OpenAPI 文档可访问。

移动端至少检查：

1. 登录态恢复。
2. Token 过期处理。
3. loading、empty、error。
4. 网络失败提示。
5. 重复点击保护。
6. 图片上传失败处理。
7. 分页和刷新状态。

