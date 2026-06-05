# 登录认证 API 契约

版本：v0.1  
状态：第一条主链路开发基线

移动端通过网关访问 `/api/auth/**`。

## 1. 数据模型

### 1.1 DeviceInfo

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `deviceId` | string | 是 | 移动端生成并持久化的设备 ID |
| `deviceName` | string / null | 否 | 设备名称，例如 `iPhone 15` |
| `platform` | string | 是 | `IOS` / `ANDROID` / `H5` |
| `appVersion` | string | 是 | App 版本 |

### 1.2 TokenPair

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `userId` | string | 是 | 用户 ID |
| `accessToken` | string | 是 | JWT Access Token |
| `refreshToken` | string | 是 | 高熵随机 Refresh Token |
| `accessTokenExpiresIn` | number | 是 | Access Token 剩余秒数，第一阶段 3600 |
| `refreshTokenExpiresIn` | number | 是 | Refresh Token 剩余秒数，第一阶段 2592000 |

## 2. 注册

```text
POST /api/auth/register
```

鉴权：不需要。

请求：

```json
{
  "username": "blue_note_user",
  "password": "password",
  "deviceId": "device-id",
  "deviceName": "iPhone 15",
  "platform": "IOS",
  "appVersion": "1.0.0"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "10001",
    "accessToken": "jwt",
    "refreshToken": "opaque-token",
    "accessTokenExpiresIn": 3600,
    "refreshTokenExpiresIn": 2592000
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `10001` | `PARAM_INVALID` |
| `10005` | `RATE_LIMITED` |
| `20008` | `PASSWORD_TOO_WEAK` |
| `20010` | `USERNAME_ALREADY_EXISTS` |
| `20011` | `DEVICE_ID_INVALID` |

后端要求：

1. `username` 数据库唯一约束兜底。
2. 密码只保存安全哈希，不打印明文日志。
3. 注册成功必须通过用户服务内部接口创建默认用户资料，并创建设备会话。
4. 注册成功写入 `UserRegistered` outbox 事件。

移动端要求：

1. 注册成功后直接进入登录态。
2. 保存 TokenPair、`userId` 和过期时间。
3. 注册成功后调用 `/api/users/me` 获取完整资料。

## 3. 登录

```text
POST /api/auth/login
```

鉴权：不需要。

请求：

```json
{
  "username": "blue_note_user",
  "password": "password",
  "deviceId": "device-id",
  "deviceName": "iPhone 15",
  "platform": "IOS",
  "appVersion": "1.0.0"
}
```

成功响应同注册。

可能错误码：

| code | reason |
|---|---|
| `10001` | `PARAM_INVALID` |
| `10005` | `RATE_LIMITED` |
| `20005` | `USERNAME_OR_PASSWORD_ERROR` |
| `20006` | `ACCOUNT_DISABLED` |
| `20011` | `DEVICE_ID_INVALID` |

后端要求：

1. 登录失败次数按用户名、IP、设备维度记录。
2. 登录成功后轮换当前设备会话的 Refresh Token。
3. 登录失败不返回用户名是否存在。

移动端要求：

1. 登录失败只展示通用用户名或密码错误。
2. 登录成功后调用 `/api/users/me`。

## 4. 刷新 Token

```text
POST /api/auth/token/refresh
```

鉴权：不需要 Access Token，必须提交 Refresh Token。

请求：

```json
{
  "refreshToken": "opaque-token",
  "deviceId": "device-id"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": "10001",
    "accessToken": "new-jwt",
    "refreshToken": "new-opaque-token",
    "accessTokenExpiresIn": 3600,
    "refreshTokenExpiresIn": 2592000
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `20003` | `REFRESH_TOKEN_EXPIRED` |
| `20004` | `REFRESH_TOKEN_INVALID` |
| `20006` | `ACCOUNT_DISABLED` |
| `20007` | `SESSION_REVOKED` |
| `20012` | `TOKEN_REFRESH_REPLAYED` |

后端要求：

1. Refresh Token 每次刷新必须轮换。
2. 服务端只保存 Refresh Token hash。
3. 旧 Refresh Token 再次使用视为风险事件，可撤销当前设备会话。

移动端要求：

1. 接口返回 `20001` 时可以自动调用刷新。
2. 并发刷新必须合并为一次，避免多个请求同时轮换 Refresh Token。
3. 刷新失败必须清理登录态并跳转登录。

## 5. 当前设备登出

```text
POST /api/auth/logout
```

鉴权：需要 `Authorization: Bearer {accessToken}`。

请求：

```json
{
  "refreshToken": "opaque-token"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "success": true
  },
  "traceId": "trace-id"
}
```

后端要求：

1. 撤销当前设备会话。
2. 当前 Access Token 的 `jti` 加入 Redis 黑名单直到过期。
3. 登出接口幂等。

移动端要求：

1. 用户主动登出后，无论服务端是否返回成功，都清理本地登录态。

## 6. 修改密码

```text
POST /api/auth/password/change
```

鉴权：需要。

请求：

```json
{
  "oldPassword": "old-password",
  "newPassword": "new-password"
}
```

成功响应：TokenPair。

可能错误码：

| code | reason |
|---|---|
| `20001` | `ACCESS_TOKEN_EXPIRED` |
| `20008` | `PASSWORD_TOO_WEAK` |
| `20009` | `OLD_PASSWORD_ERROR` |

后端要求：

1. 修改密码后提升 `passwordVersion`。
2. 当前设备返回新 TokenPair。
3. 后续可扩展为其他设备强制下线。

## 7. 内部接口占位

第一条主链路不强制依赖登录服务内部接口，避免登录服务成为每个请求瓶颈。

后续按需补充：

1. `POST /internal/auth/accounts/status`
2. `GET /internal/auth/sessions/{sessionId}`
