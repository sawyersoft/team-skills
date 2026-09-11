---
name: domain-driven-design
description: >
  在 DDD 风格项目中处理领域模型、聚合、值对象、领域事件、异步消息或仓储时使用，确保采用充血领域模型，而不是贫血式 CRUD。
---

# 领域驱动设计

## DDD 分层架构（DDD Layered Architecture）

核心原则：
将 Spring Boot 应用围绕 **领域驱动设计（DDD）** 进行组织，而不是把`Controller → Service → Repository` 简单视为完整架构。
**业务规则属于 Domain；Application Service负责用例编排；Infrastructure 负责技术实现；Interface负责外部协议适配；依赖方向必须朝向 Domain。**

## 依赖规则

依赖方向：
``` text
Interface
    ↓
Application
    ↓
Domain
    ↑
Infrastructure
```

-   Interface层 可以依赖 Application层。
-   Application 可以依赖 Domain。
-   Domain 不得依赖 Interface 或 Infrastructure。
-   Infrastructure 可以依赖 Domain，并实现 Domain 定义的接口。
-   Infrastructure 不得把基础设施关注点引入 Domain。
-   Domain 代码原则上不应依赖 Spring、JPA、Kafka、HTTP、Redis等基础设施框架。

重点不是简单的 package 隔离，而是保护 Domain 不被技术细节污染。
---

# Domain 层

Domain 是系统的核心,它表达**业务是什么、业务允许什么、不允许什么**，而不是数据库如何存储或系统如何与外部通信，优先保持 Domain 与框架无关。
Domain 只能包含Aggregate Root,Entity,Value Object,Domain Service,Domain Policy/Specification,Domain Event,Domain Exception,Repository Interface,业务所需的 Domain Port。

## Entity

Entity 通过**身份和生命周期**定义，而不仅仅是属性集合。

``` java
// GOOD 实体的核心是要有实体行为，例如"取消订单"是业务行为，应使用：
order.cancel();

// BAD 而不应该直接修改实体属性的状态，因为实体行为不仅是修改状态 还有上下游流程要受影响
order.setStatus(OrderStatus.CANCELLED);
```

实体类规则：
-   身份必须明确，要有唯一识别ID，注意不是数据库自增ID。
-   属于 Entity 的业务行为应放在 Entity 中。
-   尽量避免只有 getter / setter 的贫血模型。
-   不要为业务状态提供无限制的 setter。
-   务必将持久化关注点与领域行为分离。

## Aggregate

 Aggregate 是一个**一致性边界**，所有聚合实现必须要继承自com.xhj.utils.common.dto.AggregateRoot类。

``` text
Order ← Aggregate Root
├── OrderItem
├── OrderItem
└── Money
```

### Aggregate Root 是外部对象访问和修改 Aggregate 内部对象的唯一入口。

```java
// ✅ GOOD 聚合根控制对子实体的所有访问
order.addItem(productId, quantity); // 通过聚合根操作
order.removeItem(itemId);           // 通过聚合根操作
order.cancel();
order.changeShippingAddress(address);

// ❌ BAD 从外部直接访问子实体
order.getItems().add(new OrderItem(...)); // 绕过不变式
order.setStatus(...);
order.getItems().get(0).setQuantity(...);
```

### 聚合规则
- 每个aggregate root对应一个repository
- 外部代码只能通过aggregate root访问聚合，绝不能直接访问子实体
- aggregate之间只通过 ID或对象唯一单号 引用，不持有直接对象引用
- 保持聚合小巧——如果包含超过 3～4 个子实体，应将其拆分
- 在 Aggregate 内维护业务不变式，禁止为了执行局部校验而加载无关 Aggregate。
- 不要把 Aggregate 当作普通数据容器，不要暴露可被外部任意修改的内部集合，禁止外部代码通过直接修改状态绕过领域行为。
- 跨 Aggregate 的一致性通常通过 Application Service、Domain Service、Domain Event 或最终一致性处理，而不是无条件扩大 Aggregate。

示例：
- ✅ GOOD 良好设计 aggregate 示例阅读：`examples/good-aggregate.java` 
- ❌ BAD 不好设计 aggregate 示例阅读：`examples/bad-aggregate.java`

关键点：Aggregate 自己负责维护业务不变式，而不是让 Application Service 或 Controller 直接修改内部状态。

## 值对象

对于身份由其值决定的概念，使用 Value Object，具有 不可变性、没有唯一性标识，并且按值判断是否相等，例如：
``` text
Money
OrderId
CustomerId
EmailAddress
PhoneNumber
Address
DateRange
```

