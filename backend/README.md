# BlueNote Backend

BlueNote 后端采用 Java 21、Spring Boot 3.5.x、Spring Cloud 2025.0.x 和 Maven 多模块结构。

当前后端已经支撑第一条主链路：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情
```

## 1. 模块

```text
backend/
  bluenote-dependencies/
  bluenote-common/
    bluenote-common-core/
    bluenote-common-web/
    bluenote-common-security/
    bluenote-common-mybatis/
    bluenote-common-redis/
    bluenote-common-mq/
    bluenote-common-observability/
  bluenote-gateway-app/
  bluenote-member-app/
  bluenote-content-app/
  sql/
```

物理应用：

| 应用 | 端口 | 当前职责 |
|---|---:|---|
| `bluenote-gateway-app` | 8080 | 网关、JWT 校验、路由、用户上下文 Header 注入 |
| `bluenote-member-app` | 8081 | auth、user |
| `bluenote-content-app` | 8082 | file、note |

## 2. 当前实现状态

### 2.1 Gateway

`bluenote-gateway-app` 已实现：

1. Access Token JWT 校验。
2. 公开接口放行。
3. 认证失败统一响应。
4. 向下游注入：
   - `X-User-Id`
   - `X-Device-Id`
   - `X-Session-Id`
   - `X-Trace-Id`
5. member/content 路由。

### 2.2 Member App

`bluenote-member-app` 已从内存占位改为 MySQL 落库。

auth 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/auth/register` | 注册 |
| `POST /api/auth/login` | 登录 |
| `POST /api/auth/token/refresh` | Refresh Token 轮换 |
| `POST /api/auth/logout` | 登出 |
| `POST /api/auth/password/change` | 修改密码 |

user 外部接口：

| 接口 | 说明 |
|---|---|
| `GET /api/users/me` | 当前用户资料 |
| `PUT /api/users/me/profile` | 修改当前用户资料 |
| `GET /api/users/{userId}/public` | 用户公开资料 |
| `GET /api/users/{userId}/home` | 用户主页头部 |

user 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/users/register-profile` | 注册后创建默认用户资料 |
| `POST /internal/users/batch-summary` | 批量查询用户摘要 |
| `POST /internal/users/status-check` | 批量校验用户状态 |

已接入能力：

1. BCrypt 密码哈希。
2. Access Token 签发。
3. Refresh Token 哈希存储和轮换。
4. 设备会话。
5. 登录审计。
6. 用户资料审计。
7. auth/user outbox 写库。

限制：用户主页头部的关注数、粉丝数、获赞数当前仍是占位值，后续由 relation/counter/social 链路补齐。

### 2.3 Content App

`bluenote-content-app` 已从占位接口接入 MySQL 和 MinIO。

file 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/files/upload-token` | 申请预签名上传凭证 |
| `POST /api/files/{fileId}/confirm` | 确认上传完成 |
| `GET /api/files/{fileId}/access-url` | 获取文件访问地址 |

file 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/files/validate` | 校验文件 |
| `POST /internal/files/batch-validate` | 批量校验文件 |
| `POST /internal/files/bind` | 绑定文件 |
| `POST /internal/files/batch-bind` | 批量绑定文件 |

note 外部接口：

| 接口 | 说明 |
|---|---|
| `POST /api/notes/drafts` | 保存草稿 |
| `POST /api/notes` | 发布笔记 |
| `POST /api/notes/{noteId}/publish` | 草稿发布 |
| `DELETE /api/notes/{noteId}` | 删除笔记 |
| `GET /api/notes/{noteId}` | 笔记详情 |
| `GET /api/notes/users/{userId}` | 用户笔记列表 |
| `GET /api/notes/me` | 我的笔记列表 |

note 内部接口：

| 接口 | 说明 |
|---|---|
| `POST /internal/notes/batch-summary` | 批量笔记摘要 |
| `POST /internal/notes/comment-check` | 评论前校验 |

已接入能力：

1. MinIO 预签名 PUT 上传。
2. 文件元数据、上传会话、绑定关系落库。
3. 笔记、媒体、版本、话题、幂等请求落库。
4. 发布、删除写 note outbox。
5. 笔记详情聚合作者、媒体、计数结构和 viewerAction。

限制：点赞、收藏、评论的写接口和完整计数链路尚未实现。

## 3. 本地要求

1. JDK 21。
2. Maven，建议 3.6.3 或更高版本。
3. Docker Desktop 或可用 Docker Compose。
4. 先启动 local 依赖：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

## 4. 构建与运行

后端编译：

```bash
cd backend
mvn -q -DskipTests compile
```

如果单独运行某个应用时提示找不到本仓库内的 common 模块，可先安装本地模块：

```bash
mvn -q -DskipTests install
```

分别启动应用：

```bash
cd backend
mvn -pl bluenote-member-app spring-boot:run
mvn -pl bluenote-content-app spring-boot:run
mvn -pl bluenote-gateway-app spring-boot:run
```

建议启动顺序：

1. 本地依赖。
2. `bluenote-member-app`。
3. `bluenote-content-app`。
4. `bluenote-gateway-app`。
5. 移动端 H5。

## 5. 本地默认配置

本地默认值在各应用 `application.yml` 中提供，可通过环境变量覆盖。

| 配置 | 默认值 |
|---|---|
| MySQL root 密码 | `bluenote_root_local` |
| member 数据源 | `jdbc:mysql://127.0.0.1:3306/bluenote_auth` |
| content 数据源 | `jdbc:mysql://127.0.0.1:3306/bluenote_file` |
| MinIO endpoint | `http://127.0.0.1:9000` |
| MinIO bucket | `bluenote-files` |
| Gateway member URI | `http://127.0.0.1:8081` |
| Gateway content URI | `http://127.0.0.1:8082` |

注意：member/content 当前各自只配置一个 datasource URL，但 DDL 中按逻辑 schema 拆分为 `bluenote_auth`、`bluenote_user`、`bluenote_file`、`bluenote_note`。应用内 SQL 使用显式 schema 名访问本应用拥有的逻辑 schema。

## 6. 检查入口

Probe：

| 应用 | URL |
|---|---|
| gateway | `http://127.0.0.1:8080/internal/gateway/probe` |
| member | `http://127.0.0.1:8081/internal/member/probe` |
| content | `http://127.0.0.1:8082/internal/content/probe` |

Actuator health：

```text
http://127.0.0.1:{port}/actuator/health
```

OpenAPI UI：

```text
http://127.0.0.1:{port}/swagger-ui.html
```

## 7. 已知待办

1. 补后端自动化测试和接口集成测试。
2. 补 outbox dispatcher、RocketMQ 投递、消费幂等和重试闭环。
3. 补 relation/counter/feed/comment/notification 第二条社交链路。
4. 补点赞、收藏、评论写接口。
5. 补生产部署、备份恢复、监控告警配置。
