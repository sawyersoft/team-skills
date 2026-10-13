# design-axiomatic-pipeline

Design Axiomatic / Axiomatic Design：公理化设计。这是一种系统性的工程设计方法学（由麻省理工学院的 Nam P. Suh 教授提出），核心是通过“独立性公理”和“信息公理”来规范设计流程。

> 公理管道模式：公理设计分解 + 契约式边界 + 函数式实现 + 业务三段论

## Why It Matters

复杂业务逻辑容易变成"大泥球" - 校验、业务、副作用混杂在一起。公理管道模式将业务分解为三个正交阶段（前置/执行/后置），用契约定义阶段边界，用纯函数实现每个阶段。这让代码可测试、可组合、易于并发优化。

## 核心架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                        公理设计：功能分解                             │
│  ┌─────────────────┬─────────────────┬─────────────────┐           │
│  │   FR1: 前置处理  │   FR2: 核心执行  │   FR3: 后置处理  │           │
│  └────────┬────────┴────────┬────────┴────────┬────────┘           │
│           ↓                 ↓                 ↓                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              契约式设计：阶段间契约                            │   │
│  │   Input → [Pre契约] → Context → [Exec契约] → Result → [Post] │   │
│  └─────────────────────────────────────────────────────────────┘   │
│           ↓                 ↓                 ↓                     │
│  ┌─────────────────┬─────────────────┬─────────────────┐           │
│  │  函数式：纯校验   │  函数式：纯转换   │  函数式：纯映射   │           │
│  │  Validators     │  Transformers   │  EventHandlers  │           │
│  │  【可并发】       │  【Pipeline】    │  【异步事件】    │           │
│  └─────────────────┴─────────────────┴─────────────────┘           │
└─────────────────────────────────────────────────────────────────────┘
```

## Bad

```java
// 所有逻辑混在一起 - 难测试、难维护、难并发
@Transactional
public Order placeOrder(PlaceOrderCommand cmd) {
    // 校验散落各处
    if (cmd.getItems() == null || cmd.getItems().isEmpty()) {
        throw new ValidationException("Items required");
    }
    
    User user = userRepository.findById(cmd.getUserId())
        .orElseThrow(() -> new UserNotFoundException(cmd.getUserId()));
    
    if (!user.isActive()) {
        throw new ValidationException("User not active");
    }
    
    // 业务逻辑
    Order order = new Order();
    order.setUserId(cmd.getUserId());
    order.setItems(cmd.getItems());
    
    BigDecimal total = BigDecimal.ZERO;
    for (OrderItem item : cmd.getItems()) {
        Product product = productRepository.findById(item.getProductId())
            .orElseThrow();
        if (product.getStock() < item.getQuantity()) {
            throw new InsufficientStockException(product.getId());
        }
        total = total.add(product.getPrice().multiply(
            BigDecimal.valueOf(item.getQuantity())));
    }
    order.setTotal(total);
    
    Order saved = orderRepository.save(order);
    
    // 后置处理混在事务里
    emailService.sendOrderConfirmation(saved);  // 如果失败呢？
    inventoryService.reserve(saved);
    metricsService.recordOrder(saved);
    
    return saved;
}
```

## Good

```java
/**
 * 业务管道接口 - 契约式定义
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public interface BusinessPipeline<I, O> {
    
    /** 前置校验器（可并发执行） */
    List<Validator<I>> validators();
    
    /** 核心执行器（纯函数管道） */
    Function<I, O> executor();
    
    /** 后置处理器（异步事件化） */
    List<Consumer<O>> postHandlers();
    
    /** 默认执行流程 */
    default O execute(I input) {
        // Phase 1: Pre - 并发校验
        validators().parallelStream().forEach(v -> v.validate(input));
        
        // Phase 2: Execute - 纯函数执行
        O result = executor().apply(input);
        
        // Phase 3: Post - 异步派发
        postHandlers().forEach(h -> CompletableFuture.runAsync(() -> h.accept(result)));
        
        return result;
    }
}

/**
 * 具体业务实现
 */