示例：
```java
// GOOD 设计良好的 Money对象 （java 17+）
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("Amount cannot be negative");
        Objects.requireNonNull(currency);
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency))
            throw new CurrencyMismatchException(currency, other.currency);
        return new Money(amount.add(other.amount), currency);
    }

    public static Money of(String amount, String currency) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currency));
    }
}

// GOOD 设计良好的 EmailAddress （java 17+）
public record EmailAddress(String value) {
    public EmailAddress {
        if (!value.matches("^[\\w.-]+@[\\w.-]+\\.[a-z]{2,}$"))
            throw new InvalidEmailException(value);
    }
}
```

属于该概念本身的不变式，应尽可能由 Value Object 自己负责校验。

## 领域事件
所有领域事件都必须实现 com.xhj.utils.common.dto.DomainEvent 接口，以兼容AggregateRoot类对领域event的处理。

```java
// Event - 不可变的 record
public record OrderPlaced(OrderId orderId, CustomerId customerId, Money total, Instant occurredAt) implements DomainEvent {
    public static OrderPlaced of(Order order) {
        return new OrderPlaced(order.getId(), order.getCustomerId(), order.getTotal(), Instant.now());
    }
}

// 在aggregate中收集events，保存后再publish
@Entity
public class Order {
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    public void place() {
        this.status = OrderStatus.PLACED;
        domainEvents.add(OrderPlaced.of(this));
    }

    public List<Object> pullDomainEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }
}

// 成功保存后发布
@Service
@RequiredArgsConstructor
public class OrderApplicationService {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order placeOrder(PlaceOrderCommand command) {
        Order order = orderRepository.findById(command.orderId()).orElseThrow();
        order.place();
        Order saved = orderRepository.save(order);
        saved.pullDomainEvents().forEach(eventPublisher::publishEvent); // 提交后发布
        return saved;
    }
}

// 监听事件——绑定到事务提交，而不只是事件发布。
// @EventListener 会在事务内同步执行；如果事务随后回滚，邮件却已经发送。
// 优先使用 @TransactionalEventListener(AFTER_COMMIT)——参见 [[transactional-patterns]]。
@Component
@RequiredArgsConstructor
public class OrderPlacedHandler {
    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onOrderPlaced(OrderPlaced event) {
        emailService.sendOrderConfirmation(event.customerId(), event.orderId());
    }
}
```

## Domain Service

### 什么时候需要 Domain Service
- 明确实属于领域行为，单无法自然归属于某一个 Entity、值对象 或 Aggregate。
- 处理的是领域概念，而不是基础设施问题。
- 规则涉及多个领域对象，甚至多个聚合，把逻辑放进任何单个单实体或聚合都会显得职责牵强。
- 规则本身通常无状态，输入领域对象或值对象，输出领域结果。

可以使用 Domain Service，关键判断因素不是“逻辑复杂度”，而是“谁应该对这条业务规则负责”。

例如：

``` java
public class OrderPricingService {

    public Money calculate(Order order, PricingPolicy policy) {
        // 1. 计算商品原价
        // 2. 筛选适用活动
        // 3. 处理互斥和叠加规则
        // 4. 计算优惠金额
        // 5. 将优惠分摊到订单行
        // 6. 返回定价结果
    }
}
```
需要特别注意：Domain Service 只处理逻辑，不负责查询或写数据。

### 按这个顺序判断层级：
- 规则只依赖一个值 → 放入值对象
- 规则属于一个实体的生命周期或不变性 → 放入实体
- 规则属于整个聚合的一致性 → 放入聚合根
- 规则是真实业务行为，但横跨多个领域对象且无自然归属 → Domain Service
- 逻辑主要是查询、保存、事务或流程协调 → Application Service
- 逻辑主要是数据库、消息、支付、HTTP 等技术实现 → Infrastructure

建议优先把行为放进实体和值对象。只有确定某条业务规则没有自然归属时，才引入 Domain Service，禁止把所有业务逻辑都堆进各种 XxxDomainService。

## Repository

Repository 是面向 Domain 的 Aggregate 持久化抽象。
接口属于 Domain：

``` java
public interface OrderRepository {

    Optional<Order> findById(OrderId id);

    void save(Order order);

    void delete(Order order);
}
```

实现属于 Infrastructure：

