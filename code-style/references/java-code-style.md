---
paths:
  - "**/*.java"
---
# Java 编码风格
本文件在 [common/coding-style.md](../common/coding-style.md) 的基础上，补充 Java 特有的编码规范。

## 格式规范（Formatting）
- 使用 **google-java-format** 或 **Checkstyle** 进行代码格式检查和规范强制执行；可采用 Google Style 或 Sun Style
- 每个文件只包含一个 public 顶层类型（Top-level Type）
- 缩进统一使用 2 个或 4 个空格，以项目既有规范为准
- 成员声明顺序统一为: constants, fields, constructors, public methods, protected, private

## 不可变性（Immutability）
- 字段默认声明为 final；只有确实需要可变状态时才允许使用可变字段
- 对外部 API 返回集合时，应返回防御性副本（Defensive Copy）：List.copyOf()，Map.copyOf()，Set.copyOf()
- 采用 Copy-on-Write（写时复制）思路：需要修改对象时创建新实例，而不是直接修改已有对象

```java
// GOOD — immutable value type
public record OrderSummary(Long id, String customerName, BigDecimal total) {}

// GOOD — final fields as default, use mutable state only when required
public class Order {
    private final Long id;
    private final List<LineItem> items;

    public List<LineItem> getItems() {
        return List.copyOf(items);
    }
}
```
这里的重点是：对外暴露的对象应该尽可能保持不可变，避免调用方能够绕过模块接口直接修改内部状态。

## 命名规范

遵循标准 Java 命名约定：:
- classes, interfaces, records, enums使用`PascalCase`
- methods, fields, parameters, local variables 使用`camelCase`
- static final 常量 使用 `SCREAMING_SNAKE_CASE`
- Packages 全部使用小写，并采用反向域名命名方式 (`com.example.app.service`)

## Optional 使用规范（Optional Usage）

- 对于可能不存在结果的查询方法，返回 `Optional<T>`
- 优先使用：`map()`，`flatMap()`，`orElseThrow()`不允许在没有 `isPresent()` 判断的情况下直接调用 `get()`
- 禁止将 Optional 作为字段类型或方法参数

```java
// GOOD
return repository.findById(id)
    .map(ResponseDto::from)
    .orElseThrow(() -> new OrderNotFoundException(id));

// BAD — Optional 不应该作为方法参数
public void process(Optional<String> name) {}

// 可能没有数据时，返回Optional<T>是合理的，但应尽量避免
Optional<Order> findById(Long id)
```

## 泛型和类型安全

* 避免原始类型；声明泛型参数
* 对于可复用的工具类，优先使用有界泛型

```java
public <T extends Identifiable> Map<Long, T> indexById(Collection<T> items) { ... }
```

## 错误处理（Error Handling）

- 领域错误优先使用 Unchecked Exception（非受检异常）
- 创建领域专用异常，并继承 `RuntimeException`
- 避免宽泛地捕获 `catch (Exception e)`，除非是在最顶层的异常处理位置
- 异常消息中应包含必要的上下文信息

```java
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(Long id) {
        super("Order not found: id=" + id);
    }
}
```

## Stream 使用规范（Streams）

- Stream 适合用于数据转换（Transformation）Use streams for transformations; Pipeline 尽量保持简短，通常控制在 3～4 个操作以内
- 在可读的情况下优先使用方法引用: `.map(Order::getTotal)`
- 避免在 Stream 操作中产生副作用（Side Effects）
- 对于复杂业务逻辑，宁可使用普通 for 循环，也不要强行写成复杂的 Stream Pipeline

如果业务逻辑已经需要大量条件判断、状态变化或多阶段处理，那么普通循环通常更加清晰。

```java
// GOOD: Use streams for transformations, keep pipelines short
List<String> names = markets.stream()
    .map(Market::name)
    .filter(Objects::nonNull)
    .toList();

// BAD: Avoid complex nested streams; prefer loops for clarity
```

## Null 处理
- 仅在不可避免时接受 `@Nullable`；否则使用 `@NonNull`
- 在输入上使用 Bean 验证（`@NotNull`, `@NotBlank`）


## Lombok 使用规范

```java
// 1. 推荐使用的注解
@Data           // getter + setter + toString + equals + hashCode
@Builder        // 建造者模式
@Slf4j          // 日志
@AllArgsConstructor       // 全参构造器
@NoArgsConstructor        // 无参构造器

// 注意事项：
// 2. Entity 用 @Data 就够了
@Data
@TableName("users")
public class User { ... }

// 3. DTO/VO 需要 Builder 时加 @Builder + @NoArgsConstructor + @AllArgsConstructor
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse { ... }

// 4. Service / BizManageService 类用 @RequiredArgsConstructor 注入
@Service
@Slf4j
@RequiredArgsConstructor
public class UserBizManageService {
    private UserService userService;
}
```

