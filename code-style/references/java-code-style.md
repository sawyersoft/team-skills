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
public interface NotifyStrategy {
    void sendNotify(OrderEntity order);
}

// 2. 多个实现

// 策略需要的公共抽象方法，通用逻辑放到抽象类中实现，具体策略实现自己独有逻辑
@Slf4j
@RequiredArgsConstructor
public class AbstractNotifyProvider {
    private RedisUtils redisUtils;
    private MessageGateWay messageGateWay;

    /**
     * 发送通知失败时不要阻塞主流程，捕获异常记录错误，但是不阻断流程.
     * TODO 如何发送消息，发送给谁？ 没有商户信息 如何确定发给谁，直接发给手机号么?
     *
     * @param order    订单信息
     * @param template 模板号
     * @param params   参数
     * @return true or false
     */
    protected boolean sendNotify(OrderEntity order, Template template, Map<String, String> params) {
        return messageGateWay.sendNotify(order, template, params);
    }
}

// 订单待审核 -> 审核通过或拒绝，此时发送通知。
@Component("WAIT_REVIEW")
public class NotifyReviewComplete extends AbstractNotifyProvider implements NotifyStrategy {

    @Resource
    private ReviewRepository reviewRepository;

    /**
     * @param order 提交审核时的订单数据，待审核。
     */
    @Override
    public void sendNotify(OrderEntity order) {
        ReviewEntity review = reviewRepository.getOneByMoSn(order.getMoSn());
        Map<String, String> params = new HashMap<>();

        params.put("category", order.getCategory());
        params.put("brand", order.getBrandName());
        if (review.getResult().equals(ReviewResultEnum.APPROVE.getValue())) {
            sendNotify(order, Template.REVIEW_APPROVE, params);
        } else {
            sendNotify(order, Template.REVIEW_REJECT, params);
        }
    }
}

// 订单待支付状态 WAIT_PAY 发送通知用户支付
@Component("WAIT_PAY")
public class NotifyToPay extends AbstractNotifyProvider implements NotifyStrategy {

    @Override
    public void sendNotify(OrderEntity order) {
        Map<String, String> params = new HashMap<>();
        params.put("category", order.getCategory());
        params.put("brand", order.getBrandName());
        sendNotify(order, Template.PAY_TIP_ONE, params);
    }
}

// 3. 通过 Spring 自动注入所有实现，运行时动态选择

@Service
@RequiredArgsConstructor
public class NotifyStrategyContext {
    private final Map<String, NotifyStrategy> notifyStrategyMap = new ConcurrentHashMap<>();

    public NotifyStrategyContext(Map<String, NotifyStrategy> notifyStrategyMap) {
        if (!notifyStrategyMap.isEmpty()) {
            notifyStrategyMap.clear();
        }

        this.notifyStrategyMap.putAll(notifyStrategyMap);
    }

    public NotifyStrategy getStrategy(String status) {
        return this.notifyStrategyMap.get(status);
    }
}

// 4. 业务逻辑，基于订单状态变更事件 处理业务

// 订单审核通过后，根据订单状态 确定通知策略，执行策略。
@Slf4j
@Component
public class OrderReviewHandler {
    @Resource
    private NotifyStrategyContext strategyContext;

    /**
     * 待审核状态下，审核通过的消息通知
     *
     * @param event 事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void orderReviewDoneHandler(OrderReviewEvent event) {
        NotifyStrategy notifyStrategy = strategyContext.getStrategy(event.getOrder().getStatus());
        notifyStrategy.sendNotify(event.getOrder());
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

## 异常体系设计

### 异常类型 和 错误码设计
异常类 和 错误码统一使用 `com.xhj.utils.common.constant` 枚举定义的错误码

```java
/**
 * REST接口请求返回CODE码定义
 */
@Getter
@AllArgsConstructor
public enum ResponseCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    NOTFOUND(404, "请求的资源不存在!"),
    PARAMS_MISSING(1001, "参数缺失错误"),
    PARAMS_INVALID(1003, "请求参数[%s]检验失败，原因:[%s]!"),
    SIGNATURE_TIMESTAMP_ERROR(2002, "签名时间戳缺少或已过期或格式错误"),
    NETWORK_FAILED(5000, "网络请求发生异常"),
    BUSINESS_FAILED(6000, "业务处理发生异常"),
    SERVER_BUSY(9998, "服务器繁忙"),
    SERVER_ERROR(9999, "系统异常"),

