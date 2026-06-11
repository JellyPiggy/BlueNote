# BlueNote 当前工程状态

版本：v0.2
状态：第一条主链路完成，第二条社交链路起步
更新时间：2026-06-11
当前分支：`codex/social-chain-foundation`
当前基线提交：以本分支最新提交为准

## 1. 文档用途

本文记录 BlueNote 当前已经完成的工程资产、可运行范围、待完成事项和已知风险，用于后续继续开发时快速接上上下文。

本文不替代：

1. `AGENTS.md`：长期协作规则和边界。
2. `docs/contracts/`：API、DDL、Redis、MQ、安全权限契约。
3. `方案/`：架构方案、服务设计和 ADR。

如果后续修改了接口字段、错误码、DDL、Redis Key 或 MQ 事件，必须优先同步 `docs/contracts/`，不能只更新本文。

## 2. 当前总体状态

BlueNote 已经从纯方案阶段推进到“第一条主链路可联调基线”阶段，并开始落地第二条社交链路。

当前主链路目标：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情
```

当前完成度：

| 范围 | 状态 | 说明 |
|---|---|---|
| 契约 | 已建立第一、第二条主链路基线 | API、错误码、DDL、Redis、MQ、安全权限文档已进入 `docs/contracts/` |
| 本地依赖 | 已建立 local Compose | MySQL、Redis、Nacos、RocketMQ、MinIO 使用常用本地端口 |
| 后端基础 | 已搭建 Maven 多模块 | common、gateway、member、content、social 已有可执行结构 |
| member-app | 已从内存占位改为 MySQL | auth/user 注册、登录、Token、用户资料等主链路接口已落库 |
| gateway-app | 已补 JWT 校验 | 网关校验 Access Token 并注入用户上下文 Header |
| content-app | 已从占位接口接入 MySQL/MinIO | file/note/comment 可申请上传凭证、发布笔记、查询详情、发布评论 |
| social-app | 已新增 relation 最小纵切面 | 关注、取关、关注/粉丝列表、关注状态、relation outbox 已落库 |
| mobile | 已接入 H5 主链路 | 登录、首页、发布、详情、我的页已接真实 API |
| UI | 已完成一轮移动端视觉优化 | 信息流、笔记详情、个人页按移动端社区产品风格重做 |

## 3. 已完成内容

### 3.1 契约与文档

已完成第一、第二条主链路契约基线：

```text
docs/contracts/
  README.md
  api/
    00-common.md
    01-error-codes.md
    02-auth-api.md
    03-user-api.md
    04-file-api.md
    05-note-api.md
    06-relation-api.md
    07-comment-api.md
    08-counter-api.md
    09-feed-api.md
    10-notification-api.md
  db/
    01-main-chain-schema.md
    02-social-chain-schema.md
  redis/
    01-main-chain-keys.md
    02-social-chain-keys.md
  mq/
    01-main-chain-events.md
    02-social-chain-events.md
  security/
    01-permission-matrix.md
    02-social-chain-permission-matrix.md
```

已补工程规范：

```text
docs/engineering/01-engineering-structure-and-coding-guidelines.md
```

本文用于补充“当前做到哪里”的交接视图。

### 3.2 数据库与本地依赖

已完成第一批 DDL：

```text
backend/sql/
  V001__auth.sql
  V002__user.sql
  V003__file.sql
  V004__note.sql
  V005__relation.sql
  V006__comment.sql
```

本地依赖已配置：

| 组件 | 端口 | 说明 |
|---|---:|---|
| MySQL | 3306 | 初始化挂载 `backend/sql/` |
| Redis | 6379 | 本地开发密码见 `deploy/env/local.env.example` |
| Nacos | 8848 | local 注册中心和配置中心 |
| RocketMQ NameServer | 9876 | local MQ |
| RocketMQ Broker | 10911 | local MQ Broker |
| MinIO API | 9000 | 对象存储 API |
| MinIO Console | 9001 | 对象存储控制台 |

启动命令：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

注意：MySQL Docker 初始化脚本只会在数据卷首次创建时自动执行。已有旧数据卷时，如果 DDL 有变化，需要手动执行 SQL 或重建本地数据卷。

### 3.3 后端工程

后端当前模块：

```text
backend/
  bluenote-dependencies/
  bluenote-common/
  bluenote-gateway-app/
  bluenote-member-app/
  bluenote-content-app/
  bluenote-social-app/
  sql/
