---
name: code-style
description: "适用于 Spring Boot 服务中可读、可维护的 Java (17+) 代码的规范，编写或审查 Java 代码时，强制执行编码标准时主动使用，包括命名、不可变性、Optional用法、流、异常、泛型和项目布局。"
---

# 编码风格（Coding Style）

## 不可变性 (CRITICAL)
始终创建新对象，切勿修改既有对象：

理由：不可变数据可避免隐藏的副作用，使调试更简单，并支持安全的并发。

## 核心原则

### KISS (Keep It Simple)
- 优先选择能够正确解决问题的最简单方案
- 避免过早优化（Premature Optimization）
- 相比技巧性或“聪明”的实现，更优先保证代码清晰、直接、易理解

### DRY (不要重复自己)
- 将真正重复的逻辑提取到公共函数、共享模块或工具中
- 避免通过复制粘贴实现相同逻辑，防止不同副本逐渐产生行为偏差
- 只有当重复已经真实出现时，才引入抽象；不要为了假设中的未来复用提前设计抽象

### YAGNI (You Aren't Gonna Need It)
不要实现当前并不需要的东西，先满足当前明确需求，再让真实需求推动抽象和演进。

- 在真实需求出现之前，不要提前构建功能或抽象
- 避免推测性的通用化设计（Speculative Generality）
- 从简单方案开始，当真实变化压力出现后再进行重构

## 文件组织
多个小而聚焦的文件，优于少数几个超大文件 MANY SMALL FILES > FEW LARGE FILES:
- 高内聚（High cohesion）, 低耦合（low coupling）
- 单文件通常控制在 200-400 行, 原则上不要超过 800 行
- 当模块持续变大时，应及时拆分其中独立的职责或工具逻辑
- 优先按照**功能 / 业务领域（Feature / Domain）**组织代码，而不是单纯按照类型组织代码

例如，相比：
controller/
service/
repository/
dto/

在适合的场景下，更倾向于：
order/
payment/
customer/
inventory/

再在各自业务模块内部组织相关实现，核心目标是：让与同一业务能力相关的代码尽可能保持在一起。

## Error Handling
始终完整、明确地处理错误:
- 在每一层显式处理错误
- 在面向用户的代码中提供友好的错误信息
- 在服务端记录详细的错误上下文
- 切勿静默吞掉错误

## 输入校验
始终在系统边界进行校验：
- 处理前校验所有用户输入
- 在可用时采用基于 schema 的校验
- 快速失败并给出清晰的错误信息
- 切勿信任外部数据（API 响应、用户输入、文件内容）

## 命名约定
- 变量与函数：`camelCase`，使用描述性名称
- 布尔值：优先使用 `is`、`has`、`should` 或 `can` 前缀
- 接口、类型与组件：`PascalCase`
- 常量：`UPPER_SNAKE_CASE`
- 自定义 hooks：`camelCase`，并带 `use` 前缀

## 应避免的代码异味

### 过深嵌套
当条件逻辑开始不断嵌套时，应优先使用：Early Return（提前返回）而不是继续增加条件层级。

例如，不推荐：
```java
if (...) {
    if (...) {
        if (...) {
            ...
        }
    }
}
```
更倾向于：
```java
if (invalidCondition) {
    return
}

if (anotherInvalidCondition) {
    return
}
```

目标是让主要业务路径保持清晰。

### 魔法数字
应使用有意义的命名常量或配置项，而不是直接在代码中写裸值。

例如，不推荐：
```java
if (retryCount > 3) {
    ...
}
```
更推荐：
```java
MAX_RETRY_COUNT = 3

if (retryCount > MAX_RETRY_COUNT) {
    ...
}
```
如果某个值需要根据环境调整，则应进一步放入配置，而不是硬编码。

### 过长函数
大型函数应拆分为：
职责单一、边界清晰的小函数。
每个函数应尽可能只承担一个明确职责。
当一个函数同时负责：参数校验 + 数据查询 + 业务计算 + 状态修改 + 事件发送 + 日志记录，通常意味着它需要进一步拆分或重新设计职责。

References
java详细规范参考: references/java-code-style.md

