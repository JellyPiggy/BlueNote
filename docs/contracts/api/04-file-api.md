# 文件 API 契约

版本：v0.1  
状态：第一条主链路开发基线

移动端通过网关访问 `/api/files/**`。

## 1. 枚举

### 1.1 scene

| 值 | 说明 | 第一阶段 |
|---|---|---|
| `USER_AVATAR` | 用户头像 | 实现 |
| `USER_HOME_COVER` | 用户主页背景 | 实现 |
| `NOTE_IMAGE` | 笔记图片 | 实现 |
| `NOTE_VIDEO` | 笔记视频 | 预留，可暂不开放上传 |

### 1.2 fileStatus

| 值 | 说明 |
|---|---|
| `INIT` | 已创建上传会话，未确认上传完成 |
| `UPLOADED` | 对象存储已确认存在 |
| `BOUND` | 已绑定业务对象 |
| `DELETED` | 已删除 |
| `BLOCKED` | 已封禁 |

### 1.3 accessLevel

| 值 | 说明 |
|---|---|
| `PUBLIC` | 可公开访问 |
| `PRIVATE` | 需要签名 URL |

## 2. 上传流程

```text
移动端 -> POST /api/files/upload-token
移动端 -> 使用 uploadUrl 直传对象存储
移动端 -> POST /api/files/{fileId}/confirm
移动端 -> 在用户资料或笔记接口提交 fileId
业务服务 -> 调用 /internal/files/validate 或 batch-validate
业务服务 -> 调用 /internal/files/bind 或 batch-bind
```

文件本体不通过普通业务接口上传到后端 JVM。

## 3. 申请上传凭证

```text
POST /api/files/upload-token
```

鉴权：需要。

请求：

```json
{
  "scene": "NOTE_IMAGE",
  "filename": "demo.jpg",
  "mimeType": "image/jpeg",
  "fileSize": 2048000
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": "900001",
    "uploadMethod": "PRESIGNED_PUT",
    "uploadUrl": "https://oss.bluenote.example.com/...",
    "headers": {
      "Content-Type": "image/jpeg"
    },
    "expireAt": "2026-06-05T10:10:00+08:00",
    "objectKey": "local/note-image/2026/06/05/10001/900001.jpg"
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `22003` | `FILE_SCENE_INVALID` |
| `22004` | `FILE_TYPE_NOT_SUPPORTED` |
| `22005` | `FILE_SIZE_EXCEEDED` |
| `22010` | `OBJECT_STORAGE_FAILED` |

移动端要求：

1. 使用返回的 `uploadUrl` 直接上传到对象存储。
2. 必须携带响应中的 `headers`。
3. 上传完成后调用确认接口。
4. `objectKey` 仅用于排查，不作为业务事实。

后端要求：

1. 文件元数据写入 MySQL，状态为 `INIT`。
2. objectKey 由服务端生成，客户端不能指定。
3. 第一阶段使用 MinIO，业务代码通过对象存储适配层访问。

## 4. 确认上传完成

```text
POST /api/files/{fileId}/confirm
```

鉴权：需要。

幂等：需要。重复确认已上传文件应返回当前文件状态。

请求：

```json
{
  "etag": "etag-from-minio",
  "fileSize": 2048000
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": "900001",
    "fileStatus": "UPLOADED",
    "scene": "NOTE_IMAGE",
    "accessUrl": "https://oss.bluenote.example.com/bluenote-files/local/note-image/2026/06/05/10001/900001.jpg"
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `22001` | `FILE_NOT_FOUND` |
| `22002` | `FILE_OWNER_MISMATCH` |
| `22006` | `UPLOAD_TOKEN_EXPIRED` |
| `22007` | `UPLOAD_NOT_COMPLETED` |
| `22008` | `FILE_STATUS_INVALID` |

后端要求：

1. 通过对象存储 `headObject` 确认对象存在。
2. 校验对象大小、etag 或等价对象信息。
3. 状态从 `INIT` 更新为 `UPLOADED`。

## 5. 获取文件访问 URL

```text
GET /api/files/{fileId}/access-url?expireSeconds=3600
```

鉴权：按文件访问级别决定。公开文件可不要求登录，私有文件必须鉴权并校验权限。

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": "900001",
    "accessUrl": "https://oss.bluenote.example.com/...",
    "expireAt": "2026-06-05T11:00:00+08:00",
    "accessLevel": "PUBLIC"
  },
  "traceId": "trace-id"
}
```

可能错误码：

| code | reason |
|---|---|
| `22001` | `FILE_NOT_FOUND` |
| `22008` | `FILE_STATUS_INVALID` |

## 6. 查询我的文件，第一阶段可选

```text
GET /api/files/my?scene=NOTE_IMAGE&cursor=xxx&size=20
```

鉴权：需要。

说明：第一阶段移动端可以不做入口，保留给草稿箱和排查场景。

## 7. 内部接口：校验文件

```text
POST /internal/files/validate
```

请求：

```json
{
  "fileId": "900001",
  "ownerId": "10001",
  "scene": "USER_AVATAR",
  "requireStatus": ["UPLOADED", "BOUND"],
  "maxSize": 5242880,
  "allowedMimeTypes": ["image/jpeg", "image/png", "image/webp"]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "valid": true,
    "file": {
      "fileId": "900001",
      "ownerId": "10001",
      "scene": "USER_AVATAR",
      "mimeType": "image/jpeg",
      "fileSize": 1024000,
      "fileStatus": "UPLOADED",
      "accessUrl": "https://oss.bluenote.example.com/..."
    }
  },
  "traceId": "trace-id"
}
```

## 8. 内部接口：批量校验文件

```text
POST /internal/files/batch-validate
```

请求：

```json
{
  "ownerId": "10001",
  "scene": "NOTE_IMAGE",
  "fileIds": ["900001", "900002", "900003"],
  "maxCount": 9
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "valid": true,
    "files": [
      {
        "fileId": "900001",
        "mimeType": "image/jpeg",
        "fileSize": 2048000,
        "accessUrl": "https://oss.bluenote.example.com/..."
      }
    ]
  },
  "traceId": "trace-id"
}
```

失败响应示例：

```json
{
  "code": 22009,
  "message": "文件校验失败",
  "data": {
    "reason": "FILE_BATCH_VALIDATE_FAILED",
    "errors": [
      {
        "fileId": "900003",
        "reason": "FILE_OWNER_MISMATCH"
      }
    ]
  },
  "traceId": "trace-id"
}
```

## 9. 内部接口：绑定文件

```text
POST /internal/files/bind
```

幂等：需要。

请求：

```json
{
  "fileId": "900001",
  "ownerId": "10001",
  "bindType": "USER_AVATAR",
  "bindId": "10001"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": "900001",
    "bindStatus": "BOUND"
  },
  "traceId": "trace-id"
}
```

## 10. 内部接口：批量绑定文件

```text
POST /internal/files/batch-bind
```

幂等：需要。

请求：

```json
{
  "ownerId": "10001",
  "bindType": "NOTE_MEDIA",
  "bindId": "800001",
  "fileIds": ["900001", "900002"]
}
```

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "bindStatus": "BOUND",
    "fileIds": ["900001", "900002"]
  },
  "traceId": "trace-id"
}
```

## 11. 内部接口：解绑文件

```text
POST /internal/files/unbind
```

请求：

```json
{
  "fileId": "900001",
  "bindType": "USER_AVATAR",
  "bindId": "10001"
}
```

