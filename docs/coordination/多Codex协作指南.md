# BlueNote 多 Codex 协作指南

本文是给项目负责人使用的协作操作指南，用于决定如何开启多个 Codex、如何分支、如何合并，以及如何减少并行开发冲突。

本文不是 Codex 的根规则。Codex 进入仓库后应阅读根目录 `AGENTS.md`，并按具体任务读取相关方案文档。

## 1. 总体原则

不要一开始让 13 个 Codex 分别实现 13 个逻辑服务。

BlueNote 当前方案是：

1. 逻辑服务按微服务边界设计。
2. 第一阶段物理部署可以合并为较少 Java 进程。
3. 后端、移动端、部署、契约在一个总仓库内协同。

因此更适合按阶段和业务域开 Codex，而不是按每个逻辑服务开 Codex。

## 2. 推荐启动顺序

第一轮只开两个 Codex：

| Codex | 分支 | 负责范围 |
|---|---|---|
| 后端基础 | `codex/backend-base` | `backend/` 骨架、公共能力、第一批 DDL、local compose、API 契约 |
| 移动端 | `codex/mobile-app` | `mobile/` 工程、页面骨架、请求封装、登录态、第一条主链路页面 |

第一轮目标是跑通：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情
```

第一条主链路稳定后，再增加：

| Codex | 分支 | 负责范围 |
|---|---|---|
| 内容域 | `codex/backend-content` | file、note、comment |
| 社交分发域 | `codex/backend-social` | relation、counter、feed、rank、notification |
| 实时通信域 | `codex/backend-im` | push、im |
| 交易域 | `codex/backend-order` | order |
| 部署运维 | `codex/deploy-ops` | test/prod compose、Nacos、监控、备份、发布回滚 |

## 3. 分支策略

默认主干为 `main`。

每个 Codex 使用独立分支：

```text
codex/backend-base
codex/mobile-app
codex/backend-content
codex/backend-social
codex/backend-im
codex/backend-order
codex/deploy-ops
```

每个 Codex 启动前，先基于最新 `main` 新建分支：

```text
git clone git@github.com:JellyPiggy/BlueNote.git
cd BlueNote
git checkout -b codex/backend-base origin/main
```

完成后推送自己的分支：

```text
git push -u origin codex/backend-base
```

## 4. 合并顺序

建议合并顺序：

1. 基础契约
2. 后端基础
3. 移动端骨架
4. 内容域
5. 社交分发域
6. IM 和推送
7. 订单
8. 部署运维

如果某个分支修改了共享契约，应优先评审它对其他分支的影响，再决定是否合并。

## 5. 高冲突区域

这些目录和文件不建议多个 Codex 同时修改：

1. `backend/pom.xml`
2. `backend/bluenote-common-*`
3. `docs/contracts/**`
4. `backend/sql/**`
5. `deploy/**`

如果必须修改，应让负责该区域的 Codex 先完成基础版本，其他 Codex 再基于最新 `main` 继续。

## 6. 给 Codex 的开工提示词

后端基础 Codex：

```text
请先阅读根目录 AGENTS.md、方案/01-总体技术方案.md、方案/02-服务划分方案.md、方案/08-落地实施方案.md。
现在进入开发落地阶段，允许创建 backend、deploy、docs/contracts、backend/sql。
目标：按方案创建 Maven 多模块后端骨架、公共能力、统一响应、错误码、traceId、OpenAPI、MyBatis 基础配置、本地 Docker Compose、第一批 auth/user/file/note DDL。
不要实现所有业务，先保证工程可启动、契约清晰、后续服务 Codex 能接着做。
```

移动端 Codex：

```text
请先阅读根目录 AGENTS.md、方案/01-总体技术方案.md、方案/02-服务划分方案.md、方案/08-落地实施方案.md。
现在进入开发落地阶段，允许创建 mobile。
目标：创建 uni-app + Vue 3 + TypeScript + Pinia 移动端工程，完成请求封装、登录态管理、页面骨架、登录/注册、资料、上传、发布笔记、笔记详情的前端流程。
接口以 docs/contracts/api 或后端 OpenAPI 为准；后端未完成时可以用 mock，但不要自己发明最终接口字段。
```

内容域 Codex：

```text
请先阅读根目录 AGENTS.md、方案/01-总体技术方案.md、方案/02-服务划分方案.md、方案/08-落地实施方案.md，以及 services/03-文件服务设计.md、services/04-笔记发布服务设计.md、services/06-评论服务设计.md。
只负责 content 范围：file、note、comment。
不要修改 gateway/auth/user/social/im/order 的业务实现，不要改公共模块，除非先说明必须改的契约原因。
```

## 7. 每轮启动前检查

开新 Codex 前建议确认：

1. `main` 已包含最新根目录 `AGENTS.md`。
2. 该 Codex 的职责边界明确。
3. 它要读取的方案文档明确。
4. 它要修改的目录明确。
5. 它不应该修改的共享目录明确。
6. 已经有独立分支名。

