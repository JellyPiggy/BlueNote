# BlueNote 本地主链路联调手册

版本：v0.2
状态：第一条主链路与资料编辑闭环可重复本地验收
更新时间：2026-06-13

## 1. 目标

本文用于在本地跑通第一条主链路和资料编辑闭环：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情 -> 上传头像/封面 -> 修改资料 -> 查看主页
```

接口字段和错误码以 `docs/contracts/` 为准；本手册只记录 local 开发环境的最小步骤。

## 2. 前置条件

1. JDK 21 已启用。
2. Maven 可用。
3. Node.js 和 npm 可用。
4. Docker Compose 可用。
5. 3306、6379、8848、9000、9001、9876、10911、8080 到 8084、5173 未被其他服务占用，或已通过环境变量改端口。

## 3. 启动本地依赖

在仓库根目录执行：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

本地默认端口：

| 组件 | 端口 |
|---|---:|
| MySQL | 3306 |
| Redis | 6379 |
| Nacos | 8848 |
| RocketMQ NameServer | 9876 |
| RocketMQ Broker | 10911 |
| MinIO API | 9000 |
| MinIO Console | 9001 |

如果 MySQL 已经有旧数据卷，`backend/sql/` 下的初始化脚本不会再次自动执行。需要重新初始化时，先确认本地数据可以删除，再清理 Docker volume。

## 4. 编译后端

```bash
cd backend
mvn -q -DskipTests compile
```

如果提示 Java release 21 不支持，说明当前 shell 没有使用 JDK 21。

旧 Maven 环境下优先使用 compile。`mvn install` 可能受 Surefire/Maven 版本影响；只有运行时确实加载到旧 common 模块时，再考虑安装本地模块。

## 5. 启动后端应用

建议开五个终端，按顺序启动：

```powershell
cd backend/bluenote-member-app
mvn -q -DskipTests spring-boot:run
```

```powershell
cd backend/bluenote-content-app
mvn -q -DskipTests spring-boot:run
```

```powershell
cd backend/bluenote-social-app
mvn -q -DskipTests spring-boot:run
```

```powershell
cd backend/bluenote-order-app
mvn -q -DskipTests spring-boot:run
```

```powershell
cd backend/bluenote-gateway-app
mvn -q -DskipTests spring-boot:run
```

第一条主链路最小验证只强依赖 member、content、social、gateway；order 可以稍后启动。social 用于用户主页计数和 note 详情 counter 降级路径验证。

应用端口：

| 应用 | 端口 |
|---|---:|
| `bluenote-gateway-app` | 8080 |
| `bluenote-member-app` | 8081 |
| `bluenote-content-app` | 8082 |
| `bluenote-social-app` | 8083 |
| `bluenote-order-app` | 8084 |

健康检查：

```text
http://127.0.0.1:8080/internal/gateway/probe
http://127.0.0.1:8081/internal/member/probe
http://127.0.0.1:8082/internal/content/probe
http://127.0.0.1:8083/api/social/probe
http://127.0.0.1:8084/internal/order/probe
```

## 6. 自动冒烟验收

仓库提供第一条主链路脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-main-chain.ps1 -GatewayBaseUrl http://127.0.0.1:8080
```

脚本会自动完成：

1. 注册临时 H5 用户。
2. 获取当前用户资料。
3. 申请 `NOTE_IMAGE` 上传凭证，直传 MinIO，并确认上传。
4. 发布公开笔记。
5. 查询笔记详情，核对媒体、计数和当前用户互动状态结构。
6. 申请 `USER_AVATAR`、`USER_HOME_COVER` 上传凭证，直传并确认。
7. 修改当前用户资料。
8. 查询当前用户资料和用户主页头部。

输出 JSON 中会包含本次临时 `username`、`userId`、`noteId` 和文件 ID，便于排查。

## 7. 启动移动端 H5

```bash
cd mobile
npm run dev:h5
```

默认 H5 地址：

```text
http://127.0.0.1:5173
```

本地开发有两种访问方式：

1. 默认请求相对路径 `/api/**`，由 Vite dev server 把 `/api` 代理到 `VITE_BLUENOTE_GATEWAY`，默认 `http://127.0.0.1:8080`。
2. 如需让移动端请求层直接访问网关，可设置 `VITE_API_BASE_URL=http://127.0.0.1:8080`。

