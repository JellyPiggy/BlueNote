# BlueNote 架构决策记录

本文档目录用于记录 BlueNote 项目中的关键架构决策。

ADR 只记录会长期影响系统架构、服务边界、部署方式、技术栈或维护成本的决策。普通接口命名、字段设计、局部实现细节不进入 ADR。

## ADR 列表

| 编号 | 标题 | 状态 |
|---|---|---|
| ADR-001 | 移动端选择 uni-app 和 Vue 3 | Accepted |
| ADR-002 | 后端选择 Java 和 Spring Cloud | Accepted |
| ADR-003 | 主消息队列选择 RocketMQ | Accepted |
| ADR-004 | 第一阶段部署方式选择 Docker Compose | Accepted |
| ADR-005 | 第一阶段暂不引入 Kubernetes | Accepted |
| ADR-006 | 逻辑微服务设计但阶段性合并部署 | Accepted |
| ADR-007 | 第一阶段对象存储使用 MinIO | Accepted |

## 状态说明

| 状态 | 说明 |
|---|---|
| Proposed | 已提出，尚未最终确认 |
| Accepted | 已接受，当前方案按此执行 |
| Superseded | 已被新的 ADR 替代 |
| Deprecated | 不再推荐，但历史上曾经采用 |

## 编写模板

```markdown
# ADR-xxx-标题

状态：Accepted  
日期：YYYY-MM-DD

## 背景

## 备选方案

## 决策

## 决策原因

## 影响

## 后续重新评估条件
```