@Service
@RequiredArgsConstructor
public class PlaceOrderPipeline implements BusinessPipeline<PlaceOrderCommand, Order> {
    
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    // ═══════════════════════════════════════════════
    // Phase 1: 前置校验器（纯函数，可并发）
    // ═══════════════════════════════════════════════
    @Override
    public List<Validator<PlaceOrderCommand>> validators() {
        return List.of(
            new ItemsNotEmptyValidator(),
            new UserActiveValidator(userRepository),
            new StockAvailableValidator(productRepository),
            new IdempotencyValidator(orderRepository)
        );
    }
    
    // ═══════════════════════════════════════════════
    // Phase 2: 核心执行器（纯函数管道）
    // ═══════════════════════════════════════════════
    @Override
    public Function<PlaceOrderCommand, Order> executor() {
        return cmd -> Stream.of(cmd)
            .map(this::toOrder)           // 命令 → 订单
            .map(this::calculateTotal)    // 计算总价
            .map(this::applyPromotion)    // 应用优惠
            .map(orderRepository::save)   // 持久化（副作用边界）
            .findFirst()
            .orElseThrow();
    }
    
    // ═══════════════════════════════════════════════
    // Phase 3: 后置处理器（事件化，解耦）
    // ═══════════════════════════════════════════════
    @Override
    public List<Consumer<Order>> postHandlers() {
        return List.of(
            order -> eventPublisher.publishEvent(new OrderPlacedEvent(order))
        );
    }
    
    // 纯函数：命令转订单
    private Order toOrder(PlaceOrderCommand cmd) {
        return Order.builder()
            .userId(cmd.getUserId())
            .items(cmd.getItems())
            .status(OrderStatus.PENDING)
            .build();
    }
    
    // 纯函数：计算总价
    private Order calculateTotal(Order order) {
        BigDecimal total = order.getItems().stream()
            .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return order.toBuilder().total(total).build();
    }
    
    // 纯函数：应用优惠
    private Order applyPromotion(Order order) {
        // 优惠逻辑
        return order;
    }
}
```

## 校验器示例

```java
// 校验器接口 - 契约式
@FunctionalInterface
public interface Validator<T> {
    /**
     * @pre input != null
     * @post 校验通过返回，否则抛出 ValidationException
     */
    void validate(T input);
}

// 具体校验器 - 纯函数
public class ItemsNotEmptyValidator implements Validator<PlaceOrderCommand> {
    @Override
    public void validate(PlaceOrderCommand cmd) {
        if (cmd.getItems() == null || cmd.getItems().isEmpty()) {
            throw new ValidationException("Items cannot be empty");
        }
    }
}

public class StockAvailableValidator implements Validator<PlaceOrderCommand> {
    private final ProductRepository productRepository;
    
    @Override
    public void validate(PlaceOrderCommand cmd) {
        for (OrderItemCommand item : cmd.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
            if (product.getStock() < item.getQuantity()) {
                throw new InsufficientStockException(product.getId(), 
                    item.getQuantity(), product.getStock());
            }
        }
    }
}
```

## 后置事件处理

```java
// 事件定义
public record OrderPlacedEvent(Order order) {}

// 事件监听器 - 解耦的后置处理
@Component
public class OrderEventHandlers {
    
    @EventListener
    @Async
    public void sendConfirmationEmail(OrderPlacedEvent event) {
        emailService.sendOrderConfirmation(event.order());
    }
    
    @EventListener
    @Async
    public void reserveInventory(OrderPlacedEvent event) {
        inventoryService.reserve(event.order());
    }
    
    @EventListener
    @Async  
    public void recordMetrics(OrderPlacedEvent event) {
        metricsService.recordOrder(event.order());
    }
    
    // 事务提交后才执行（保证数据一致性）
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyWarehouse(OrderPlacedEvent event) {
        warehouseService.notify(event.order());
    }
}
```

## 三阶段特性对比

| 阶段 | 特性 | 执行方式 | 失败处理 |
|------|------|----------|----------|
| **前置** | 校验、幂等检查、规则跳过 | 可并发 | 快速失败 |
| **执行** | 核心业务、数据持久化 | 串行/事务 | 回滚 |
| **后置** | 通知、缓存更新、审计 | 异步/事件 | 重试/补偿 |

## 设计原则

1. **公理设计**：三阶段功能正交，互不干扰
2. **契约式设计**：每阶段有明确的输入/输出契约
3. **函数式编程**：阶段内部是纯函数组合
4. **事件驱动**：后置处理通过事件解耦
