# 线程使用会犯（concurrency-executor-service）

> 使用 ExecutorService，而不是直接创建 Thread

## 为什么重要

直接创建 Thread 的开销很大（每个线程需要 1MB 以上的栈空间），且难以扩展。ExecutorService 提供线程池、任务队列、优雅关闭机制，并可通过 Future 处理执行结果。它将任务提交与执行策略分离，使代码更易于测试，也便于配置线程调度策略。

## Bad

```java
// 直接创建线程 - 开销大且不易管理
for (int i = 0; i < 100; i++) {
    final int taskId = i;
    new Thread(() -> {
        processTask(taskId);
    }).start();
}
// 没有线程数量限制、线程无法复用，也没有关闭处理逻辑

// 手动管理线程
Thread worker = new Thread(this::doWork);
worker.start();
// 如果抛出异常怎么办？如何获取结果？如何取消？
```

## Good

```java
// 固定大小的线程池 - 有限的可控并发度
ExecutorService executor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);

try {
    List<Future<Result>> futures = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
        final int taskId = i;
        futures.add(executor.submit(() -> processTask(taskId)));
    }
    
    // 收集结果
    for (Future<Result> future : futures) {
        Result result = future.get();  // 阻塞至任务完成
        handleResult(result);
    }
} finally {
    executor.shutdown();
    executor.awaitTermination(60, TimeUnit.SECONDS);
}

// Try-with-resources (Java 19+)
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> processTask(1));
    executor.submit(() -> processTask(2));
}  // Auto-shutdown and await
```

## 常见的 Executor 类型

```java
// Fixed thread pool - 固定大小的有界线程池 - 适用于 CPU 密集型任务
ExecutorService fixed = Executors.newFixedThreadPool(4);

// Cached thread pool - 缓存线程池 - 无界，适用于大量短任务
ExecutorService cached = Executors.newCachedThreadPool();

// Single thread - 顺序执行
ExecutorService single = Executors.newSingleThreadExecutor();

// Scheduled - 适用于延迟或周期性任务
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(2);

// Virtual threads (Java 21+) - 适用于 I/O 密集型任务，但是必须是Java 21以上才能用。
ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor();

// Custom thread pool - 支持进行友好灵活的细粒度控制
ThreadPoolExecutor custom = new ThreadPoolExecutor(
    4,                      // core pool size
    8,                      // max pool size
    60L, TimeUnit.SECONDS,  // keep-alive time
    new LinkedBlockingQueue<>(100),  // work queue
    new ThreadPoolExecutor.CallerRunsPolicy()  // rejection policy
);
```

## 使用 Future

```java
ExecutorService executor = Executors.newFixedThreadPool(4);

// 提交 Callable 以获取结果
Future<String> future = executor.submit(() -> {
    return fetchData();
});

// 获取结果（阻塞式）
try {
    String result = future.get();
    // 或者设置超时时间
    String result = future.get(5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    future.cancel(true);  // 超时时取消任务
} catch (ExecutionException e) {
    Throwable cause = e.getCause();  // 原始异常
}

// 检查状态
future.isDone();      // 已完成（成功或失败）
future.isCancelled(); // 已取消
```

## 正确关闭（Shutdown Properly）

```java
public class TaskExecutor implements AutoCloseable {
    private final ExecutorService executor;
    
    public TaskExecutor() {
        this.executor = Executors.newFixedThreadPool(4);
    }
    
    public Future<Result> submit(Task task) {
        return executor.submit(() -> process(task));
    }
    
    @Override
    public void close() {
        executor.shutdown();  // 停止接收新任务
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();  // 等待一段时间，例如  60秒，强制关闭
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

// 使用方式
try (var taskExecutor = new TaskExecutor()) {
    taskExecutor.submit(task1);
    taskExecutor.submit(task2);
}  // 正确关闭
```

## InvokeAll 和 InvokeAny

```java
ExecutorService executor = Executors.newFixedThreadPool(4);

// InvokeAll - 等待所有任务完成
List<Callable<Result>> tasks = List.of(
    () -> processA(),
    () -> processB(),
    () -> processC()
);

List<Future<Result>> results = executor.invokeAll(tasks);
// invokeAll 返回时，所有 Future 均已完成

// InvokeAny - 返回第一个成功完成的结果
Result first = executor.invokeAny(tasks);
// 任意一个任务成功完成后立即返回
// 取消其余任务
```

## Spring 集成（Spring Integration）

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}

@Service
public class UserService {
    
