# BlueNote GitHub Demo Guide

版本：v0.2
状态：项目收口展示指南
更新时间：2026-06-13

## 1. 展示定位

BlueNote 适合作为个人全栈项目展示，重点不是“功能无限多”，而是：

1. 服务边界清楚。
2. 契约、DDL、Redis、MQ 和权限文档完整。
3. 后端多应用可运行。
4. 移动端 H5 有真实页面和真实接口链路。
5. 主链路可以通过脚本重复验收。

如果目标是让别人愿意 star，建议把项目定位讲成：

```text
一个面向后端实习项目学习和面试展示的社区类多模块后端项目。
```

不要只强调“功能很多”。更应该强调：

1. 每个模块的事实归属和服务边界。
2. 为什么使用 MySQL、Redis、RocketMQ、MinIO。
3. 计数、Feed、通知、订单和榜单这些典型后端问题怎么处理。
4. 当前哪些能力已经完成，哪些生产能力被诚实列为 non-goals。

## 2. 推荐演示顺序

### 2.1 先展示代码结构

推荐先打开：

```text
README.md
README.zh-CN.md
docs/assets/showcase/hero.png
docs/engineering/README.md
docs/contracts/README.md
backend/README.md
docs/engineering/current-status.md
```

说明重点：

1. `docs/contracts/` 是接口、数据库、Redis、MQ 和权限基线。
2. `backend/` 是 Java 21 + Spring Boot 多模块后端。
3. `mobile/` 是 uni-app H5 移动端。
4. `方案/services/` 是服务设计文档，展示项目不是边写边凑。
5. `docs/engineering/04-backend-internship-study-roadmap.md`、`05-architecture-and-core-flows.md`、`06-interview-and-resume-guide.md` 是面向后端实习项目复用的讲解材料。
6. `docs/assets/showcase/` 保存 README 顶部展示图、H5 GIF 和页面截图。

### 2.2 再展示本地运行

启动依赖：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

启动后端：

```bash
cd backend
mvn -q -DskipTests compile
```

再分别启动 member、content、social、order、gateway。详细命令见：

```text
docs/engineering/02-local-main-chain-runbook.md
```

### 2.3 然后跑自动冒烟

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-main-chain.ps1 -GatewayBaseUrl http://127.0.0.1:8080
```

这一步可以证明：

1. 网关可用。
2. member/content/social 可协作。
3. MySQL、MinIO、counter 聚合可用。
4. 注册、上传、发笔记、改资料、查主页都是真实链路。

### 2.4 最后展示移动端

```bash
cd mobile
npm run dev:h5
```

推荐展示页面：

1. 登录/注册。
2. 首页推荐和关注 tab。
3. 发布页。
4. 笔记详情。
5. 我的页和编辑主页。
6. 通知中心。
7. 私信列表和聊天页。
8. 神券页。
9. 排行榜页。

## 3. 推荐讲解亮点

### 3.0 README 第一屏

当前 README 顶部已经补充：

1. 技术栈 badge。
2. `docs/assets/showcase/hero.png` 项目展示图。
3. H5 页面 GIF 和排行榜截图。
4. 更适合 GitHub 浏览的功能矩阵。
5. MIT License 入口。

展示时先用 README 第一屏说明项目定位，再进入契约和源码，会比直接打开代码目录更容易让读者建立兴趣。

### 3.1 契约优先

接口字段、错误码、DDL、Redis Key、MQ 事件和权限矩阵都在 `docs/contracts/` 里维护。实现时后端、移动端和脚本都按这些契约对齐。

### 3.2 真实工程边界

虽然是个人项目，但没有把所有代码堆在一个应用里。当前后端拆成：

1. gateway-app
2. member-app
3. content-app
4. social-app
5. order-app

逻辑服务边界仍保留在包结构、schema、接口和事件里。

### 3.3 事件驱动基础

项目已经接入 RocketMQ producer/consumer、outbox dispatcher、消费幂等记录和多个业务事件流。counter、feed、rank、notification、push 和 order 都已经有事件链路。

### 3.4 移动端不是纯静态壳

H5 页面接入了真实 API：

1. 登录态和 Token 刷新。
2. 文件直传。
3. 笔记发布和详情。
4. 关注 Feed。
5. 通知未读。
6. WebSocket realtime。
7. IM 单聊。
8. 神券订单。
9. 排行榜。

### 3.5 适合后端实习项目复盘

项目已经补充了面向学习和面试的中文材料：

1. `README.zh-CN.md`：中文项目入口。
2. `docs/engineering/04-backend-internship-study-roadmap.md`：按模块学习路线。
3. `docs/engineering/05-architecture-and-core-flows.md`：架构和核心流程。
4. `docs/engineering/06-interview-and-resume-guide.md`：简历写法和面试讲法。
5. `docs/engineering/07-module-design-overview.md`：各功能模块的具体架构、流程和方案选型。

展示时可以强调：这个项目不是只给自己看的代码仓库，而是别人也可以顺着文档学习、运行、复盘和改造的后端实习项目参考。

## 4. 当前不建议继续扩的方向

为了让项目像一个完成作品，而不是永远在施工，当前不建议继续优先扩：

1. 推荐系统。
2. 全文搜索。
3. 真实支付渠道。
4. 真实厂商 Push。
5. IM 群聊和富媒体。
6. 大型运营后台。

这些都可以在 README 中作为明确 non-goals 或 future work。

## 5. 发布到 GitHub 前检查

建议执行：

```bash
git status --short --branch
```

确认没有提交：

1. `.m2/`
2. `out/`
3. `target/`
4. `node_modules/`
5. `unpackage/`
6. `.env`
7. 日志和临时文件

建议验证：

```bash
cd backend
mvn -q -DskipTests compile
```

```bash
cd mobile
npm run typecheck
npm run build:h5
```

如果本地服务正在运行，再跑：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-main-chain.ps1 -GatewayBaseUrl http://127.0.0.1:8080
```
