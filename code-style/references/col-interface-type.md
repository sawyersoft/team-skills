# 集合接口类型（collection-interface-type）

> 面向接口编程：使用 List、Set、Map，而不是具体实现类
> 
## 为什么重要

使用接口类型（List、Set、Map）而不是实现类型（ArrayList、HashSet、HashMap），可以提高代码的灵活性并降低耦合。更换具体实现时，不会影响调用方。这样还能让 API 更简洁，并支持通过依赖注入在测试或不同场景上下文中使用不同的实现。

## Bad

```java
// 与特定实现绑定
public ArrayList<User> findUsers() {
    ArrayList<User> users = new ArrayList<>();
    // ...
    return users;
}

// 字段声明使用具体类型
private HashMap<String, User> userCache = new HashMap<>();

// 参数使用具体类型
public void processUsers(ArrayList<User> users) {
    for (User user : users) {
        // ...
    }
}
```

## Good

```java
// return 类型使用接口Interface类型
public List<User> findUsers() {
    List<User> users = new ArrayList<>();  // 内部实际具体实现细节
    // ...
    return users;  // 调用方只看到 List
}

// 字段类型使用interface类型
private Map<String, User> userCache = new HashMap<>();

// 参数类型使用 interface 类型
public void processUsers(List<User> users) {
    for (User user : users) {
        // ...
    }
}
// 现在可以传入 ArrayList、LinkedList 或任意 List 实现
```

## 选择合适的 Interface

```java
// Collection - 最通用的接口，很少直接使用。
Collection<User> items;

// List - 有序、允许重复、支持按索引访问
List<User> users = new ArrayList<>();
List<User> queue = new LinkedList<>();

// Set - 元素唯一，不允许重复
Set<User> uniqueUsers = new HashSet<>();
Set<User> sortedUsers = new TreeSet<>();

// Map - 键值对
Map<Long, User> usersById = new HashMap<>();
Map<String, User> sortedByName = new TreeMap<>();

// Queue - FIFO 先进先出操作
Queue<Task> taskQueue = new ArrayDeque<>();

// Deque - 双端队列（也可用作栈）
Deque<String> stack = new ArrayDeque<>();
```

## Method Signatures

```java
// 接受能够满足需求的最通用类型
public void process(Collection<User> users) {  // Accepts List, Set, etc.
    for (User user : users) {
        // ...
    }
}

// Return 最具体且有用的类型
public List<User> findUsers() {  // List 保留顺序，并支持 indexOf 等操作
    return new ArrayList<>();
}

// 对于只读数据，可以考虑返回不可修改的集合
public Set<String> getPermissions() {
    return Collections.unmodifiableSet(permissions);
}
```

## 在构造或初始化时选择具体实现

根据上下文用途 和 使用场景 选择具体实现

```java
// 快速随机访问和迭代 选择 ArrayList
List<User> users = new ArrayList<>();

// 频繁在中间插入元素（实际场景中较少见）
List<User> users = new LinkedList<>();

// 快速查找，不要求顺序
Set<User> users = new HashSet<>();

// 保持插入顺序
Set<User> users = new LinkedHashSet<>();

// 保持排序
Set<User> users = new TreeSet<>(Comparator.comparing(User::getName));

// 线程安全（并发访问）
Map<Long, User> users = new ConcurrentHashMap<>();
```

## 使用 Interface Types的好处

```java
// 1. 易于更换具体实现
private List<User> users = new ArrayList<>();
// 后续有需要时，更换为写时复制：
private List<User> users = new CopyOnWriteArrayList<>();  // 线程安全
// 这种您更换，无须修改其他代码！对调用者安全兼容。

// 2. 对依赖注入更友好
@Service
public class UserService {
    private final Map<Long, User> cache;
    
    public UserService(Map<Long, User> cache) {  // 可以注入任意 Map 实现
        this.cache = cache;
    }
}

// 3. 更易于测试
@Test
void testWithMockMap() {
    Map<Long, User> mockCache = mock(Map.class);
    UserService service = new UserService(mockCache);
    // ...
}

// 4. 提高 API 灵活性
public void importUsers(Collection<User> users) {
    // 调用方可以传入任意集合类型
}
```

## 例外：何时使用具体类型

```java
class Collections{
    // 在性能敏感且依赖特定操作的代码中
    public void sortInPlace(ArrayList<String> list) {
        // ArrayList 特有的方法
        list.trimToSize();
    }

    // 需要特定保证时
    public EnumSet<Permission> getPermissions() {
        return EnumSet.copyOf(permissions);  // EnumSet 特有的优化
    }

    // 内部实现细节
    private ArrayList<Item> items = new ArrayList<>();  // 私有字段可以使用具体类型    
}
```

# 根据具体场景选择合适的集合

不同的集合具有不同的性能特征。使用错误的集合可能会将 O(1) 操作变成 O(n)，在数据规模较大时造成严重的性能问题。理解何时使用 ArrayList 或 LinkedList、HashMap 或 TreeMap 等集合，是编写高效 Java 代码的基础。

## 快速参考

