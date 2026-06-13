# BlueNote Engineering Docs

版本：v0.1
状态：工程文档索引
更新时间：2026-06-13

## 1. 文档用途

本目录保存 BlueNote 的工程说明、联调手册、展示指南和后端实习项目学习材料。

如果你是第一次看这个项目，建议不要直接从所有代码文件开始读。先通过本文找到合适入口，再按业务链路进入具体契约和源码。

## 2. 推荐入口

| 你想做什么 | 推荐文档 |
|---|---|
| 快速判断项目是否值得看 | [README.md](../../README.md)、[README.zh-CN.md](../../README.zh-CN.md) |
| 了解当前做到哪里 | [current-status.md](current-status.md) |
| 本地跑通主链路 | [02-local-main-chain-runbook.md](02-local-main-chain-runbook.md) |
| 按后端实习项目学习 | [04-backend-internship-study-roadmap.md](04-backend-internship-study-roadmap.md) |
| 理解架构和核心业务流程 | [05-architecture-and-core-flows.md](05-architecture-and-core-flows.md) |
| 按模块理解具体架构、流程和方案选型 | [07-module-design-overview.md](07-module-design-overview.md) |
| 准备简历和面试讲解 | [06-interview-and-resume-guide.md](06-interview-and-resume-guide.md) |
| 对外展示 GitHub 项目 | [03-github-demo-guide.md](03-github-demo-guide.md) |
| 查看编码和工程规范 | [01-engineering-structure-and-coding-guidelines.md](01-engineering-structure-and-coding-guidelines.md) |

## 3. 文档清单

### 3.1 工程状态

`current-status.md`

记录当前已经完成的工程资产、验证记录、待办和风险。它是继续开发时的交接文档，不替代契约。

### 3.2 工程规范

`01-engineering-structure-and-coding-guidelines.md`

说明后端多模块结构、公共模块边界、业务模块分层、统一响应、错误码、数据库、Redis、MQ 和日志规范。

### 3.3 本地联调

`02-local-main-chain-runbook.md`

说明如何启动 Docker Compose 依赖、编译后端、启动应用、运行主链路冒烟脚本和启动移动端 H5。

### 3.4 GitHub 展示

`03-github-demo-guide.md`

说明对外展示项目时应该按什么顺序讲代码结构、本地运行、冒烟验证和移动端页面。

### 3.5 学习路线

`04-backend-internship-study-roadmap.md`

面向后端实习生，按 auth/user/file/note/comment/relation/counter/feed/notification/push/im/order/rank 的顺序解释怎么读项目、看哪些代码、理解哪些设计点。

### 3.6 架构与流程

`05-architecture-and-core-flows.md`

讲解总体架构、逻辑服务与物理应用、数据归属、技术选型，以及注册登录、发笔记、计数、Feed、通知、IM、订单、排行榜等核心流程。

### 3.7 简历与面试

`06-interview-and-resume-guide.md`

提供简历写法、项目 30 秒介绍、各模块面试讲法、常见追问和不要夸大的边界。

### 3.8 模块设计总览

`07-module-design-overview.md`

按 Gateway、Auth、User、File、Note、Comment、Relation、Counter、Feed、Notification、Push、IM、Order、Rank 逐个说明模块解决的问题、核心流程、方案选型和面试讲法。

## 4. 与其他目录的关系

| 目录 | 作用 |
|---|---|
| `docs/contracts/` | API、错误码、DDL、Redis Key、MQ 事件和权限契约，是编码与联调基线 |
| `docs/testing/` | 冒烟测试、契约核对和验收记录 |
| `方案/` | 原始中文架构方案、服务设计和 ADR |
| `backend/` | Java 后端 Maven 多模块工程和 SQL |
| `mobile/` | uni-app H5 移动端工程 |
| `deploy/` | Docker Compose、环境变量和部署相关配置 |

规则很简单：如果你要改接口字段、错误码、DDL、Redis Key 或 MQ 事件，优先改 `docs/contracts/`；如果你只是说明当前怎么运行、怎么展示、怎么学习，再改 `docs/engineering/`。