``` java
@Repository
@RequiredArgsConstructor
public class MbpOrderRepository implements OrderRepository {

    private final SpringDataOrderRepository repository;

    @Override
    public Optional<Order> findById(OrderId id) {
        return repository.findById(id.value())
            .map(OrderJpaEntity::toDomain);
    }

    @Override
    public void save(Order order) {
        repository.save(OrderJpaEntity.from(order));
    }

    @Override
    public void delete(Order order) {
        repository.deleteById(order.getId().value());
    }
}
```

### Repository 规则

- 当 Repository 表达 Domain 的持久化需求时，其接口属于 Domain。
- JPA / Spring Data / Mybatis /Mybatis-plus Repository 接口属于 Infrastructure。
- Repository 主要负责 Aggregate 的持久化和获取。
- 不要在 Repository 实现中放业务决策。
- Domain 接口不得暴露 JPA 类型。
- Domain 不得直接依赖 `JpaRepository`, `mybatis mapper` 等基础架构层的库。
- 避免返回原始 `Object[]`。
- 对复杂查询，适当使用显式 Projection / Read Model。

## Application 层

Application 层负责定义、编排 和 执行**用例（Use Case）**，它回答的是 **系统提供什么操作？**

Application 层负责“业务流程”，不负责负责“业务规则”，它的典型职责：
- 调度和编排实体、聚合、领域服务和业务流程。
- 加载所需 Aggregate。
- 调用 Domain 行为。
- 在需要时协调多个 Aggregate。
- 调用 Domain Port。
- 定义事务边界。
- 持久化变更。
- 发布应用层结果或事件。
- 返回 Application Result 或查询结果。

示例：

``` java
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;

    @Transactional
    public OrderResult createOrder(CreateOrderCommand command) {
        Order order = Order.create(
            command.customerId(),
            command.items()
        );

        inventoryService.reserve(command.items());
        orderRepository.save(order);
        return OrderResult.from(order);
    }
}
```

Application Service 负责协调用例，而 Domain 负责决定操作是否符合业务规则。

## 规约（复杂查询）
我们使用 MyBatis-Plus 提供MySQL的数据读写能力。Wrapper 类允许开发者以链式调用的方式构造查询条件，无需编写繁琐的 SQL 语句，从而提高开发效率并减少 SQL 注入的风险。
详细使用方式阅读：

## Interface 层
Interface 层负责把外部协议转换为 Application Command，并把 ApplicationResult 转换成外部响应。
例如：
-   REST Controller
-   GraphQL Controller
-   gRPC Endpoint
-   Message Consumer
-   CLI Adapter

## Controller 规则

-   只处理 HTTP 相关内容。
-   解析和校验请求。
-   Request DTO → Application Command。
-   调用一个 Application Service Use Case。
-   Application Result → Response DTO。
-   不包含业务规则。
-   不直接访问 Repository。
-   不直接操作 Aggregate 内部状态。
-   当 Domain 对象会暴露内部领域结构时，不要直接返回 Domain 对象。
-   使用 `@ControllerAdvice` 将异常转换为 HTTP 响应。

示例：

``` java
@PostMapping("/orders")
public ResponseEntity<OrderResponse> create(
        @Valid @RequestBody CreateOrderRequest request) {

    OrderResult result = orderApplicationService.createOrder(
        request.toCommand()
    );

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(OrderResponse.from(result));
}
```

## 防腐层（ACL）
- 与外部系统或遗留代码集成时，不要让它们的模型渗透到领域中
- 创建防腐层，将外部数据转换为领域语言
- 防腐层位于基础设施层，而不是领域层

```java
// ✅ GOOD——防腐层将外部支付 API 转换为领域概念
@Component
@RequiredArgsConstructor
public class PaymentGatewayAdapter implements PaymentPort {

    private final ExternalPaymentClient client;  // 第三方 SDK

    @Override
    public PaymentConfirmation charge(OrderId orderId, Money amount) {
        // 领域模型 → 外部模型
        PaymentApiRequest apiRequest = new PaymentApiRequest(
            orderId.value().toString(),
            amount.amount().doubleValue(),
            amount.currency().getCurrencyCode());

        // 调用外部系统
        PaymentApiResponse apiResponse = client.charge(apiRequest);

        // 外部模型 → 领域模型
        return new PaymentConfirmation(
            PaymentId.of(apiResponse.getTransactionId()),
            apiResponse.isSuccessful() ? PaymentStatus.CONFIRMED : PaymentStatus.DECLINED);
    }
}
```