## 项目结构 (Maven/Gradle)
src/main/java/com/example/app/
├── Application.java
├── application
│   ├── assembler
│   │   ├── ExpressCreateAssembler.java
│   ├── command
│   │   └── ExpressCreateCmd.java
│   ├── event
│   │   ├── ReadyEventHandler.java
│   │   └── integrationHandler
│   │       ├── MessageEventPublishHandler.java
│   │       └── ProducerCallbackHandler.java
│   ├── package-info.java
│   ├── readme.md
│   ├── response
│   │   ├── EnumVo.java
│   │   ├── IntegerEnumVo.java
│   │   └── StringEnumVo.java
│   └── service
│       └── ExpressCreateService.java
├── config
│   ├── GsonConfig.java
│   ├── RedisConfig.java
│   ├── RedissonConfig.java
│   ├── Swagger3Config.java
│   └── WebConfig.java
├── constant
│   └── Enable.java
├── domain
│   ├── entity
│   │   └── WaybillInfo.java
│   ├── exception
│   │   └── ExpressException.java
│   ├── repository
│   │   └── ExpressCreateRepository.java
│   └── service
│       ├── SFGateway.java
│       ├── adapter
│       │   └── readme.md
│       └── factory
│           └── readme.md
├── infrastructure
│   ├── adapter
│   │   ├── 3rd
│   │   ├── gateway
│   │   └── readme.md
│   ├── migrations
│   ├── package-info.java
│   └── persistence
│       └── mysql
│           ├── impl
│           ├── mapper
│           │   └── ExpressCreateMapper.java
│           ├── po
│           └── repository
│               └── MybatisExpressCreateRepository.java
├── interfaces
│   ├── Readme.md
│   ├── console
│   │   ├── package-info.java
│   │   ├── schedule
│   │   └── xxl
│   ├── controller
│   │   ├── EnumController.java
│   │   ├── HomeController.java
│   │   └── package-info.java
│   ├── message
│   │   ├── Readme.md
│   │   ├── consumer
│   │   │   └── MessageConsumerHandler.java
│   │   ├── package-info.java
│   │   └── subscirbe
│   │       ├── ConsumeController.java
│   │       └── HttpMessageEvent.java
│   └── package-info.java
└── utils
    └── AppRunListener.java


## 设计模式应用

### 策略模式（Strategy）

当有多个同类实现需要根据条件选择时使用。典型场景：多个 LLM 供应商的请求执行器。

```java
// 1. 定义接口
public interface LlmRequestExecutor {
    boolean supports(String type);
    Mono<ObjectNode> executeNormal(ObjectNode request, ModelGroupConfigItem provider, ...);
    Flux<ServerSentEvent<String>> executeStream(ObjectNode request, ModelGroupConfigItem provider, ...);
}

// 2. 多个实现
@Service
public class OpenAiRequestExecutor extends AbstractRequestExecutor {
    @Override
    public boolean supports(String type) {
        return type.startsWith("openai_");
    }
}

@Service
public class AnthropicRequestExecutor extends AbstractRequestExecutor {
    @Override
    public boolean supports(String type) {
        return type.equals("anthropic_messages");
    }
}

// 3. 通过 Spring 自动注入所有实现，运行时动态选择
@Service
@RequiredArgsConstructor
public class RelayServiceImpl implements RelayService {
    private final List<LlmRequestExecutor> executors;

    private LlmRequestExecutor getExecutor(String type) {
        return executors.stream()
            .filter(e -> e.supports(type))
            .findFirst()
            .orElseThrow(() -> new BusinessException("不支持的请求类型: " + type));
    }
}
```

### 模板方法模式（Template Method）

当多个实现有共同的流程骨架时，把公共逻辑提取到抽象基类：

```java
public abstract class AbstractRequestExecutor implements LlmRequestExecutor {

    // 公共方法：创建日志上下文
    protected RequestLogContext createLogContext(ObjectNode request, ...) {
        RequestLogContext ctx = new RequestLogContext();
        ctx.setId(String.valueOf(snowflakeIdGenerator.nextId()));
        ctx.setRequestTime(System.currentTimeMillis() / 1000);
        // ... 公共初始化逻辑
        return ctx;
    }

    // 公共方法：计算费用
    protected void calculateCost(RequestLogContext ctx) { ... }

    // 子类实现具体的请求发送逻辑
    @Override
    public abstract Mono<ObjectNode> executeNormal(ObjectNode request, ...);
}
```



## 测试期望

- 使用 JUnit 5 + AssertJ 进行流畅的断言
- 使用 Mockito 进行模拟；尽可能避免部分模拟
- 倾向于确定性测试；没有隐藏的休眠

**记住**：保持代码意图明确、类型安全且可观察。除非证明有必要，否则优先考虑可维护性而非微优化。