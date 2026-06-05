# API 错误码契约

版本：v0.1  
状态：第一条主链路开发基线

## 1. 编码规则

| 范围 | 含义 |
|---|---|
| `0` | 成功 |
| `10xxx` | 系统通用错误 |
| `20xxx` | 登录认证错误 |
| `21xxx` | 用户服务错误 |
| `22xxx` | 文件服务错误 |
| `23xxx` | 笔记服务错误 |
| `24xxx` | 评论服务错误，后续补充 |
| `25xxx` | 关系服务错误，后续补充 |
| `26xxx` | 计数服务错误，后续补充 |
| `27xxx` | Feed / 排行错误，后续补充 |
| `28xxx` | 推送 / 通知错误，后续补充 |
| `29xxx` | IM 错误，后续补充 |
| `30xxx` | 订单错误，后续补充 |

## 2. 通用错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `10000` | `SYSTEM_ERROR` | 系统繁忙，请稍后再试 | toast，可重试 |
| `10001` | `PARAM_INVALID` | 请求参数不正确 | 表单提示或 toast |
| `10002` | `REQUEST_BODY_INVALID` | 请求内容格式不正确 | toast |
| `10003` | `METHOD_NOT_ALLOWED` | 请求方式不支持 | toast |
| `10004` | `RESOURCE_NOT_FOUND` | 资源不存在 | 空态或 toast |
| `10005` | `RATE_LIMITED` | 操作太频繁，请稍后再试 | toast，按钮短暂禁用 |
| `10006` | `IDEMPOTENCY_CONFLICT` | 请求正在处理，请勿重复提交 | 防重复点击 |
| `10007` | `DOWNSTREAM_FAILED` | 服务暂时不可用，请稍后再试 | toast，可重试 |
| `10008` | `DATA_CONFLICT` | 数据已变化，请刷新后重试 | 刷新当前页面 |
| `10009` | `UNSUPPORTED_MEDIA_TYPE` | 请求内容类型不支持 | toast |
| `10010` | `REQUEST_TOO_LARGE` | 请求内容过大 | toast |
| `10011` | `INTERNAL_AUTH_FAILED` | 服务调用未授权 | 移动端不应收到 |

## 3. 登录认证错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `20001` | `ACCESS_TOKEN_EXPIRED` | 登录已过期，请重新登录 | 尝试刷新 Token |
| `20002` | `ACCESS_TOKEN_INVALID` | 登录状态无效，请重新登录 | 清理登录态并跳转登录 |
| `20003` | `REFRESH_TOKEN_EXPIRED` | 登录已过期，请重新登录 | 清理登录态并跳转登录 |
| `20004` | `REFRESH_TOKEN_INVALID` | 登录状态无效，请重新登录 | 清理登录态并跳转登录 |
| `20005` | `USERNAME_OR_PASSWORD_ERROR` | 用户名或密码错误 | 表单提示 |
| `20006` | `ACCOUNT_DISABLED` | 账号不可用 | 展示账号异常提示 |
| `20007` | `SESSION_REVOKED` | 当前设备已退出登录 | 清理登录态并跳转登录 |
| `20008` | `PASSWORD_TOO_WEAK` | 密码强度不足 | 表单提示 |
| `20009` | `OLD_PASSWORD_ERROR` | 原密码错误 | 表单提示 |
| `20010` | `USERNAME_ALREADY_EXISTS` | 用户名已被使用 | 表单提示 |
| `20011` | `DEVICE_ID_INVALID` | 设备信息不正确 | 重新生成设备 ID |
| `20012` | `TOKEN_REFRESH_REPLAYED` | 登录状态存在风险，请重新登录 | 清理登录态并跳转登录 |