# 事件驱动消息
除非整个系统能证明更强的语义，否则按至少一次投递进行设计，事件和消息可能被处理超过一次，必须设计为Idempotency（幂等性）：
收到消息
   ↓
判断是否已经处理
   ↓
未处理 → 执行业务逻辑 → 记录已处理
已处理 → 直接跳过

## 事件约束（Event contract）
- 使用不可变的事件类封装，包含 `eventId`、`eventType`、`occurredAt`、`schemaVersion` 和 payload。
- 将已发布的 schema 视为 API。以兼容方式添加字段，**绝不能在不知会消费者的情况下改变已有字段的语义**。
- 将产生事件的根因对象的ID或单号（Causation ID） 以及 根因对象的关联对象的ID或单号（Correlation ID） 放在消息的header 或 payload中。
- 不要直接发布 JPA Entity、领域Entity、聚合根、mybatis实体 或 框架特定的序列化结构。

## 生产者规则（Producer rules）
- 只有在源状态变更已经持久化并且可靠落盘之后，才能发布事件。
- 当数据库状态与消息中间件发布必须一致时，必须使用事务性 Outbox（Transactional Outbox）且在数据事务提交后有独立Publisher发送消息。
- 当需要保证同一聚合（aggregate）内的消息顺序时，请使用确定性的分区键（Partition Key）或属性字段来保证顺序性。

## 消费者规则（Consumer rules）
- 消费者必须严格保证消费业务幂等，必须根据已经持久化的处理记录 或 本身就是幂等的写操作，保证处理逻辑具备幂等性。
- 将业务状态变更与幂等性标记放在同一个事务范围内。
- 在配置重试机制之前，先区分：临时性失败（Transient Failure）还是 永久性失败（Permanent Failure）。
- 为后续重放（Replay）保留：原始事件，失败原因，尝试次数（Attempt Count or retry times）。
- 必须记录重要的消费处理逻辑日志，以及失败、重试的日志。
- MUST 显式的手动acknowledge，禁止自动ack。

## 测试
- 测试序列化兼容性与处理器幂等性。
- 使用 Testcontainers 进行消息中间件集成测试。
- 测试重复消息（Duplicate Message），消息乱序（Reordered Message），延迟消息（Delayed Message），有毒消息（Poison Message）等场景.

## 示例
```java
// ❌ BAD consumer 
@KafkaListener(topics = "orders.created")
void consume(OrderEntity event) {
    orders.save(event);
    emailClient.sendConfirmation(event.getCustomerEmail());
}

// ✅ GOOD consumer
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
final class OrderCreatedConsumer {

    private final TransactionTemplate transactionTemplate;
    private final ProcessedEvents processedEvents;
    private final Orders orders;

    OrderCreatedConsumer(
            TransactionTemplate transactionTemplate,
            ProcessedEvents processedEvents,
            Orders orders) {
        this.transactionTemplate = transactionTemplate;
        this.processedEvents = processedEvents;
        this.orders = orders;
    }

    @KafkaListener(topics = "orders.created", groupId = "order-projections")
    void consume(OrderCreated event) {
        transactionTemplate.executeWithoutResult(status -> {
            if (processedEvents.markIfNew(event.eventId())) {
                orders.apply(event);
            }
        });
    }
}

record OrderCreated(UUID eventId, UUID orderId, int schemaVersion) implements DomainEvent{ }

interface ProcessedEvents {
    boolean markIfNew(UUID eventId);
}

interface Orders {
    void apply(OrderCreated event);
}

```

## 常见陷阱
- Agent 创建只有 getter/setter 的贫血模型——应将行为放在领域对象中
- Agent 使用 `int` 作为实体 ID——应使用强类型值对象（`OrderId`、`CustomerId`）
- Agent 将领域逻辑放入服务——服务只应负责编排，不应作出领域决策
- Agent 从外部直接访问子实体——始终通过聚合根操作
- Agent 在保存前发布事件——应在成功保存或事务提交后发布
- Agent 允许外部 API 模型进入领域——使用防腐层进行转换
- Agent 因消息中间件支持事务而假设恰好一次投递 —— 端到端副作用仍需要幂等性。
- Agent 对校验失败无限重试 —— 应尽快将永久失败转入死信或通过ack丢弃消息。
- Agent 在数据库事务内直接发布，却未使用发件箱 —— 崩溃可能导致状态与事件投递不一致。
- Agent 使用随机分区键 —— 同一聚合的顺序将丢失。
- Agent 直接反序列化为 JPA 实体 —— 应使用带版本的事件契约。

