---
name: springboot-performance
description: Spring Boot + MyBatis + Redis 项目的性能优化规范。当用户编写数据库查询、实现列表接口、处理批量数据、设计缓存策略、使用连接池、处理高并发场景时触发。特别关注禁止循环查库（N+1问题）、禁止 OFFSET 深分页、缓存穿透/击穿/雪崩防护等核心性能问题。即使用户只是"写个列表查询"或"加个缓存"，也应使用此技能检查是否存在性能隐患。
---

# Spring Boot 性能优化规范

本技能定义了 Spring Boot + MyBatis + Redis 项目中必须遵守的性能规范。这些不是建议，而是底线——违反这些规则的代码在生产环境中会导致严重的性能问题。

## 红线规则（绝对禁止）

### 1. 禁止循环查库（N+1 问题）

这是最常见也最致命的性能问题。表面上代码很"清晰"，实际上一个列表请求可能触发几百次数据库查询。

```java
// ❌ 绝对禁止：循环中查数据库
List<Order> orders = orderMapper.selectList(wrapper);
for (Order order : orders) {
    // 每次循环都发一条SQL，10条订单就是10次查询
    User user = userMapper.selectById(order.getUserId());
    order.setUsername(user.getUsername());
}

// ❌ 同样禁止：stream 里查数据库（本质相同）
orders.stream().map(order -> {
    order.setUsername(userMapper.selectById(order.getUserId()).getUsername());
    return order;
}).collect(Collectors.toList());
```

正确做法是**批量查询 + 内存关联**：

```java
// ✅ 正确做法：一次查出所有需要的数据，在内存中关联
List<Order> orders = orderMapper.selectList(wrapper);

// 收集所有需要的 userId，一次性查出
Set<Long> userIds = orders.stream()
    .map(Order::getUserId)
    .collect(Collectors.toSet());
Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
    .collect(Collectors.toMap(User::getId, Function.identity()));

// 内存中关联
orders.forEach(order ->
    order.setUsername(userMap.getOrDefault(order.getUserId(), new User()).getUsername())
);
```

或者用 **JOIN 查询一步到位**：

```xml
<!-- ✅ 更优：SQL 层面直接 JOIN -->
<select id="selectOrdersWithUser" resultMap="orderWithUserMap">
    SELECT o.*, u.username
    FROM orders o
    LEFT JOIN users u ON o.user_id = u.id
    WHERE o.status = #{status}
</select>
```

**判断标准**：如果你在循环体（for/while/stream）中看到 Mapper 调用、Service 调用、或任何可能触发 SQL 的操作，就是 N+1 问题。

### 2. 禁止 OFFSET 深分页

传统分页用 `LIMIT offset, size`，当 offset 很大时（比如第 10000 页），数据库要扫描前面 99990 行再丢弃，非常慢。

```sql
-- ❌ 禁止：深分页时性能极差
SELECT * FROM request_logs ORDER BY id DESC LIMIT 10 OFFSET 100000;
-- 数据库实际扫描了 100010 行，只返回 10 行
```

#### 方案一：游标分页（推荐）

利用上一页最后一条记录的 ID 作为游标，配合索引直接定位：

```java
// ✅ 游标分页：性能稳定，不随页数增长而变慢
public List<RequestLog> getLogsByCursor(Long lastId, Integer size) {
    LambdaQueryWrapper<RequestLog> wrapper = new LambdaQueryWrapper<>();
    if (lastId != null) {
        wrapper.lt(RequestLog::getId, lastId); // WHERE id < lastId
    }
    wrapper.orderByDesc(RequestLog::getId)
           .last("LIMIT " + size);
    return requestLogMapper.selectList(wrapper);
}
```

```sql
-- 实际执行的SQL：直接通过索引定位，性能恒定
SELECT * FROM request_logs WHERE id < 12345 ORDER BY id DESC LIMIT 10;
```

#### 方案二：子查询优化（兼容传统分页 UI）

如果业务上必须支持"跳到第 N 页"，用子查询先定位主键再回表：

```xml
<!-- ✅ 子查询分页：先查主键，再回表取数据 -->
<select id="selectByPage" resultType="RequestLog">
    SELECT * FROM request_logs
    WHERE id IN (
        SELECT id FROM request_logs
        ORDER BY id DESC
        LIMIT #{size} OFFSET #{offset}
    )
    ORDER BY id DESC
</select>
```