PowerShell 写法：

```powershell
$env:VITE_BLUENOTE_GATEWAY='http://127.0.0.1:8080'
npm run dev:h5
```

或：

```powershell
$env:VITE_API_BASE_URL='http://127.0.0.1:8080'
npm run dev:h5
```

## 8. 手工验收步骤

### 8.1 注册或登录

在 H5 登录页完成注册或登录。

验收点：

1. 登录成功后进入首页。
2. 本地保存 access token、refresh token 和用户资料。
3. `GET /api/users/me` 成功。

### 8.2 上传图片并发布笔记

在发布页选择图片，填写标题和正文，点击发布。

验收点：

1. 移动端先调用 `/api/files/upload-token`。
2. 图片通过预签名 URL 上传到 MinIO。
3. 移动端调用 `/api/files/{fileId}/confirm`。
4. 移动端调用 `/api/notes` 发布笔记。
5. 发布成功后跳转笔记详情。

### 8.3 查看详情、我的页面和主页

验收点：

1. `/api/notes/{noteId}` 返回作者、媒体、标题、正文、计数结构和 `viewerAction`。
2. 笔记详情页图片、作者和底部操作区显示正常。
3. `/api/notes/me` 能在我的页面展示已发布笔记。
4. `/api/users/{userId}/home` 能返回主页头部结构，并通过 counter 聚合返回关注数、粉丝数、作品数和获赞数。

### 8.4 修改资料、头像和主页封面

在我的页进入编辑主页。

验收点：

1. 头像走 `USER_AVATAR` 上传场景。
2. 主页封面走 `USER_HOME_COVER` 上传场景。
3. `PUT /api/users/me/profile` 成功后 `profileVersion` 增加。
4. `/api/users/me` 返回真实 `avatarFileId/avatarUrl/homeCoverFileId/homeCoverUrl`。
5. `/api/users/{userId}/home` 返回更新后的用户摘要。

## 9. 常见问题

### 9.1 MySQL 表不存在

原因通常是 Docker volume 已存在，初始化 SQL 没有重新执行。

处理方式：

1. 确认本地数据不需要保留。
2. 停止 Compose。
3. 清理对应 Docker volume。
4. 重新启动 Compose。

### 9.2 上传图片失败

优先检查：

1. MinIO 是否在 `9000` 端口可访问。
2. `bluenote-files` bucket 是否已创建。
3. `BLUENOTE_MINIO_PUBLIC_ENDPOINT` 是否对浏览器可访问。
4. 浏览器控制台是否有跨域或网络失败。

### 9.3 登录后接口仍是未认证

优先检查：

1. Gateway 是否启动在 `8080`。
2. H5 dev server 是否代理 `/api` 到 `8080`，或 `VITE_API_BASE_URL` 是否直连到 `8080`。
3. member 和 gateway 的 `BLUENOTE_ACCESS_TOKEN_SECRET` 是否一致。
4. 请求是否携带 `Authorization: Bearer ...`。

### 9.4 主页计数一直是 0 或降级

优先检查：

1. social-app 是否启动在 `8083`。
2. member-app 的 `BLUENOTE_COUNTER_INTERNAL_URI` 是否指向 social-app。
3. content-app 是否启动在 `8082`，因为作品数、获赞数和评论数会从 content 来源接口聚合。
4. MySQL、Redis 是否可连接；来源接口失败时用户主页会返回 `degraded=true` 并降级为 0。

### 9.5 运行时报 `NoSuchFieldError: COUNTER_*`

说明本地运行进程加载到了旧的 common-core。优先重新编译当前源码并重启应用；必要时再让 common 模块更新到本地 Maven 仓库。

## 10. 当前未覆盖

以下能力不属于第一条主链路冒烟脚本范围：

1. 评论、关注、Feed、通知、Push、IM、订单和排行榜的完整联调。
2. RocketMQ 死信告警、人工重放审计和异常补偿路径。
3. H5 真机或手机浏览器的安全区、底部 tab、图片选择和上传进度体验。
4. OpenAPI 与契约文档的自动 diff。
