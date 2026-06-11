# BlueNote 本地主链路联调手册

版本：v0.1  
状态：第一条主链路与已落地社交接口最小联调清单
更新时间：2026-06-11

## 1. 目标

本文用于在本地跑通第一条主链路，并顺手验证当前已经落地的 relation/comment 社交接口：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情
```

本文只记录当前 local 开发环境的最小步骤。接口字段和错误码仍以 `docs/contracts/` 为准。

## 2. 前置条件

1. JDK 21 已启用。
2. Maven 可用。
3. Node.js 和 npm 可用。
4. Docker Compose 可用。
5. 3306、6379、8848、9000、9001、9876、10911 未被其他服务占用，或已通过环境变量改端口。

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

## 5. 启动后端应用

建议开四个终端，按顺序启动：

```bash
cd backend
mvn -pl bluenote-member-app spring-boot:run
```

```bash
cd backend
mvn -pl bluenote-content-app spring-boot:run
```

```bash
cd backend
mvn -pl bluenote-social-app spring-boot:run
```

```bash
cd backend
mvn -pl bluenote-gateway-app spring-boot:run
```

应用端口：

| 应用 | 端口 |
|---|---:|
| `bluenote-member-app` | 8081 |
| `bluenote-content-app` | 8082 |
| `bluenote-social-app` | 8083 |
| `bluenote-gateway-app` | 8080 |

健康检查：

```text
http://127.0.0.1:8081/internal/member/probe
http://127.0.0.1:8082/internal/content/probe
http://127.0.0.1:8083/api/social/probe
http://127.0.0.1:8080/internal/gateway/probe
```

## 6. 启动移动端 H5

```bash
cd mobile
npm run dev:h5
```

默认 H5 地址：

```text
http://127.0.0.1:5173
```

Vite dev server 默认把 `/api` 代理到：

```text
http://127.0.0.1:8080
```

如需覆盖网关地址：

```bash
VITE_BLUENOTE_GATEWAY=http://127.0.0.1:8080 npm run dev:h5
```

PowerShell 写法：

```powershell
$env:VITE_BLUENOTE_GATEWAY='http://127.0.0.1:8080'
npm run dev:h5
```

## 7. 主链路验收步骤

### 7.1 注册或登录

在 H5 登录页完成注册或登录。

验收点：

1. 登录成功后进入首页。
2. `mobile/src/stores/auth.ts` 能保存 access token、refresh token 和用户资料。
3. 访问 `/api/users/me` 成功。

### 7.2 上传图片并发布笔记

在发布页选择图片，填写标题和正文，点击发布。

验收点：

1. 移动端先调用 `/api/files/upload-token`。
2. 图片通过预签名 URL 上传到 MinIO。
3. 移动端调用 `/api/files/{fileId}/confirm`。
4. 移动端调用 `/api/notes` 发布笔记。
5. 发布成功后跳转笔记详情。

### 7.3 查看详情与我的页面

验收点：

1. `/api/notes/{noteId}` 返回作者、媒体、标题、正文、计数结构。
2. 笔记详情页图片、作者和底部操作区显示正常。
3. `/api/notes/me` 能在我的页面展示已发布笔记。
4. `/api/users/{userId}/home` 能返回主页头部结构。

### 7.4 验证关注和评论接口

在已登录状态下，可以继续验证当前第二条主链路的最小接口：

1. `POST /api/relations/following/{followeeId}` 能关注用户。
2. `DELETE /api/relations/following/{followeeId}` 能取消关注。
3. `POST /api/comments/notes/{noteId}` 能发布一级评论。
4. `POST /api/comments/{commentId}/replies` 能回复评论。
5. `GET /api/comments/notes/{noteId}` 能查询一级评论。
6. `GET /api/comments/{rootCommentId}/replies` 能查询回复。
7. `POST /api/comments/{commentId}/like` 和 `DELETE /api/comments/{commentId}/like` 能切换评论点赞状态。

如果 MySQL 是旧数据卷，新增的 `V005__relation.sql`、`V006__comment.sql` 可能不会自动执行，需要手动执行 SQL 或重建本地数据卷。

## 8. 常见问题

### 8.1 MySQL 表不存在

原因通常是 Docker volume 已存在，初始化 SQL 没有重新执行。

处理方式：

1. 确认本地数据不需要保留。
2. 停止 Compose。
3. 清理对应 Docker volume。
4. 重新启动 Compose。

### 8.2 上传图片失败

优先检查：

1. MinIO 是否在 `9000` 端口可访问。
2. `bluenote-files` bucket 是否已创建。
3. `BLUENOTE_MINIO_PUBLIC_ENDPOINT` 是否对浏览器可访问。
4. 浏览器控制台是否有跨域或网络失败。

### 8.3 登录后接口仍是未认证

优先检查：

1. Gateway 是否启动在 `8080`。
2. H5 dev server 是否代理 `/api` 到 `8080`。
3. member 和 gateway 的 `BLUENOTE_ACCESS_TOKEN_SECRET` 是否一致。
4. 请求是否携带 `Authorization: Bearer ...`。

### 8.4 主页计数一直是 0

这是当前阶段已知限制。关注关系已落库，但用户主页头部还没有接入 counter 聚合。

## 9. 当前未覆盖

以下能力不属于第一条主链路当前验收范围：

1. 笔记点赞、收藏写操作。
2. Feed 投递。
3. 通知。
4. IM。
5. 订单。
6. RocketMQ outbox dispatcher 和消费者幂等闭环。