这比直接 `SELECT *` 加 OFFSET 快得多，因为子查询只扫描主键索引（覆盖索引），不需要回表读取所有字段。

#### 方案三：限制最大页码

最简单的方案——直接不允许翻到太深的页：

```java
// 限制最大翻页深度
int maxPage = 100;
if (current > maxPage) {
    throw new BusinessException("最多支持查看前 " + maxPage + " 页数据，请使用搜索条件缩小范围");
}
```

### 3. 禁止在循环中操作 Redis

```java
// ❌ 禁止：循环中逐个操作 Redis
for (String key : keys) {
    String value = redisTemplate.opsForValue().get(key);
    // 处理...
}

// ✅ 正确：使用 Pipeline 批量操作
List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    for (String key : keys) {
        connection.stringCommands().get(key.getBytes());
    }
    return null;
});

// ✅ 或使用 multiGet
List<String> values = redisTemplate.opsForValue().multiGet(keys);
```

---

## 缓存策略规范

### 缓存使用原则

- **只缓存读多写少的数据**（如配置信息、热点数据），频繁更新的数据缓存收益低
- **缓存必须设置过期时间**，没有过期时间的缓存迟早会出问题
- **更新数据时删除缓存**，不要试图更新缓存（Cache Aside 模式）

### 缓存键命名规范

所有 Redis Key 必须以业务系统前缀开头，防止多系统共用 Redis 时 Key 冲突。详细规范参见 `springboot-coding-conventions` 技能的 Redis 章节。

```java
// 格式：{系统前缀}:{业务模块}:{数据类型}:{标识符}
// 系统前缀统一管理在 RedisKeyConstants 中

private static final String USER_KEY = "%s:user:info:%d";         // lumina:user:info:123
private static final String GROUP_KEY = "%s:group:config:%s";     // lumina:group:config:gpt-4
private static final String LOCK_KEY = "%s:lock:order:%s";        // lumina:lock:order:ORD001

// 使用时通过 buildKey 工具方法拼接前缀
String key = RedisKeyConstants.buildKey(USER_KEY, userId);
```

### 三大缓存问题防护

#### 缓存穿透（查询不存在的数据）

恶意或错误请求查询数据库中不存在的数据，每次都穿透缓存打到数据库。

```java
// ✅ 缓存空值，短过期时间
public User getUserById(Long id) {
    String key = String.format("user:info:%d", id);
    Object cached = redisTemplate.opsForValue().get(key);

    if (cached != null) {
        if ("NULL".equals(cached)) return null; // 空值标记
        return (User) cached;
    }

    User user = userMapper.selectById(id);
    if (user == null) {
        // 缓存空值，过期时间短（防止长期占用内存）
        redisTemplate.opsForValue().set(key, "NULL", 5, TimeUnit.MINUTES);
        return null;
    }

    redisTemplate.opsForValue().set(key, user, 1, TimeUnit.HOURS);
    return user;
}
```

#### 缓存击穿（热点 key 过期）

某个热点 key 突然过期，大量并发请求同时查数据库。

```java
// ✅ 使用分布式锁防击穿
public ModelGroupConfig getGroupConfig(String name) {
    String key = String.format("group:config:%s", name);
    Object cached = redisTemplate.opsForValue().get(key);
    if (cached != null) return (ModelGroupConfig) cached;

    // 只让一个线程去查数据库
    String lockKey = "lock:group:config:" + name;
    Boolean locked = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);

    if (Boolean.TRUE.equals(locked)) {
        try {
            // 双重检查
            cached = redisTemplate.opsForValue().get(key);
            if (cached != null) return (ModelGroupConfig) cached;

            ModelGroupConfig config = groupMapper.getModelGroupByName(name);
            if (config != null) {
                redisTemplate.opsForValue().set(key, config, 1, TimeUnit.HOURS);
            }
            return config;
        } finally {
            redisTemplate.delete(lockKey);
        }
    } else {
        // 其他线程短暂等待后重试
        Thread.sleep(100);
        return getGroupConfig(name);
    }
}
```

#### 缓存雪崩（大量 key 同时过期）

```java
// ✅ 过期时间加随机偏移，避免同时失效
int baseExpire = 3600; // 1小时
int randomOffset = ThreadLocalRandom.current().nextInt(0, 600); // 0~10分钟随机
redisTemplate.opsForValue().set(key, value, baseExpire + randomOffset, TimeUnit.SECONDS);
```