## 4. 用户服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `21001` | `USER_NOT_FOUND` | 用户不存在 | 空态或 toast |
| `21002` | `USER_DISABLED` | 用户不可用 | 展示用户异常 |
| `21003` | `NICKNAME_INVALID` | 昵称不符合要求 | 表单提示 |
| `21004` | `BIO_INVALID` | 简介不符合要求 | 表单提示 |
| `21005` | `AVATAR_FILE_INVALID` | 头像文件不可用 | 重新选择图片 |
| `21006` | `HOME_COVER_FILE_INVALID` | 主页背景文件不可用 | 重新选择图片 |
| `21007` | `PROFILE_VERSION_CONFLICT` | 资料已变化，请刷新后重试 | 刷新资料页 |
| `21008` | `BIRTHDAY_INVALID` | 生日不符合要求 | 表单提示 |
| `21009` | `REGION_INVALID` | 地区不符合要求 | 表单提示 |

## 5. 文件服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `22001` | `FILE_NOT_FOUND` | 文件不存在 | toast |
| `22002` | `FILE_OWNER_MISMATCH` | 无权使用该文件 | toast |
| `22003` | `FILE_SCENE_INVALID` | 文件场景不正确 | toast |
| `22004` | `FILE_TYPE_NOT_SUPPORTED` | 文件类型不支持 | 重新选择文件 |
| `22005` | `FILE_SIZE_EXCEEDED` | 文件大小超出限制 | 重新选择文件 |
| `22006` | `UPLOAD_TOKEN_EXPIRED` | 上传凭证已过期 | 重新申请上传凭证 |
| `22007` | `UPLOAD_NOT_COMPLETED` | 文件尚未上传完成 | 重试确认或重新上传 |
| `22008` | `FILE_STATUS_INVALID` | 文件状态不允许当前操作 | toast |
| `22009` | `FILE_BATCH_VALIDATE_FAILED` | 文件校验失败 | 展示失败文件并重新选择 |
| `22010` | `OBJECT_STORAGE_FAILED` | 文件服务暂时不可用 | toast，可重试 |
| `22011` | `FILE_BIND_FAILED` | 文件绑定失败 | toast，可重试 |

## 6. 笔记服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `23001` | `NOTE_NOT_FOUND` | 笔记不存在 | 空态或返回上一页 |
| `23002` | `NOTE_STATUS_INVALID` | 笔记状态不允许当前操作 | 刷新详情 |
| `23003` | `NOTE_AUTHOR_FORBIDDEN` | 无权操作该笔记 | toast |
| `23004` | `NOTE_VISIBILITY_INVALID` | 可见性参数不正确 | 表单提示 |
| `23005` | `NOTE_TITLE_INVALID` | 标题不符合要求 | 表单提示 |
| `23006` | `NOTE_CONTENT_INVALID` | 正文不符合要求 | 表单提示 |
| `23007` | `NOTE_MEDIA_INVALID` | 笔记图片或视频不可用 | 重新选择媒体 |
| `23008` | `NOTE_MEDIA_COUNT_EXCEEDED` | 媒体数量超出限制 | 重新编辑媒体 |
| `23009` | `NOTE_VERSION_CONFLICT` | 笔记已变化，请刷新后重试 | 刷新编辑页 |
| `23010` | `NOTE_PUBLISH_RATE_LIMITED` | 发布太频繁，请稍后再试 | toast，按钮禁用 |
| `23011` | `NOTE_INTERACTION_RATE_LIMITED` | 操作太频繁，请稍后再试 | toast |
| `23012` | `NOTE_COMMENT_DISABLED` | 作者已关闭评论 | 禁用评论入口 |
| `23013` | `NOTE_IDEMPOTENCY_MISMATCH` | 重复请求内容不一致 | 停止重试并提示 |

## 7. 新增错误码规则

新增错误码必须：

1. 保持数字码稳定，不能复用旧含义。
2. 同步更新后端枚举、OpenAPI、移动端错误处理和 mock。
3. `message` 不暴露内部实现细节。
4. `reason` 使用大写下划线。
5. 移动端能明确判断是否需要重试、跳转登录、刷新页面或展示空态。