    @Async
    public CompletableFuture<User> findUserAsync(long id) {
        User user = repository.findById(id);
        return CompletableFuture.completedFuture(user);
    }
}
```

## 线程安全

从一开始就按照线程安全的要求设计类。

### 为什么线程安全很重要

线程安全无法在后期简单地附加到现有设计中。竞态条件和数据损坏问题通常很隐蔽、难以复现，并且修复成本高昂。预先按照线程安全的要求进行设计，可以避免一整类缺陷。应明确记录线程安全保证，让使用者知道如何正确使用这些类。

## Bad

```java
// Not thread-safe - 非线程安全 - 包含可变的共享状态
public class UserCache {
    private Map<Long, User> cache = new HashMap<>();  // Not thread-safe!
    private int hitCount;  // 递增操作存在竞态条件
    
    public User get(Long id) {
        hitCount++;  // 不是原子操作！
        return cache.get(id);
    }
    
    public void put(Long id, User user) {
        cache.put(id, user);  // 并发修改！
    }
}

// Usage - 并发情况下的行为不确定
UserCache cache = new UserCache();
// Thread 1: cache.put(1, user1);
// Thread 2: cache.get(1);  // 可能观察到不完整的状态或抛出异常
```

## Good

```java
// Thread-safe 使用并发集合和原子操作实现线程安全
public class UserCache {
    private final ConcurrentMap<Long, User> cache = new ConcurrentHashMap<>();
    private final AtomicLong hitCount = new AtomicLong(); // 原子性计数统计
    
    public User get(Long id) {
        hitCount.incrementAndGet();
        return cache.get(id);
    }
    
    public void put(Long id, User user) {
        cache.put(id, user);
    }
    
    public long getHitCount() {
        return hitCount.get();
    }
}

// 或者使用不可变对象（天然线程安全）
@Immutable  // 表明设计意图
public final class UserConfig {
    private final String name;
    private final List<String> roles;
    
    public UserConfig(String name, List<String> roles) {
        this.name = name;
        this.roles = List.copyOf(roles);  // 防御性复制为不可变列表
    }
    
    public String getName() { return name; }
    public List<String> getRoles() { return roles; }  // 已经不可变
}
```

## 线程安全策略（Thread-Safety Strategies）

```java
// 1. Immutability - 不可变性 - 在可行时record是最佳选择
public record Point(int x, int y) {}  // 设计上不可变（java 17以上）

// 2. Thread-confined - 线程封闭，对象仅由单个线程使用
public class RequestContext {
    // 仅由处理请求的线程访问
    private User currentUser;
    private List<String> messages = new ArrayList<>();
}

// 3. 同步访问
public class SynchronizedCounter {
    private int count;
    
    public synchronized void increment() {
        count++;
    }
    
    public synchronized int get() {
        return count;
    }
}

// 4. 基于锁的实现
public class BankAccount {
    private final ReentrantLock lock = new ReentrantLock();
    private BigDecimal balance;
    
    public void transfer(BankAccount target, BigDecimal amount) {
        // 始终按照一致的顺序获取锁，以防止死锁
        BankAccount first = this.hashCode() < target.hashCode() ? this : target;
        BankAccount second = first == this ? target : this;
        
        first.lock.lock();
        try {
            second.lock.lock();
            try {
                this.balance = this.balance.subtract(amount);
                target.balance = target.balance.add(amount);
            } finally {
                second.lock.unlock();
            }
        } finally {
            first.lock.unlock();
        }
    }
}
```

## Java doc 文档层备注线程安全性
用Java doc注解标注，线程安全或不安全，可变或不可变。

```java
/**
 * 用于存储 user对象的线程安全缓存。
 * 
 * <p> 该类可由多个线程安全地并发使用，所有public方法彼此之间均具有原子性。</p>
 * 
 * @ThreadSafe
 */
public class UserCache { ... }

/**
 * 请求作用域的上下文容器。
 * 
 * <p> 该类不是线程安全的。每个实例只能由单个线程(处理请求的线程）访问。</p>
 * 
 * @NotThreadSafe
 */
public class RequestContext { ... }

/**
 * 配置信息容器。
 * 
 * <p>该类的实例不可变，因此天然线程安全。</p>
 * 
 * @Immutable
 */
public final class AppConfig { ... }
```

## Using javax.annotation.concurrent

```java
import javax.annotation.concurrent.ThreadSafe;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.GuardedBy;

@ThreadSafe
public class SafeCounter {
    @GuardedBy("this")  // Documents 说明由哪个锁保护该字段
    private int count;
    
    public synchronized void increment() {
        count++;
    }
}
```

 ## 虚拟线程并发规范 (con-virtual-threads)
 
  针对 I/O 密集型任务，须优先使用虚拟线程，但必须是 Java 21+ 才可以使用。

### 为什么虚拟线程很重要 (Why It Matters)

传统的**平台线程（Platform Threads）非常昂贵（每个线程默认占用 1MB+ 的栈内存），且数量受限于操作系统资源。而虚拟线程（Virtual Threads）极其轻量（每个仅占用几 KB），允许系统轻松创建数百万个并发线程。** 在 I/O 密集型工作负载（如 HTTP 请求调用、数据库查询、文件 I/O）下，虚拟线程既能大幅简化并发代码的书写，又能榨干系统吞吐量，且完全无需引入响应式编程（Reactive Programming）那套复杂的异步逻辑。

## Bad

```java
// Platform threads - 扩展性受到严重限制
ExecutorService executor = Executors.newFixedThreadPool(200);  // 硬编码设置了数量上限
List<Future<String>> futures = new ArrayList<>();

