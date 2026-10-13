# anti-null-return

返回集合或数组的方法不要返回 null

## Why It Matters

返回 null 会迫使调用方到处添加 null 检查，产生难以阅读的防御性代码。
一旦遗漏检查，还容易引发 NullPointerException。空集合可以安全地进行迭代、过滤和映射，也能与 Stream 及其他函数式操作自然组合。
Optional 类型则在类型系统中明确地表示了可能不存在的情况，强制调用者处理这两种情况。这有助于编写更健壮的代码，并实现更清晰的 API。

## Bad

```java
public List<User> findUsersByRole(Role role) {
    List<User> users = repository.findByRole(role);
    if (users.isEmpty()) {
        return null;  // 调用方必须检查 null！
    }
    return users;
}

public String[] getTags() {
    if (tags == null) {
        return null;  // Dangerous!
    }
    return tags.toArray(new String[0]);
}

// Caller - must add defensive null checks everywhere
List<User> users = findUsersByRole(Role.ADMIN);
if (users != null) {  // 很容易遗漏！
    for (User user : users) {
        // process
    }
}

// 或者直接崩溃
findUsersByRole(Role.ADMIN).forEach(this::process);  // 如果返回 null，则抛出 NPE！
```

## Good

```java
public List<User> findUsersByRole(Role role) {
    List<User> users = repository.findByRole(role);
    if (users == null) {
        return Collections.emptyList();  // 安全的空列表
    }
    return users;
}

// 或者更简洁地写
public List<User> findUsersByRole(Role role) {
    List<User> users = repository.findByRole(role);
    return users != null ? users : Collections.emptyList();
}

// 或者使用 Optional.ofNullable
public List<User> findUsersByRole(Role role) {
    return Optional.ofNullable(repository.findByRole(role))
        .orElse(Collections.emptyList());
}

public String[] getTags() {
    if (tags == null || tags.isEmpty()) {
        return new String[0];  // Empty array
    }
    return tags.toArray(new String[0]);
}

// 调用方 - 不需要检查 null
findUsersByRole(Role.ADMIN).forEach(this::process);  // Safe!

// 可以与 Stream 组合使用
long count = findUsersByRole(Role.ADMIN).stream()
    .filter(User::isActive)
    .count();  // 即使集合为空也能正常工作
```

## Empty Collection Constants

```java
// 使用这些方法，而不是创建新的空集合
Collections.emptyList()     // 不可变的空 List
Collections.emptySet()      // 不可变的空 Set
Collections.emptyMap()      // 不可变的空 Map

// Java 9+ 工厂方法
List.of()                   // 不可变的空 List
Set.of()                    // 不可变的空 Set
Map.of()                    // 不可变的空 Map

// 数组
new String[0]               // 空 String 数组
new int[0]                  // 空 int 数组
```

## API 设计

```java
// 在文档中明确说明这一保证
/**
 * 返回与指定角色匹配的用户。
 * 
 * @param role 用于筛选的角色
 * @return 匹配的用户列表，永不为 null，但可能为空
 */
public List<User> findUsersByRole(Role role) {
    // 实现应确保返回值不为 null
}

// 使用 @Nonnull 注解
@Nonnull
public List<User> findUsersByRole(@Nonnull Role role) {
    // ...
}
```

## Optional的正确用法

```java
// 对于可能不存在的单个值，使用 Optional
public Optional<User> findUserById(long id) {
    User user = repository.findById(id);
    return Optional.ofNullable(user);
}

// 不要用于集合 — Optional<List> 使用起来很别扭
// BAD
public Optional<List<User>> findUsers() { ... }

// GOOD
public List<User> findUsers() {
    return users != null ? users : List.of();
}

// BAD: 用于类属性字段 fields，应该用null或者 null object 模式
class User {
    private Optional<Address> address;  // BAD - 没有必要的 overload
    private Address address;             // GOOD - 对于属性字段来说可以直接使用 null 值
}

// BAD: 用于方法或函数参数；
void process(Optional<String> value);  // BAD - 只有开销没有好处
void process(String value);            // GOOD

// BAD: 用户集合，应该直接返回空集合即可
Optional<List<User>> findUsers();     // BAD
List<User> findUsers();               // GOOD - 返回空 list
```

## 处理 Repository 可能返回 null 的情况

```java
@Repository
public class UserRepository {
    
    // 某些 方法可能返回 null
    public List<User> findByStatus(Status status) {
        List<User> result = entityManager
            .createQuery("SELECT u FROM User u WHERE u.status = :status", User.class)
            .setParameter("status", status)
            .getResultList();
        
        // getResultList() 返回空列表，而不是 null
        // 但如果不确定，应该提供防御性措施
        return result != null ? result : List.of();
    }
}

// Service 层 - 始终返回非 null 值
@Service
public class UserService {
    
    public List<User> findActiveUsers() {
        List<User> users = repository.findByStatus(Status.ACTIVE);
        // 即使 Repository 可能返回 null，也要保护调用方
        return users != null ? users : List.of();
    }
}
```
