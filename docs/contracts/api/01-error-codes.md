# API 错误码契约

版本：v0.2
状态：第一、第二条主链路开发基线

## 1. 编码规则

| 范围 | 含义 |
|---|---|
| `0` | 成功 |
| `10xxx` | 系统通用错误 |
| `20xxx` | 登录认证错误 |
| `21xxx` | 用户服务错误 |
| `22xxx` | 文件服务错误 |
| `23xxx` | 笔记服务错误 |
| `24xxx` | 评论服务错误 |
| `25xxx` | 关系服务错误 |
| `26xxx` | 计数服务错误 |
| `27xxx` | Feed / 排行榜错误 |
| `28xxx` | 推送 / 通知错误 |
| `29xxx` | IM 错误 |
| `30xxx` | 订单错误 |

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

## 7. 评论服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `24001` | `COMMENT_NOT_FOUND` | 评论不存在 | 刷新评论区或 toast |
| `24002` | `COMMENT_STATUS_INVALID` | 评论状态不可操作 | 刷新评论区 |
| `24003` | `COMMENT_AUTHOR_FORBIDDEN` | 无权操作该评论 | toast |
| `24004` | `COMMENT_CONTENT_INVALID` | 评论内容不符合要求 | 输入框提示 |
| `24005` | `COMMENT_REPLY_TARGET_INVALID` | 回复目标不可用 | toast |
| `24006` | `COMMENT_NOTE_NOT_ALLOWED` | 当前笔记不可评论 | 禁用评论入口 |
| `24007` | `COMMENT_RATE_LIMITED` | 评论太频繁，请稍后再试 | toast，按钮短暂禁用 |
| `24008` | `COMMENT_LIKE_RATE_LIMITED` | 操作太频繁，请稍后再试 | toast |
| `24009` | `COMMENT_IDEMPOTENCY_MISMATCH` | 重复请求内容不一致 | 停止重试并提示 |
| `24010` | `COMMENT_QUERY_DEGRADED` | 评论加载不完整，请稍后刷新 | 展示已返回内容并允许刷新 |

## 8. 关系服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `25001` | `RELATION_TARGET_NOT_FOUND` | 用户不存在 | toast 或空态 |
| `25002` | `RELATION_TARGET_DISABLED` | 用户状态不可关注 | toast |
| `25003` | `RELATION_SELF_FOLLOW_FORBIDDEN` | 不能关注自己 | toast |
| `25004` | `RELATION_STATUS_INVALID` | 关注状态不可操作 | 刷新关注状态 |
| `25005` | `RELATION_RATE_LIMITED` | 操作太频繁，请稍后再试 | toast，按钮短暂禁用 |
| `25006` | `RELATION_BATCH_SIZE_EXCEEDED` | 批量查询数量超出限制 | 移动端拆分请求 |
| `25007` | `RELATION_CURSOR_INVALID` | 分页游标不正确 | 重新刷新列表 |
| `25008` | `RELATION_QUERY_DEGRADED` | 关系数据暂时不完整 | 展示降级状态 |

## 9. 计数服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `26001` | `COUNTER_BATCH_SIZE_EXCEEDED` | 批量查询数量超出限制 | 移动端不应直接收到 |
| `26002` | `COUNTER_TARGET_TYPE_UNSUPPORTED` | 计数对象类型不支持 | 移动端不应直接收到 |
| `26003` | `COUNTER_FIELD_NOT_SUPPORTED` | 计数字段不支持 | 移动端不应直接收到 |
| `26004` | `COUNTER_TARGET_ID_INVALID` | 计数对象不正确 | 移动端不应直接收到 |
| `26005` | `COUNTER_QUERY_DEGRADED` | 计数暂时不准确 | 展示后端返回的降级数据 |
| `26006` | `COUNTER_REBUILD_TASK_NOT_FOUND` | 计数重建任务不存在 | 移动端不应直接收到 |
| `26007` | `COUNTER_REBUILD_TOO_FREQUENT` | 计数修复过于频繁 | 移动端不应直接收到 |
| `26008` | `COUNTER_SOURCE_UNAVAILABLE` | 计数来源暂时不可用 | 移动端不应直接收到 |