| 需求                     | 使用                | 避免              |
| ------------------------ | ------------------- | ----------------- |
| 保持顺序、按索引访问     | `ArrayList`         | `LinkedList`      |
| 频繁在中间插入或删除元素 | `LinkedList`        | `ArrayList`       |
| 根据键快速查找           | `HashMap`           | `TreeMap`         |
| 键保持有序               | `TreeMap`           | `HashMap`         |
| 元素唯一、快速判断包含   | `HashSet`           | `ArrayList`       |
| 元素唯一且保持有序       | `TreeSet`           | `HashSet`         |
| 队列（FIFO）             | `ArrayDeque`        | `LinkedList`      |
| 栈（LIFO）               | `ArrayDeque`        | `Stack`           |
| 线程安全的 Map           | `ConcurrentHashMap` | `synchronizedMap` |
| 枚举类型的键             | `EnumMap`           | `HashMap`         |
| 枚举值集合               | `EnumSet`           | `HashSet`         |

## Bad

```java
// 使用 LinkedList 进行随机访问——每次访问都是 O(n)！
List<User> users = new LinkedList<>();
for (int i = 0; i < users.size(); i++) {
    process(users.get(i));  // 每次 get 都是 O(n)！
}

// 使用 ArrayList 频繁删除首个元素——每次删除都是 O(n)！
List<Task> queue = new ArrayList<>();
while (!queue.isEmpty()) {
    Task task = queue.remove(0);  // 需要移动所有元素！
    process(task);
}

// 使用 HashMap 进行有序迭代
Map<String, Integer> scores = new HashMap<>();
// 迭代顺序不可预测！

// 需要保持顺序时使用 HashSet
Set<String> tags = new HashSet<>();
// Order not preserved!
```

## Good

```java
// 使用 ArrayList 进行索引访问和迭代
List<User> users = new ArrayList<>();
for (User user : users) {  // 处理每个元素的时间复杂度为 O(1)
    process(user);
}
users.get(100);  // O(1) 随机访问

// 使用 ArrayDeque 执行队列操作
Queue<Task> queue = new ArrayDeque<>();
while (!queue.isEmpty()) {
    Task task = queue.poll();  // O(1)
    process(task);
}

// 使用 TreeMap 保持键有序
Map<String, Integer> scores = new TreeMap<>();
// 按键的排序顺序进行迭代

// 使用 LinkedHashSet 保持插入顺序
Set<String> tags = new LinkedHashSet<>();
// 保持元素的插入顺序

// 对枚举类型的键使用 EnumMap 效率很高
Map<DayOfWeek, Schedule> schedules = new EnumMap<>(DayOfWeek.class);
```

## 集合选择指南

```java
// 1. 是否需要键值对?
//    是 -> Map 系列
//    否 -> 继续……

// 2. 是否要求元素唯一？
//    是 -> Set 系列
//    否 -> List 或 Queue 系列

// 3. 对于 List：
if (needRandomAccess || mostlyIterating) {
    return new ArrayList<>();  // 默认选择
}
if (frequentInsertRemoveInMiddle) {
    return new LinkedList<>();  // 实际场景中较少使用
}

// 4. 对于 Set：
if (needSortedOrder) {
    return new TreeSet<>();  // 操作时间复杂度为 O(log n)
}
if (needInsertionOrder) {
    return new LinkedHashSet<>();  // 保持顺序，操作时间复杂度为 O(1)
}
return new HashSet<>();  // 默认选择：操作时间复杂度为 O(1)

// 5. 对于 Map：
if (needSortedKeys) {
    return new TreeMap<>();  // 操作时间复杂度为 O(log n)
}
if (needInsertionOrder) {
    return new LinkedHashMap<>();  // 保持顺序，操作时间复杂度为 O(1)
}
if (keysAreEnums) {
    return new EnumMap<>(KeyEnum.class);  // 速度很快
}
return new HashMap<>();  // 默认选择：操作时间复杂度为 O(1)

// 6. 对于 Queue：
if (needPriorityOrdering) {
    return new PriorityQueue<>();
}
return new ArrayDeque<>();  // 默认选择：快速执行 FIFO/LIFO 操作
```

## 性能对比

```java
// ArrayList 与 LinkedList 处理 10,000 个元素时的对比
ArrayList:  get(5000) = O(1)      ~1 ns
LinkedList: get(5000) = O(n)     ~50,000 ns

// HashMap 与 TreeMap 处理 10,000 个条目时的对比
HashMap:  get(key) = O(1)        ~10 ns
TreeMap:  get(key) = O(log n)    ~100 ns

// HashSet 与 TreeSet 执行包含检查时的对比
HashSet:  contains(x) = O(1)     ~10 ns
TreeSet:  contains(x) = O(log n) ~100 ns
```

## 专用集合

```java
// EnumSet - 处理枚举值时速度极快
Set<Permission> permissions = EnumSet.of(Permission.READ, Permission.WRITE);
// 内部使用位向量——操作时间复杂度为 O(1)，内存占用很小

// EnumMap - 针对枚举类型的键进行优化
Map<Status, Handler> handlers = new EnumMap<>(Status.class);
// 基于数组实现，无须计算哈希值

// IdentityHashMap - 使用 == 而不是 equals()
Map<Object, String> identityMap = new IdentityHashMap<>();
// 适用于采用对象身份语义的对象map

// WeakHashMap - 键被垃圾回收后，对应条目也会被移除
Map<Object, Metadata> cache = new WeakHashMap<>();
// 适用于不应阻止对象被垃圾回收的缓存
```