    //系统内置HTTP类异常Code码转换定义
    MethodArgumentNotValidException(400, "客户端请求异常，通常是参数或者uri不对。"),
    MethodArgumentTypeMismatchException(400, "参数类型不匹配，比如要求int给了字符串!"),
    MissingServletRequestPartException(400, "POST文件上传，缺少multipart/form-data类参数!"),//针对通常是multipart/form-data参数
    MissingPathVariableException(400, "REST接口URI上的参数缺失，如:brand/{id},缺少id参数!"),
    BindException(400, "地址已在使用，通常是端口被占用，启动服务失败。"),
    MissingServletRequestParameterException(400, "请求缺少必传参数，请补全参数!"),
    TypeMismatchException(400, "请求参数类型错误，参数绑定实体类属性时发现类型不对。"),
    ServletRequestBindingException(400, "请求绑定异常,通常是@RequestAttribute指定的控制器参数"),
    HttpMessageNotReadableException(400, "调用方在以post json方式请求服务时，没有对参数进行json序列化。"),
    NoHandlerFoundException(404, "请求接口不存在或路由参数缺失!"),
    NoSuchRequestHandlingMethodException(404, "指定的请求不存在，404 Not Found!"),
    HttpRequestMethodNotSupportedException(405, "请求方法Method不支持，比如只支持POST的接口用GET方法请求!"),
    //服务器端指定的输出媒体类型，客户端不接受，例如服务端标记输出text/plan（@RequestMapping(value="test",produces="text/plain"))但是客户端为Accept: application/json
    HttpMediaTypeNotAcceptableException(406, "服务端输出的媒体类型客户端不接受。"),
    HttpMediaTypeNotSupportedException(415, "不支持的媒体类型"),
    ConversionNotSupportedException(500, "服务器内部错误，通常是Mapper类型转换或对比异常!"),
    HttpMessageNotWritableException(500, "服务器内部错误，通常是对象转为JSON时出现了无限递归调用!"),
    AsyncRequestTimeoutException(503, "服务不可用!");

    private final int code;
    private final String message;

    /**
     * 指定枚举值获得枚举对象
     *
     * @param code 枚举值
     * @return 枚举对象
     */
    public static ResponseCode getStatusEnum(int code) {
        return Arrays.stream(ResponseCode.values()).filter(e -> e.getCode() == code).findFirst().orElse(SERVER_ERROR);
    }

    /**
     * 获得枚举值的名称或描述文字
     *
     * @param code 枚举值
     * @return String 行数说明
     */
    public static String getLabel(int code) {
        return ResponseCode.getStatusEnum(code).getMessage();
    }
}

```

### 自定义异常类
所有自定义异常都必须继承自 RuntimeException，为什么用 RuntimeException 而不是 checked Exception，因为 Spring 的 @Transactional 默认只回滚 RuntimeException，而且 checked Exception 会污染方法签名，让调用链上的每个方法都要声明 throws。

```java
/**
 * 业务异常基类
 * 所有可预期的业务错误都应继承此类
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }
}

/**
 * 资源不存在异常
 */
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, Object id) {
        super(404, resource + "不存在: " + id);
    }
}

/**
 * 重复资源异常
 */
public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String message) {
        super(409, message);
    }
}
```

### 异常使用规则

#### 基本原则：Service 层的职责是检查业务规则，不满足就抛异常。不要返回 null 或者特殊状态码让 Controller 去判断——那会让 Controller 充满 if-else。

```java
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserVO getUserById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new NotFoundException("用户不存在", id);
        }
        return convertToVO(user);
    }
}
```

#### try-catch 使用原则
- 非必要，不要在各层业务代码 catch 异常 —— 让它一路抛到全局处理器
- 只 catch 你确实能处理的异常，比如外部 API 调用失败需要降级
- catch 后必须做有意义的事（日志 + 降级 / 日志 + 转换为业务异常），禁止空 catch 不做任何处理

```java
// ❌ BAD - 错误：空 catch，异常被吃掉了
try {
    callExternalApi();
} catch (Exception e) {
    // 什么都不做
}

// ❌ BAD - 错误：catch 了又原样抛出，没有意义
try {
    userMapper.insert(user);
} catch (Exception e) {
    throw e;
}

// ✅ 正确：catch 外部异常，转为业务异常 + 记录日志
try {
    externalApiClient.sendNotification(userId);
} catch (Exception e) {
    log.error("发送通知失败, userId={}: {}", userId, e);
    // 通知失败不影响主流程，降级处理
}

// ✅ 正确：catch 后转为业务异常
try {
    smsService.sendCode(phone);
} catch (SmsException e) {
    log.error("短信发送失败: {}", e);
    throw new BusinessException(ErrorCode.SMS_SEND_FAILED);
}
```

## 测试期望

- 使用 JUnit 5 + AssertJ 进行流畅的断言
- 使用 Mockito 进行模拟；尽可能避免部分模拟
- 倾向于确定性测试；没有隐藏的休眠

**记住**：保持代码意图明确、类型安全且可观察。除非证明有必要，否则优先考虑可维护性而非微优化。