## 10. Feed / 排行榜服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `27001` | `FEED_CURSOR_INVALID` | 分页游标不正确 | 重新刷新 Feed |
| `27002` | `FEED_SIZE_EXCEEDED` | 请求数量超出限制 | 使用默认分页大小重试 |
| `27003` | `FEED_QUERY_DEGRADED` | 内容加载不完整，请稍后刷新 | 展示已返回内容 |
| `27004` | `FEED_REBUILD_TASK_NOT_FOUND` | Feed 重建任务不存在 | 移动端不应直接收到 |
| `27005` | `FEED_FANOUT_TASK_NOT_FOUND` | Feed 投递任务不存在 | 移动端不应直接收到 |
| `27006` | `FEED_INTERNAL_DEPENDENCY_FAILED` | 内容服务暂时不可用 | toast，可重试 |
| `27007` | `RANK_CODE_UNSUPPORTED` | 不支持的榜单 | 使用默认榜单或隐藏入口 |
| `27008` | `RANK_PERIOD_NOT_FOUND` | 榜单周期不存在 | 刷新榜单 |
| `27009` | `RANK_MEMBER_TYPE_INVALID` | 榜单成员类型不正确 | 移动端不应直接收到 |
| `27010` | `RANK_MEMBER_NOT_FOUND` | 榜单成员不存在 | 空态或刷新 |
| `27011` | `RANK_NOTE_NOT_ELIGIBLE` | 笔记不具备上榜资格 | 刷新榜单 |
| `27012` | `RANK_QUERY_SIZE_EXCEEDED` | 榜单查询数量超出限制 | 使用默认分页大小重试 |
| `27013` | `RANK_CURSOR_INVALID` | 榜单游标不正确 | 重新刷新榜单 |
| `27014` | `RANK_REBUILD_TASK_NOT_FOUND` | 榜单重建任务不存在 | 移动端不应直接收到 |
| `27015` | `RANK_REBUILD_TOO_FREQUENT` | 榜单重建过于频繁 | 移动端不应直接收到 |
| `27016` | `RANK_SCORE_RULE_INVALID` | 榜单分数规则不正确 | 移动端不应直接收到 |
| `27017` | `RANK_QUERY_DEGRADED` | 榜单数据暂时不完整 | 展示已返回内容 |

## 11. 推送 / 通知服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `28001` | `NOTIFICATION_NOT_FOUND` | 通知不存在 | 刷新通知列表 |
| `28002` | `NOTIFICATION_OWNER_FORBIDDEN` | 无权操作该通知 | toast |
| `28003` | `NOTIFICATION_CATEGORY_INVALID` | 通知分类不正确 | 使用默认分类刷新 |
| `28004` | `NOTIFICATION_CURSOR_INVALID` | 分页游标不正确 | 重新刷新通知列表 |
| `28005` | `NOTIFICATION_SIZE_EXCEEDED` | 请求数量超出限制 | 使用默认分页大小重试 |
| `28006` | `NOTIFICATION_READ_STATE_CONFLICT` | 通知状态已变化 | 刷新未读数 |
| `28007` | `NOTIFICATION_SYSTEM_REQUEST_INVALID` | 系统通知请求不正确 | 移动端不应直接收到 |
| `28008` | `NOTIFICATION_REBUILD_TOO_FREQUENT` | 未读数修复过于频繁 | 移动端不应直接收到 |
| `28009` | `PUSH_DEVICE_NOT_FOUND` | 推送设备不存在 | 重新注册设备 |
| `28010` | `PUSH_DEVICE_INVALID` | 推送设备信息不正确 | 重新注册设备 |
| `28011` | `PUSH_PREFERENCE_INVALID` | 推送偏好不正确 | 恢复默认值后重试 |
| `28012` | `PUSH_REQUEST_NOT_FOUND` | 推送请求不存在 | 移动端不应直接收到 |
| `28013` | `PUSH_REQUEST_INVALID` | 推送请求不正确 | 移动端不应直接收到 |
| `28014` | `PUSH_DELIVERY_UNAVAILABLE` | 推送暂时不可用 | 稍后重试 |