---

## 数据库查询优化

<!-- ### 只查需要的字段

```java
// ❌ 不好：查出所有字段（包括大文本 request_content, response_content）
List<RequestLog> logs = logMapper.selectList(wrapper);

// ✅ 好：只查需要的字段
LambdaQueryWrapper<RequestLog> wrapper = new LambdaQueryWrapper<RequestLog>()
    .select(RequestLog::getId, RequestLog::getRequestId,
            RequestLog::getStatus, RequestLog::getDtCreated)
    .eq(RequestLog::getStatus, "SUCCESS")
    .orderByDesc(RequestLog::getDtCreated);
``` -->

### 合理使用索引

```java
// ❌ 不好：在索引字段上使用函数，导致索引失效
wrapper.apply("DATE(dt_created) = '2024-01-01'");

// ✅ 好：用范围查询，索引可以生效
wrapper.ge(RequestLog::getDtCreated, LocalDateTime.of(2024, 1, 1, 0, 0))
       .lt(RequestLog::getDtCreated, LocalDateTime.of(2024, 1, 2, 0, 0));

// ❌ 不好：LIKE 左模糊，索引失效
wrapper.like(User::getUsername, "%admin");

// ✅ 好：LIKE 右模糊，索引可以生效
wrapper.likeRight(User::getUsername, "admin"); // LIKE 'admin%'
```

### 批量操作代替循环

```java
// ❌ 禁止：循环单条插入
for (GroupItem item : items) {
    groupItemMapper.insert(item);
}

// ✅ 正确：批量插入
groupItemService.saveBatch(items);

// ✅ 批量更新
groupItemService.updateBatchById(items);

// ✅ 批量删除
groupItemMapper.deleteBatchIds(ids);
```

---

## 连接池配置

### HikariCP 数据库连接池

```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 5           # 最小空闲连接数
      maximum-pool-size: 20     # 最大连接数（根据业务调整，一般 CPU核数*2 + 磁盘数）
      idle-timeout: 30000       # 空闲连接超时（30秒）
      max-lifetime: 1800000     # 连接最大存活时间（30分钟）
      connection-timeout: 30000 # 获取连接超时（30秒）
```

### Redis 连接池（Lettuce）

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 200  # 最大活跃连接
          max-idle: 10     # 最大空闲连接
          min-idle: 0      # 最小空闲连接
      timeout: 6000ms      # 命令超时
```

---

## 异步处理

对于不需要同步返回结果的操作（如日志写入、消息通知），使用异步处理避免阻塞主线程：

```java
// ✅ 异步日志写入，不阻塞请求处理
@Slf4j
@Component
public class LogWriter {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void submit(RequestLogContext ctx) {
        executor.submit(() -> {
            try {
                requestLogService.save(convert(ctx));
            } catch (Exception e) {
                log.error("日志写入失败: {}", e);
            }
        });
    }
}
```

对于 WebFlux 响应式项目，确保不在响应式链中执行阻塞操作：

```java
// ❌ 禁止：在 Mono/Flux 链中执行阻塞调用
return Mono.fromCallable(() -> {
    Thread.sleep(1000); // 阻塞操作
    return result;
});

// ✅ 正确：阻塞操作调度到专用线程池
return Mono.fromCallable(() -> blockingOperation())
    .subscribeOn(Schedulers.boundedElastic());
```

---

## 性能检查清单

编写或 Review 代码时，逐条检查：

- [ ] 是否在循环中调用了 Mapper/Service/Redis？→ 改为批量查询 + 内存关联
- [ ] 分页接口是否使用了 OFFSET？→ 大数据量场景改用游标分页
- [ ] 查询是否 SELECT *？→ 只查需要的字段
- [ ] WHERE 条件中的字段是否有索引？→ 检查执行计划
- [ ] 是否在索引字段上用了函数？→ 改为范围查询
- [ ] 缓存是否设置了过期时间？→ 必须设置
- [ ] 是否考虑了缓存穿透/击穿/雪崩？→ 按场景选择防护策略
- [ ] 批量操作是否用了 saveBatch？→ 不要循环单条插入
- [ ] 响应式链中是否有阻塞调用？→ 调度到 boundedElastic