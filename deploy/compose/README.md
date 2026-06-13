# BlueNote 本地依赖环境

本目录提供 local 环境依赖组件的 Docker Compose 配置。

启动：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml up -d
```

停止：

```bash
docker compose -f deploy/compose/compose.base.yml -f deploy/compose/compose.local.yml down
```

组件：

| 服务 | 本地端口 | 用途 |
|---|---:|---|
| MySQL | 3306 | auth、user、file、note、comment、relation、counter、feed、rank、notification、push、im、order schema |
| Redis | 6379 | 登录限流、Token 黑名单、计数/Feed/榜单/通知/Push/订单缓存 |
| Nacos | 8848 | 注册中心和配置中心 |
| RocketMQ NameServer | 9876 | MQ NameServer |
| RocketMQ Broker | 10911 | MQ Broker |
| MinIO API / Console | 9000 / 9001 | 图片和文件对象存储 |

注意：

1. 默认值只用于 local 开发。test/prod 必须通过环境变量或独立 env 文件覆盖敏感配置。
2. MySQL 初始化脚本挂载 `backend/sql/`，只会在数据卷首次创建时自动执行。
3. 如果新增 DDL 后发现表不存在，通常是旧数据卷没有重新初始化；可手动执行对应 SQL，或确认数据可删除后重建本地 volume。
4. MinIO 默认会创建 `bluenote-files` bucket，业务上传使用预签名 PUT，不经过后端 JVM 中转文件本体。
