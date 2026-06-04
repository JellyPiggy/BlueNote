# ADR-002-后端选择 Java 和 Spring Cloud

状态：Accepted  
日期：2026-06-03

## 背景

BlueNote 后端计划按照真实企业项目标准设计，包含登录、笔记、计数、关系、Feed、排行榜、评论、IM、订单等多个业务域。

系统需要长期维护、服务边界清晰、生态成熟，并且方便后续补充监控、配置中心、网关、消息队列、缓存和数据库治理。

## 备选方案

| 方案 | 说明 |
|---|---|
| Java + Spring Boot + Spring Cloud | 企业级生态成熟，适合微服务 |
| Go + Gin/Kratos | 性能好，部署轻，但业务生态和开发习惯需重新建立 |
| Node.js + NestJS | 开发效率高，但大型后端长期治理需要更强约束 |
| Python + FastAPI | 开发快，但高并发和工程治理不是当前首选 |

## 决策

后端第一阶段选择：

```text
Java 21 LTS + Spring Boot 3.5.x + Spring Cloud 2025.0.x
```

配套技术：

1. Spring Cloud Gateway
2. Nacos
3. MyBatis-Plus + MyBatis XML
4. Spring Security
5. OpenAPI / Knife4j
6. Spring Boot Actuator + Micrometer

## 决策原因

1. Java 和 Spring 生态适合长期维护的企业级项目。
2. Spring Cloud 对网关、服务发现、配置、负载均衡等能力支持成熟。
3. Java 21 LTS 稳定，适合作为当前主版本。
4. Spring Boot 3.5.x 比新大版本更稳，第三方组件兼容风险较低。
5. MyBatis-Plus + XML 兼顾开发效率和复杂 SQL 可控性。

## 影响

正向影响：

1. 架构能力完整。
2. 可维护性和招聘友好度较好。
3. 与 MySQL、Redis、RocketMQ、Nacos 等生态集成成熟。
4. 后续拆微服务和做治理有明确路线。

代价：

1. Java 服务内存占用较高。
2. 2 核 4G 服务器上不能部署过多独立 Java 进程。
3. 工程结构需要控制，避免微服务项目变成复杂大杂烩。

## 后续重新评估条件

出现以下情况时重新评估：

1. 部署资源长期无法承载 Java 服务。
2. 某些高性能网关、IM 接入层需要单独使用 Go 或其他语言。
3. Spring Boot 4.x 生态完全稳定，并且项目需要升级。
4. Java 25 LTS 生态成熟后，评估 JDK 升级。