for (String url : urls) {  // 假设有 10,000 个 URL
    futures.add(executor.submit(() -> fetchUrl(url)));
}
// 只有 200 个线程，即使线程大部分时间都在等待 I/O 返回，整体吞吐量也会遭遇严重瓶颈

// 或者编写极其复杂的响应式代码
Flux.fromIterable(urls)
    .flatMap(url -> webClient.get().uri(url).retrieve().bodyToMono(String.class))
    .collectList()
    .block();
// 调试极其困难，心智模型与传统开发截然不同
```

## Good

```java
// Virtual threads - 逻辑简单且扩展性极强
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<String>> futures = urls.stream()
        .map(url -> executor.submit(() -> fetchUrl(url)))
        .toList();
    
    List<String> results = futures.stream()
        .map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        })
        .toList();
}  // 离开 try 代码块时自动关闭（Auto-closes），并确保等待所有子任务执行完毕

// 引入“结构化并发（Structured Concurrency）”（预览特性）后代码能更简练
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    List<StructuredTaskScope.Subtask<String>> tasks = urls.stream()
        .map(url -> scope.fork(() -> fetchUrl(url)))
        .toList();
    
    scope.join().throwIfFailed();
    
    List<String> results = tasks.stream()
        .map(StructuredTaskScope.Subtask::get)
        .toList();
}
```

## 什么时候改用虚拟线程 (When to Use Virtual Threads)

```java
// GOOD: I/O 密集型操作
// - HTTP 请求调用
// - 数据库查询
// - File I/O
// - 网络 Socket 通信

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // 同时向多个外部 API 并发请求数据
    var userFuture = executor.submit(() -> userService.getUser(id));
    var ordersFuture = executor.submit(() -> orderService.getOrders(id));
    var prefsFuture = executor.submit(() -> prefService.getPreferences(id));
    
    return new UserDashboard(
        userFuture.get(),
        ordersFuture.get(),
        prefsFuture.get()
    );
}
```

## 什么时候不该用虚拟线程 (When NOT to Use Virtual Threads)

```java
// BAD: CPU 密集型操作 - 必须使用平台线程
// 虚拟线程对计算密集型（纯 CPU 消耗）的工作没有任何性能帮助

// 针对 CPU 密集型任务，应使用 ForkJoinPool 或固定大小的平台线程池
ForkJoinPool cpuPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
cpuPool.submit(() -> computeHeavyTask());

// BAD: 带有阻塞行为的 synchronized 同步代码块
// 在执行 synchronized 逻辑时，虚拟线程会被“锁死/绑定（Pinned）”在底层的平台线程上！
synchronized (lock) {  // 导致虚拟线程发生 Pinned 锁定！
    doBlockingIO();  // 此时发生阻塞，会平白浪费一个底层的 OS 平台线程
}

// GOOD: 改用 ReentrantLock 替代 synchronized
private final ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    doBlockingIO();  // 此时即使阻塞，虚拟线程也能顺利从此处的底层内核上“卸载/让出（Unmount）”
} finally {
    lock.unlock();
}
```

## Spring Boot 框架集成 (Spring Boot Integration)

```java
// application.properties 适用于 Spring Boot 3.2+
spring.threads.virtual.enabled=true

// 或者通过 Java 代码进行显式编程式配置
@Configuration
public class VirtualThreadConfig {
    
    @Bean
    public TomcatProtocolHandlerCustomizer<?> protocolHandlerVirtualThreadCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
    
    @Bean
    public AsyncTaskExecutor asyncTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

## 最佳实践 (Best Practices)

```java
// 1. 永远不要池化（Pool）虚拟线程 —— 随用随建，用完即焚
// BAD
ExecutorService pool = Executors.newFixedThreadPool(100);  // Don't limit!
// GOOD  
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

// 2. 避免在核心 I/O 链路中利用 ThreadLocal 传递大对象
// 虚拟线程的数量动辄高达数百万级别，ThreadLocal 会导致内存急剧膨胀，扩展性极差
// 推荐使用 Scoped Values（范围值，预览特性）或直接显式传递业务上下文

// 3. 严格利用 try-with-resources 管理 Executor 的生命周期
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // 提交你的子任务
}   // 代码离开此范围时，会自动阻塞等待所有异步子任务执行完，随后安全关闭

// 4. 彻底杜绝在 I/O 核心调用链路中使用 synchronized
// 凡是涉及 I/O 的阻塞路径，一律使用 java.util.concurrent.locks 包下的锁机制替代
```
