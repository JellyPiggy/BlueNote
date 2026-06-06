# BlueNote Backend

BlueNote 后端采用 Java 21、Spring Boot 3.5.x、Spring Cloud 2025.0.x 和 Maven 多模块结构。

## 模块

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

当前骨架优先支持第一条主链路：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情
```

## 当前实现状态

`bluenote-member-app` 已提供 auth/user 的第一批契约接口骨架：

| 范围 | 路径 |
|---|---|
| auth 外部接口 | `/api/auth/register`、`/api/auth/login`、`/api/auth/token/refresh`、`/api/auth/logout`、`/api/auth/password/change` |
| user 外部接口 | `/api/users/me`、`/api/users/me/profile`、`/api/users/{userId}/public`、`/api/users/{userId}/home` |
| user 内部接口 | `/internal/users/register-profile`、`/internal/users/batch-summary`、`/internal/users/status-check` |

这些接口当前用于固定 Controller、DTO、统一响应、参数校验和 OpenAPI 输出。业务实现仍是内存占位，尚未接入 MySQL、密码哈希、JWT、Refresh Token 轮换、outbox 和真实内部服务认证。

## 本地要求

正式构建需要：

1. JDK 21。
2. Maven，建议 3.6.3 或更高版本。
3. 先按 `deploy/compose/README.md` 启动本地依赖。

## 校验

```bash
mvn -q -DskipTests validate
mvn -q -DskipTests compile
```

当前本机如果仍是 JDK 17，正式 `compile` 会提示不支持 release 21。不要把项目降到 Java 17；应切换到 JDK 21。

## 应用端口

| 应用 | 端口 | Probe |
|---|---:|---|
| `bluenote-gateway-app` | 8080 | `/internal/gateway/probe` |
| `bluenote-member-app` | 8081 | `/internal/member/probe` |
| `bluenote-content-app` | 8082 | `/internal/content/probe` |

OpenAPI UI：

```text
http://localhost:{port}/swagger-ui.html
```

Actuator health：

```text
http://localhost:{port}/actuator/health
```