```

已完成基础能力：

1. Java 21 + Spring Boot 3.5.x + Spring Cloud 2025.0.x Maven 多模块。
2. 统一响应 `ApiResponse`、错误码、业务异常、全局异常处理。
3. traceId 和基础 Web 上下文。
4. common security 中的 JWT Access Token 签发与解析。
5. common mybatis、redis、mq、observability 基础包结构。
6. OpenAPI / Swagger UI 基础接入。

### 3.4 Gateway

`bluenote-gateway-app` 已补：

1. JWT Access Token 校验。
2. 公开路径放行。
3. 鉴权失败统一响应。
4. 用户上下文 Header 注入：
   - `X-User-Id`
   - `X-Device-Id`
   - `X-Session-Id`
   - `X-Trace-Id`
5. member/content/social 路由基础配置。
6. `/internal/gateway/probe` 探针。

### 3.5 Member App

`bluenote-member-app` 当前包含 auth/user 第一条主链路能力。

auth 已完成：

1. 注册：`POST /api/auth/register`
2. 登录：`POST /api/auth/login`
3. 刷新 Token：`POST /api/auth/token/refresh`
4. 登出：`POST /api/auth/logout`
5. 修改密码：`POST /api/auth/password/change`
6. 密码 BCrypt 哈希。
7. Refresh Token 哈希存储和轮换。
8. 设备会话表、登录审计表。
9. 注册时写 auth outbox，并创建默认用户资料。

user 已完成：

1. 当前用户资料：`GET /api/users/me`
2. 修改资料：`PUT /api/users/me/profile`
3. 公开资料：`GET /api/users/{userId}/public`
4. 用户主页头部：`GET /api/users/{userId}/home`
5. 内部注册资料：`POST /internal/users/register-profile`
6. 内部批量用户摘要：`POST /internal/users/batch-summary`
7. 内部用户状态校验：`POST /internal/users/status-check`
8. 用户资料修改审计和 user outbox。

限制：`/api/users/{userId}/home` 的计数结构已对齐契约，但关注数、粉丝数、获赞数仍是占位值，后续需要 counter/relation/social 链路补齐。

### 3.6 Content App

`bluenote-content-app` 当前包含 file/note 第一条主链路能力。

file 已完成：

1. 上传凭证：`POST /api/files/upload-token`
2. 上传确认：`POST /api/files/{fileId}/confirm`
3. 访问地址：`GET /api/files/{fileId}/access-url`
4. 内部文件校验：`POST /internal/files/validate`
5. 内部批量文件校验：`POST /internal/files/batch-validate`
6. 内部文件绑定：`POST /internal/files/bind`
7. 内部批量文件绑定：`POST /internal/files/batch-bind`
8. 文件元数据、上传会话、绑定关系落 MySQL。
9. MinIO 预签名上传和访问 URL。
10. file outbox 写入。

note 已完成：

1. 保存草稿：`POST /api/notes/drafts`
2. 发布笔记：`POST /api/notes`
3. 草稿发布：`POST /api/notes/{noteId}/publish`
4. 删除笔记：`DELETE /api/notes/{noteId}`
5. 笔记详情：`GET /api/notes/{noteId}`
6. 用户笔记列表：`GET /api/notes/users/{userId}`
7. 我的笔记列表：`GET /api/notes/me`
8. 内部批量笔记摘要：`POST /internal/notes/batch-summary`
9. 内部评论前校验：`POST /internal/notes/comment-check`
10. 笔记、媒体、版本、话题、幂等请求、互动查询等表结构已接入。
11. 发布和删除写 note outbox，例如 `NotePublished`、`NoteDeleted`。

comment 已完成：

1. 发布一级评论：`POST /api/comments/notes/{noteId}`
2. 回复评论：`POST /api/comments/{commentId}/replies`
3. 删除评论：`DELETE /api/comments/{commentId}`
4. 笔记评论列表：`GET /api/comments/notes/{noteId}`
5. 回复列表：`GET /api/comments/{rootCommentId}/replies`
6. 评论点赞：`POST /api/comments/{commentId}/like`
7. 取消评论点赞：`DELETE /api/comments/{commentId}/like`
8. 我的评论：`GET /api/comments/me`
9. 内部批量评论摘要：`POST /internal/comments/batch-summary`
10. 内部评论计数来源：`POST /internal/comments/counter-source`
11. 评论事实、个人评论读模型、评论正文、评论点赞、幂等、操作日志、outbox 已落 MySQL。

限制：笔记点赞、收藏写接口和完整计数链路还没有落地；当前详情页可以展示计数字段和 viewerAction 结构，但笔记互动行为仍是下一阶段任务。

### 3.7 移动端

移动端技术栈：

1. uni-app
2. Vue 3
3. TypeScript
4. Pinia
5. `uni.request` 封装
6. H5 已作为当前主要验收目标

已完成目录：

```text
mobile/src/
  api/
  components/
  pages/
    home/
    login/
    note/detail/
    profile/
    publish/
  stores/
  utils/