## 12. IM 服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `29001` | `IM_CONVERSATION_NOT_FOUND` | 会话不存在 | 刷新会话列表 |
| `29002` | `IM_CONVERSATION_FORBIDDEN` | 无权访问该会话 | toast 并返回会话列表 |
| `29003` | `IM_TARGET_INVALID` | 私信对象不正确 | toast |
| `29004` | `IM_MESSAGE_INVALID` | 消息内容不正确 | 输入框提示 |
| `29005` | `IM_MESSAGE_TYPE_UNSUPPORTED` | 暂不支持该消息类型 | toast |
| `29006` | `IM_CLIENT_MSG_ID_INVALID` | 消息幂等标识不正确 | 重新生成消息 ID 后重试 |
| `29007` | `IM_MESSAGE_SIZE_EXCEEDED` | 消息内容过长 | 输入框提示 |
| `29008` | `IM_CURSOR_INVALID` | 分页游标不正确 | 重新刷新列表 |
| `29009` | `IM_RATE_LIMITED` | 发送太频繁，请稍后再试 | toast，发送按钮短暂禁用 |
| `29010` | `IM_SEND_FORBIDDEN` | 暂时无法发送私信 | toast |

## 13. 订单服务错误码

| code | reason | message | 移动端处理 |
|---|---|---|---|
| `30001` | `ORDER_ACTIVITY_NOT_FOUND` | 活动不存在 | 刷新神券页或空态 |
| `30002` | `ORDER_ACTIVITY_STATUS_INVALID` | 活动状态暂不可参与 | 刷新活动状态 |
| `30003` | `ORDER_SECKILL_TOKEN_INVALID` | 抢券凭证已失效，请刷新后重试 | 重新获取 token 后重试 |
| `30004` | `ORDER_STOCK_NOT_ENOUGH` | 神券已抢光 | 展示售罄状态 |
| `30005` | `ORDER_DUPLICATE_REQUEST` | 你已参与本次活动 | 查询已有抢券结果 |
| `30006` | `ORDER_REQUEST_NOT_FOUND` | 抢券请求不存在 | 停止轮询并提示刷新 |
| `30007` | `ORDER_NOT_FOUND` | 订单不存在 | 刷新订单状态 |
| `30008` | `ORDER_OWNER_FORBIDDEN` | 无权操作该订单 | toast 并返回 |
| `30009` | `ORDER_STATUS_INVALID` | 订单状态不允许当前操作 | 刷新订单详情 |
| `30010` | `ORDER_PAYMENT_INVALID` | 支付请求不正确 | 停止支付并提示 |
| `30011` | `ORDER_COUPON_NOT_FOUND` | 神券不存在 | 刷新卡包 |
| `30012` | `ORDER_CURSOR_INVALID` | 分页游标不正确 | 重新刷新卡包 |
| `30013` | `ORDER_RATE_LIMITED` | 操作太频繁，请稍后再试 | toast，按钮短暂禁用 |
| `30014` | `ORDER_ACTIVITY_REBUILDING` | 活动库存正在恢复，请稍后再试 | 稍后重试 |
| `30015` | `ORDER_ACTIVITY_NOT_PREHEATED` | 活动准备中，请稍后再试 | 稍后刷新活动 |

## 14. 新增错误码规则

新增错误码必须：

1. 保持数字码稳定，不能复用旧含义。
2. 同步更新后端枚举、OpenAPI、移动端错误处理和 mock。
3. `message` 不暴露内部实现细节。
4. `reason` 使用大写下划线。
5. 移动端能明确判断是否需要重试、跳转登录、刷新页面或展示空态。
