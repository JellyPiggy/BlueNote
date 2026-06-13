# 第一条主链路契约核对记录

版本：v0.1
状态：当前代码与契约人工核对 + 可重复本地冒烟脚本
更新时间：2026-06-13

## 1. 范围

本记录覆盖第一条主链路和资料编辑闭环：

```text
注册/登录 -> 获取用户资料 -> 上传图片 -> 发布笔记 -> 查看笔记详情 -> 上传头像/封面 -> 修改资料 -> 查看主页
```

对照契约：

1. `docs/contracts/api/00-common.md`
2. `docs/contracts/api/02-auth-api.md`
3. `docs/contracts/api/03-user-api.md`
4. `docs/contracts/api/04-file-api.md`
5. `docs/contracts/api/05-note-api.md`
6. `docs/contracts/security/01-permission-matrix.md`

## 2. 自动冒烟入口

本轮新增脚本：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/verify-main-chain.ps1 -GatewayBaseUrl http://127.0.0.1:8080
```

脚本要求本地依赖、member/content/social/gateway 已启动。脚本会生成临时用户和测试图片，按网关路径验证：

1. `POST /api/auth/register`
2. `GET /api/users/me`
3. `POST /api/files/upload-token`
4. MinIO 预签名 `PUT`
5. `POST /api/files/{fileId}/confirm`
6. `POST /api/notes`
7. `GET /api/notes/{noteId}`
8. `PUT /api/users/me/profile`
9. `GET /api/users/{userId}/home`

## 3. 字段核对

| 契约对象 | 关键字段 | 当前核对结果 |
|---|---|---|
| `ApiResponse` | `code`、`message`、`data`、`traceId` | 脚本每次调用断言 `code=0` 和 `traceId` 非空 |
| `TokenPair` | `userId`、`accessToken`、`refreshToken`、`accessTokenExpiresIn`、`refreshTokenExpiresIn` | 注册响应已按契约返回，脚本断言核心登录态字段 |
| `UserProfile` | `userId`、`bluenoteNo`、`nickname`、`avatarFileId`、`avatarUrl`、`bio`、`gender`、`birthday`、`regionCode`、`homeCoverFileId`、`homeCoverUrl`、`userStatus`、`profileVersion` | `GET /api/users/me` 和资料编辑后刷新已核对，头像/封面 fileId 与 URL 快照可返回 |
| `UploadToken` | `fileId`、`uploadMethod`、`uploadUrl`、`headers`、`expireAt`、`objectKey` | NOTE_IMAGE / USER_AVATAR / USER_HOME_COVER 均走 `PRESIGNED_PUT`，脚本携带返回 headers 直传 |
| `ConfirmUploadResult` | `fileId`、`fileStatus`、`scene`、`accessUrl` | 脚本断言场景一致，状态为 `UPLOADED` 或后续绑定后的 `BOUND` |
| `PublishNoteResponse` | `noteId`、`noteStatus`、`visibility`、`currentVersion`、`publishedAt` | `POST /api/notes` 返回 `PUBLISHED + PUBLIC`，脚本断言核心字段 |
| `NoteDetail` | `noteId`、`author`、`title`、`content`、`visibility`、`noteStatus`、`mediaFiles`、`topics`、`counts`、`viewerAction`、`publishedAt`、`degraded` | `GET /api/notes/{noteId}` 已核对媒体、计数、当前用户互动状态和发布状态 |
| `UserHome` | `user`、`counts`、`relation`、`degraded` | `GET /api/users/{userId}/home` 已核对用户摘要与计数结构 |

## 4. OpenAPI 核对入口

本地启动后可通过以下地址人工核对接口展示和 DTO 字段：

| 应用 | OpenAPI UI |
|---|---|
| gateway | `http://127.0.0.1:8080/swagger-ui.html` |
| member | `http://127.0.0.1:8081/swagger-ui.html` |
| content | `http://127.0.0.1:8082/swagger-ui.html` |
| social | `http://127.0.0.1:8083/swagger-ui.html` |
| order | `http://127.0.0.1:8084/swagger-ui.html` |

第一条主链路主要核对 member 与 content；移动端只通过 gateway 的 `/api/**` 访问业务接口。

## 5. 当前结论

当前第一条主链路的外部接口字段与 `docs/contracts/api/` 基线一致，且已有可重复本地脚本覆盖核心 happy path。

仍待增强：

1. 建立 OpenAPI JSON 与 `docs/contracts/api/` 的自动 diff。
2. 为 auth/user/file/note 增加 Java 层最小接口或集成测试。
3. 补真实手机浏览器或真机文件选择、上传进度、安全区适配验收。