```

已完成能力：

1. API 请求统一封装在 `mobile/src/api/`。
2. `code/message/data/traceId` 统一解析。
3. `20001` Token 过期时自动刷新并重试。
4. Pinia 管理登录态、Token、当前用户资料。
5. 注册/登录页面。
6. 首页信息流。
7. 发布笔记：图片选择、上传、确认、发布。
8. 笔记详情：图片轮播、作者、正文、计数展示、底部操作区。
9. 我的页面：封面背景、头像、账号信息、关注/粉丝/获赞、笔记/收藏/赞过 tab、账号侧边菜单。
10. 笔记详情页评论区：评论列表、回复列表、发布评论、回复、评论点赞、删除自己的评论。
11. H5 真实浏览器验收已做过多轮视觉检查。

当前 UI 风格已经从早期工程占位调整为移动端社区产品风格，但不使用小红书品牌元素。

## 4. 最近提交脉络

当前分支近期关键提交：

| 提交 | 内容 |
|---|---|
| `68d9da1` | 第一条主链路契约 |
| `e986667` | 后端基础工程 |
| `f74cea6` | 本地 MySQL/Redis 改回常用端口 |
| `70f2eee` | member auth/user MySQL 落库 |
| `90e2d40` | gateway JWT 鉴权 |
| `dd615e2` | content file/note MySQL/MinIO 落库 |
| `547afc5` | 移动端主链路 |
| `8d5e632` | 移动端整体视觉优化 |
| `bc5cfcd` | 笔记详情阅读布局优化 |
| `56a950f` | 详情页关注按钮文字居中 |
| `5feeae7` | 我的页封面区重做 |
| `36da5ed` | 我的页账号侧边菜单 |
| `2774283` | 我的页顶部贴边、统计和 tab 重排 |
| `7c35f3f` | 第二条社交链路契约 |
| `d1a86fc` | relation 后端最小纵切面 |

## 5. 当前运行与验证方式

本地依赖：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

后端常用检查：

```bash
cd backend
mvn -q -DskipTests compile
```

移动端常用检查：

```bash
cd mobile
npm run typecheck
npm run build:h5
npm run dev:h5
```

当前已知验证记录：

1. 移动端 `npm run typecheck` 通过。
2. 移动端 `npm run build:h5` 通过。
3. H5 页面通过浏览器验收过首页、发布、详情、我的页关键视觉。
4. `build:h5` 当前会输出 Dart Sass legacy JS API deprecation warning，暂不影响构建。
5. 后端 `mvn compile` 已通过，覆盖 gateway/member/content/social 模块。

本文更新时没有重新执行端到端接口联调。后续继续联调任务前，建议启动服务后补一次网关到 member/content/social 的接口验证。

## 6. 待完成事项

### 6.1 第一条主链路收尾

优先级最高：

1. 补一次完整本地端到端验收：网关 -> member/content -> MySQL/MinIO。
2. 更新 `backend/README.md`，它当前仍有早期“内存占位”的过时描述。
3. 整理 local 启动顺序和常见问题到 `docs/engineering/` 或 `deploy/compose/README.md`。
4. 为 auth/user/file/note 补最小接口测试或集成测试。
5. 补 OpenAPI 与 `docs/contracts/api/` 的字段核对记录。
6. 确认 H5 真机或手机浏览器适配，尤其是安全区、底部 tab、图片上传。

### 6.2 移动端体验继续完善

下一批移动端任务：

1. 编辑主页真实页面和 `PUT /api/users/me/profile` 联调。
2. 我的页“收藏”和“赞过”tab 接真实数据。
3. 继续补笔记点赞、收藏写操作。
4. 首页从“我的笔记”过渡到更真实的推荐/发现数据源。
5. 图片上传失败重试、上传进度、重复发布保护再打磨。
6. 空态、错误态、弱网态和 token 过期后的提示继续统一。

### 6.3 第二条社交链路

下一条主链路：

```text
关注用户 -> 发布笔记事件 -> Feed 投递 -> 关注页拉取 -> 点赞/收藏/评论 -> 计数更新 -> 通知生成
```

待落地：

1. 把用户主页头部计数接到 relation/counter。
2. counter 服务：关注数、粉丝数、获赞数、点赞数、收藏数、评论数。
3. note 点赞/取消点赞、收藏/取消收藏接口。
4. feed 服务：关注页 Feed 拉取和发布事件投递。
5. notification 服务：互动通知和未读数。
6. RocketMQ outbox dispatcher 和消费幂等记录。
7. Redis 计数、Feed key 的真实读写和重建策略。

### 6.4 后续链路

第三条实时链路待完成：

```text
设备注册 -> WebSocket 连接 -> IM 发送消息 -> 消息入库 -> 在线下发 -> 离线 Push 请求
```

第四条交易链路待完成：

```text
活动预热 -> 秒杀令牌 -> Redis Lua 预扣库存 -> RocketMQ 异步下单 -> 订单查询 -> 支付/发券 -> 超时关单
```

这些链路目前只在方案和契约方向上有设计基础，工程代码还没有开始落地。

## 7. 已知风险与注意事项

1. Outbox 当前主要是写库记录，还没有完整 MQ dispatcher、重试、死信和消费幂等闭环。
2. 用户主页计数目前未接真实 counter/relation 数据。
3. 笔记点赞、收藏仍是占位交互，移动端评论已经接入本轮新增的评论后端接口。
4. 自动化测试基本还没建立，后续改动风险会越来越高。
5. social-app 当前只完成 relation 最小纵切面，counter/feed/notification 仍未接入。
6. MySQL 初始化依赖 Docker 首次建卷行为，重建本地环境时要注意数据卷状态。
7. 当前工作区存在未归属本轮任务的改动或临时目录时，不要误提交。
8. 正式部署、备份恢复、监控告警、Nginx/Caddy HTTPS 还没有落地。

## 8. 当前工作区提示

创建本文前，工作区仍存在以下非本文任务内容：

```text
M  方案/AGENTS.md
?? .m2/
?? out/
```

这些内容不属于当前状态文档任务。后续提交时应继续避免误带入，除非确认它们确实需要进入版本库。

## 9. 更新规则

建议在以下场景更新本文：

1. 一个主链路阶段完成。
2. 新增或移除后端应用、移动端页面、部署组件。
3. 完成一次端到端联调。
4. 发现重要风险或临时约束。
5. 进入第二条、第三条、第四条主链路前。

建议不要在本文记录过细的日常流水账。已经稳定下来的规则放 `AGENTS.md` 或 `docs/engineering/` 规范文档；接口、DDL、Redis、MQ、安全权限变化放 `docs/contracts/`。
