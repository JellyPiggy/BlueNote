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
| MySQL | 13306 | auth、user、file、note schema |
| Redis | 16379 | 登录限流、Token 黑名单、缓存 |
| Nacos | 8848 | 注册中心和配置中心 |
| RocketMQ NameServer | 9876 | MQ NameServer |
| RocketMQ Broker | 10911 | MQ Broker |
| MinIO API / Console | 9000 / 9001 | 对象存储 |

默认值只用于 local 开发。test/prod 必须通过环境变量或独立 env 文件覆盖敏感配置。
