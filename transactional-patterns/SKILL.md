---
name: transactional-patterns
description: >
  在Java、Springboot、springcloud项目中处理数据库事务有关逻辑是主动使用，适用于@Transactional、多步骤数据库操作、分布式事务，或任何需要保证
  原子性的代码。涵盖事务传播规则、隔离级别、只读优化以及常见陷阱。
---

# 事务模式

## 基本规则

- `@Transactional` 应标注在**服务层方法**上，不应标注在控制器或仓储层上
- 默认传播行为是 `REQUIRED`——加入已有事务，或者创建一个新事务
- 对数据库执行写操作或协调多个写操作的方法，应始终使用该注解
- 所有只读的服务层方法都应使用 `@Transactional(readOnly = true)`——这可以启用相关优化

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 作为该服务中所有方法的默认事务配置
public class OrderService {

    @Transactional // 写操作覆盖类级别的 readOnly 配置
    public Order createOrder(CreateOrderRequest request) {
        inventoryService.reserve(request.items()); // 参与同一个事务
        return orderRepository.save(Order.from(request));
    }

    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id); // 继承 readOnly = true
    }
}
```

## 事务传播行为 (Propagation)
| 传播行为              | 行为说明                                                  |
|---------------------- |---------------------------------------------------------- |
| `REQUIRED` (default)  | 加入已有事务，或者创建一个新事务                          |
| `REQUIRES_NEW`        | 始终创建新事务，并挂起已有事务                               |
| `SUPPORTS`            | 如果事务存在则加入；如果不存在，则在无事务环境中继续执行  |
| `NOT_SUPPORTED`       | 始终在无事务环境中运行                                       |
| `MANDATORY`           | 必须存在已有事务；如果不存在则抛出异常                       |
| `NEVER`               | 必须不存在事务；如果存在事务则抛出异常                       |

```java
// REQUIRES_NEW — 适用于即使父事务回滚也必须保留的审计日志
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logAuditEvent(AuditEvent event) {
    auditRepository.save(event); // 独立于父事务提交
}

// 订单事务回滚，但审计日志仍然会被保存
@Transactional
public void processOrder(Order order) {
    auditService.logAuditEvent(new AuditEvent("ORDER_START", order.getId()));
    try {
        // ……执行处理，期间可能抛出异常
    } catch (Exception e) {
        auditService.logAuditEvent(new AuditEvent("ORDER_FAILED", order.getId()));
        throw e; // 父事务回滚，但审计事务已经提交
    }
}
```

## 自调用陷阱(Self-Invocation Pitfall)

```java
// ❌ BROKEN — s自调用绕过 Spring 代理，导致 @Transactional 被忽略
@Service
public class OrderService {
    @Transactional
    public void processAll(List<UUID> ids) {
        ids.forEach(id -> this.processSingle(id)); // 绕过了代理！
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processSingle(UUID id) { ... } // 不会创建新事务
}

// ✅ FIX — 注入自身代理，或者将方法提取到独立的 Bean 中
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderProcessor orderProcessor; // 独立的 Bean

    @Transactional
    public void processAll(List<UUID> ids) {
        ids.forEach(id -> orderProcessor.processSingle(id)); // 通过代理调用(goes through proxy)
    }
}
```

## 异常处理(Handling Exceptions)

```java

// 默认情况下，@Transactional 会在抛出 RuntimeException 时回滚
// 对于自定义受检（for checked exceptions）异常，需要显式声明 rollbackFor
@Transactional(rollbackFor = InsufficientInventoryException.class) // checked exception
public Order createOrder(CreateOrderRequest request) throws InsufficientInventoryException {
    ...
}

// noRollbackFor — 适用于希望事务仍然提交的非致命异常
@Transactional(noRollbackFor = OptimisticLockException.class)
public void updateWithRetry(UUID id) { ... }
```

## 乐观锁(Optimistic Locking)

```java
@Entity
public class Order {
    @Version
    private Long version; // 乐观锁处理版本冲突
}

// Handles concurrent updates
@Transactional
public Order updateStatus(UUID id, OrderStatus newStatus) {
    Order order = orderRepository.findById(id).orElseThrow();
    order.updateStatus(newStatus); // 如果另一个事务修改了该实体，则抛出Exception
    return orderRepository.save(order);
}
```

## 分布式事务（Saga 模式）

对于跨多个服务的操作，应使用 Saga 模式，而不是分布式事务：

```java
@Service
@RequiredArgsConstructor
public class OrderSaga {

    @Transactional
    public void execute(CreateOrderRequest request) {
        Order order = orderRepository.save(Order.create(request));
        try {
            inventoryClient.reserve(request.items());       // 锁定可售库存 step 1
            paymentClient.charge(order.getId(), request.total()); // 支付订单 step 2
            order.confirm();
            orderRepository.save(order);
        } catch (PaymentException e) {
            inventoryClient.release(request.items()); // 补偿释放库存 step 1
            order.fail("Payment failed");
            orderRepository.save(order);
            throw e;
        }
    }
}
```

## 事务提交后的副作用 Side Effects After Commit

不要在事务内部触发外部操作或通知，例如发送邮件、发布 Kafka 消息、调用 Webhook 或进行缓存预热，如果事务随后回滚，外部操作却已经执行。
应将外部操作或通知绑定到事务提交后：

```java
// Publisher — inside the TX
@Transactional
public Order place(UUID id) {
    Order order = orderRepository.findById(id).orElseThrow();
    order.place();
    eventPublisher.publishEvent(new OrderPlaced(order.getId())); // not sent yet
    return orderRepository.save(order);
}

// Listener — runs ONLY if the TX commits successfully
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onOrderPlaced(OrderPlaced event) {
    emailService.sendConfirmation(event.orderId()); // safe: data is durable
}
```

`AFTER_COMMIT` 在数据库事务提交后运行。需要注意，它运行在原事务之外。
因此，如果监听器本身需要写入数据库，就需要开启一个新的 @Transactional(REQUIRES_NEW) 事务。
这是在 [[domain-driven-design]]中要求的在 aggregate 中发布已收集领域事件的简洁方式。

## 常见陷阱
- Agent 将 @Transactional 标注在控制器上 — 应仅标注在服务层
- Agent 在事务内部发送 email通知 或 publish event — 应使用 @TransactionalEventListener(AFTER_COMMIT)
- Agent 忘记在只读方法上设置 readOnly = true — 无法利用相应的数据库优化
- Agent 通过 this 调用标注了 @Transactional 的方法 — 自调用会绕过代理
- Agent 认为“检查型异常”（checked exception）会触发回滚 — 必须添加 rollbackFor
- Agent 在 private 方法上使用 @Transactional — Spring 代理无法拦截该方法无效事务

