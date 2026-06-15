# 第一条主链路权限矩阵

版本：v0.1  
状态：第一条主链路开发基线

## 1. 网关用户上下文

网关校验 Access Token 后，向后端服务注入：

| Header | 说明 |
|---|---|
| `X-User-Id` | 当前用户 ID |
| `X-Device-Id` | 当前设备 ID |
| `X-Session-Id` | 当前会话 ID |
| `X-Trace-Id` | 链路 ID |

后端业务服务不直接解析移动端 Token，优先使用网关注入的用户上下文。内部服务调用也必须透传 `X-Trace-Id`。

## 2. 外部接口权限矩阵

| 方法 | 路径 | 登录 | 资源归属 | 幂等 | 限流 | 说明 |
|---|---|---|---|---|---|---|
| POST | `/api/auth/register` | 否 | 无 | 用户名唯一 | IP / 设备 | 注册 |
| POST | `/api/auth/login` | 否 | 无 | 否 | 用户名 / IP / 设备 | 登录 |
| POST | `/api/auth/token/refresh` | 否 | 设备会话 | Refresh Token 轮换 | 设备 | 刷新 Token |
| POST | `/api/auth/logout` | 是 | 当前会话 | 是 | 用户 | 登出 |
| POST | `/api/auth/password/change` | 是 | 当前用户 | 是 | 用户 | 修改密码 |
| GET | `/api/users/me` | 是 | 当前用户 | 否 | 用户 | 查询当前资料 |
| PUT | `/api/users/me/profile` | 是 | 当前用户 | 建议 | 用户 | 修改资料 |
| GET | `/api/users/{userId}/public` | 可选 | 公开资料 | 否 | IP / 用户 | 公开资料 |
| GET | `/api/users/{userId}/home` | 可选 | 公开资料 | 否 | IP / 用户 | 主页头部 |
| POST | `/api/files/upload-token` | 是 | 当前用户 | 否 | 用户 | 申请上传凭证 |
| POST | `/api/files/{fileId}/confirm` | 是 | 文件 owner | 是 | 用户 | 上传确认 |
| GET | `/api/files/{fileId}/access-url` | 按访问级别 | 文件权限 | 否 | 用户 / IP | 获取访问 URL |
| GET | `/api/files/my` | 是 | 当前用户 | 否 | 用户 | 我的文件，可选 |
| POST | `/api/notes/drafts` | 是 | 当前用户 | 是 | 用户 | 保存草稿 |
| POST | `/api/notes` | 是 | 当前用户 | 是 | 用户 | 发布笔记 |
| POST | `/api/notes/{noteId}/publish` | 是 | 作者 | 是 | 用户 | 发布草稿 |
| PUT | `/api/notes/{noteId}` | 是 | 作者 | 是 | 用户 | 编辑笔记 |
| DELETE | `/api/notes/{noteId}` | 是 | 作者 | 是 | 用户 | 删除笔记 |
| GET | `/api/notes` | 可选 | 公开列表 | 否 | 用户 / IP | 公开笔记时间流 |
| GET | `/api/notes/{noteId}` | 可选 | 可见性校验 | 否 | 用户 / IP | 详情 |
| GET | `/api/notes/users/{userId}` | 可选 | 公开列表 | 否 | 用户 / IP | 作者笔记 |
| GET | `/api/notes/me` | 是 | 当前用户 | 否 | 用户 | 我的笔记 |
| POST | `/api/notes/{noteId}/like` | 是 | 当前用户 | 业务唯一键 | 用户 | 点赞 |
| DELETE | `/api/notes/{noteId}/like` | 是 | 当前用户 | 业务唯一键 | 用户 | 取消点赞 |
| POST | `/api/notes/{noteId}/collect` | 是 | 当前用户 | 业务唯一键 | 用户 | 收藏 |
| DELETE | `/api/notes/{noteId}/collect` | 是 | 当前用户 | 业务唯一键 | 用户 | 取消收藏 |
| GET | `/api/notes/me/collections` | 是 | 当前用户 | 否 | 用户 | 我的收藏 |
| GET | `/api/notes/me/likes` | 是 | 当前用户 | 否 | 用户 | 我的赞过 |

## 3. 内部接口权限矩阵

| 方法 | 路径 | 调用方 | 被调用方 | 要求 |
|---|---|---|---|---|
| POST | `/internal/users/register-profile` | auth | user | 服务身份认证，按 userId 幂等 |
| POST | `/internal/users/batch-summary` | note / comment / feed / im / notification | user | 服务身份认证，单次最多 100 |
| POST | `/internal/users/status-check` | note / comment / relation / im | user | 服务身份认证，写操作前校验 |
| POST | `/internal/files/validate` | user / note / im | file | 服务身份认证，校验 ownerId |
| POST | `/internal/files/batch-validate` | note / im | file | 服务身份认证，单次最多 20 文件 |
| POST | `/internal/files/bind` | user / note | file | 服务身份认证，幂等 |
| POST | `/internal/files/batch-bind` | note | file | 服务身份认证，幂等 |
| POST | `/internal/files/unbind` | user / note | file | 服务身份认证，幂等 |
| POST | `/internal/notes/batch-summary` | feed / rank / comment / notification | note | 服务身份认证，单次最多 100 |
| POST | `/internal/notes/comment-check` | comment | note | 服务身份认证，校验可评论 |

网关不得暴露 `/internal/**` 到公网。

## 4. 参数校验基线

| 对象 | 规则 |
|---|---|
| `username` | 4 到 32 位，只允许字母、数字、下划线，具体正则由后端统一 |
| `password` | 最少 8 位，必须满足基础复杂度 |
| `deviceId` | 非空，最大 128 字符 |
| `nickname` | 1 到 64 字符，需内容安全检查 |
| `bio` | 最大 256 字符 |
| 图片大小 | 单文件最大 10MB |
| 笔记标题 | 1 到 128 字符 |
| 笔记正文 | 1 到 5000 字符 |
| 笔记图片数 | 1 到 9 张 |
| 话题数 | 0 到 10 个，每个 1 到 64 字符 |
| `size` | 1 到 50 |

## 5. 敏感数据规则

| 数据 | 规则 |
|---|---|
| 密码 | 只存 hash，不打印日志 |
| Refresh Token | 只存 hash，不打印完整值 |
| Access Token | 日志不打印完整值 |
| 手机号 / 邮箱 | 第一阶段不强依赖；后续展示时脱敏 |
| 文件 URL | 私有文件返回短期签名 URL |
| IM / 订单数据 | 后续契约单独定义，不在当前主链路泄露 |

## 6. 移动端安全要求

1. Access Token 和 Refresh Token 持久化存储要集中封装。
2. Token 刷新失败必须清理登录态。
3. 发布、上传确认、编辑资料必须防重复点击。
4. 不在页面中硬编码内部服务地址。
5. 不在日志或调试输出中打印完整 Token。

## 7. 后端安全要求

1. 所有写接口使用 Jakarta Validation。
2. 所有用户资源操作校验 `X-User-Id`。
3. 文件绑定必须校验 ownerId。
4. 私密、删除、下架笔记不能对无权限用户返回。
5. 认证失败、限流、幂等冲突使用固定错误码。
6. 日志包含 `traceId`，敏感字段脱敏。
