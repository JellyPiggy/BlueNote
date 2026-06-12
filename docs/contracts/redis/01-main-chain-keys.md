# 第一条主链路 Redis Key 契约

版本：v0.2
状态：第一、第二条主链路开发基线

## 1. 命名规则

统一格式：

```text
bluenote:{env}:{service}:{biz}:{id}
```

示例：

```text
bluenote:local:auth:token:blacklist:{jti}
bluenote:test:user:profile:{userId}
bluenote:prod:note:detail:{noteId}
```

`env` 取值：

1. `local`
2. `test`
3. `prod`

Redis 不作为唯一事实来源。Redis 丢失后，必须能从 MySQL 或对象存储重建。

## 2. auth keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:auth:login-fail:username:{username}` | String | 15 分钟 | auth | auth | 用户名登录失败次数 |
| `bluenote:{env}:auth:login-fail:ip:{ip}` | String | 15 分钟 | auth | auth | IP 登录失败次数 |
| `bluenote:{env}:auth:login-fail:device:{deviceId}` | String | 15 分钟 | auth | auth | 设备登录失败次数 |
| `bluenote:{env}:auth:rate:register:ip:{ip}` | String | 1 小时 | auth | auth / gateway | IP 注册限流 |
| `bluenote:{env}:auth:rate:login:ip:{ip}` | String | 15 分钟 | auth | auth / gateway | IP 登录限流 |
| `bluenote:{env}:auth:token:blacklist:{jti}` | String | Access Token 剩余有效期 | auth | gateway | 当前设备登出后的 Access Token 黑名单 |
| `bluenote:{env}:auth:session:{sessionId}` | String(JSON) | 30 分钟 | auth | gateway / auth | 会话缓存，可选 |

降级：

1. Redis 不可用时，登录主流程应尽量可用。
2. 黑名单不可用时记录风险日志，依赖短 Access Token 过期兜底。
3. 登录失败限流不可用时可退化为网关基础限流。

## 3. user keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:user:profile:{userId}` | String(JSON) | 6 小时 + 抖动 | user | user / 内部调用方 | 用户公开摘要缓存 |
| `bluenote:{env}:user:profile:null:{userId}` | String | 60 秒 | user | user | 不存在用户空值缓存 |

缓存内容：

```json
{
  "userId": "10001",
  "bluenoteNo": "BN10001",
  "nickname": "小蓝",
  "avatarFileId": "90001",
  "avatarUrl": "https://oss.bluenote.example.com/avatar.png",
  "bio": "记录生活",
  "userStatus": "NORMAL",
  "profileVersion": 1
}
```

失效策略：

1. 用户资料更新成功后删除 `user:profile:{userId}`。
2. 发布 `UserProfileUpdated` 事件。
3. 其他服务保存快照时必须比较 `profileVersion`。

## 4. file keys

文件服务第一阶段不强依赖 Redis 保存业务事实。

可选 Key：

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:file:rate:upload:{userId}` | String | 1 分钟 | file | file / gateway | 上传频率限制 |
| `bluenote:{env}:file:upload-token:{fileId}` | String(JSON) | 上传 URL 剩余有效期 | file | file | 上传凭证短缓存，可选 |

对象存储是否存在必须通过 `headObject` 或 MySQL 状态确认，不能只看 Redis。

## 5. note keys

| Key | 类型 | TTL | 写入方 | 读取方 | 用途 |
|---|---|---|---|---|---|
| `bluenote:{env}:note:detail:{noteId}` | String(JSON) | 5 到 30 分钟 + 抖动 | note | note | 笔记详情缓存 |
| `bluenote:{env}:note:summary:{noteId}` | String(JSON) | 10 到 60 分钟 + 抖动 | note | note / feed / rank | 笔记摘要缓存 |
| `bluenote:{env}:note:viewer-action:{userId}:{noteId}` | Hash | 5 到 30 分钟 | note | note | 当前用户点赞收藏状态 |
| `bluenote:{env}:note:rate:publish:{userId}` | String | 1 分钟 | note | note / gateway | 发布限流 |
| `bluenote:{env}:note:rate:edit:{userId}` | String | 1 分钟 | note | note / gateway | 编辑限流 |
| `bluenote:{env}:note:rate:interaction:{userId}` | String | 10 秒 | note | note / gateway | 点赞收藏限流 |

`note:detail` 内容：

```json
{
  "noteId": "800001",
  "authorId": "10001",
  "title": "杭州周末咖啡店记录",
  "content": "正文",
  "coverFileId": "900001",
  "mediaFileIds": ["900001"],
  "visibility": "PUBLIC",
  "noteStatus": "PUBLISHED",
  "currentVersion": 1,
  "publishedAt": "2026-06-05T10:01:00+08:00"
}
```

失效策略：

1. 笔记编辑、删除、下架、可见性变化后删除详情和摘要缓存。
2. 点赞收藏状态变化后删除 `viewer-action`。
3. Redis 不可用时直接查 MySQL。

## 6. Key 变更规则

新增 Key 必须记录：

1. Key 模板。
2. 所属服务。
3. 数据结构。
4. TTL。
5. 写入方和读取方。
6. 失效策略。
7. 重建方式。
