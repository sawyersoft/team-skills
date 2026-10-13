# anti-god-class
避免职责过多的上帝类

## 为什么重要
上帝类是指知道得太多或承担过多职责的类。它违反了单一职责原则，使代码难以理解、测试和维护。修改一个功能时，可能破坏其他功能。上帝类还表明领域建模不佳，并阻碍有效的代码复用。

## Bad

```java
// God class - does everything related to users
public class UserManager {
    // User CRUD
    public User createUser(String name, String email) { ... }
    public User findUser(long id) { ... }
    public void updateUser(User user) { ... }
    public void deleteUser(long id) { ... }
    
    // Authentication
    public boolean authenticate(String email, String password) { ... }
    public void logout(User user) { ... }
    public String generateToken(User user) { ... }
    public User validateToken(String token) { ... }
    
    // Authorization
    public boolean hasPermission(User user, String permission) { ... }
    public void grantRole(User user, Role role) { ... }
    public void revokeRole(User user, Role role) { ... }
    
    // Email
    public void sendWelcomeEmail(User user) { ... }
    public void sendPasswordResetEmail(User user) { ... }
    public void sendNotification(User user, String message) { ... }
    
    // Reporting
    public List<User> getActiveUsers() { ... }
    public Map<String, Long> getUserStatistics() { ... }
    public void generateUserReport() { ... }
    
    // ... 50+ more methods
}
// 1000+ lines, untestable, unmaintainable
```

## Good

```java
// 按职责拆分独立

// User persistence
@Repository
public class UserRepository {
    public Optional<User> findById(long id) { ... }
    public User save(User user) { ... }
    public void delete(User user) { ... }
    public List<User> findAll() { ... }
}

// User business logic
@Service
public class UserService {
    private final UserRepository repository;
    private final PasswordEncoder encoder;
    
    public User createUser(CreateUserRequest request) {
        User user = new User(request.getName(), request.getEmail());
        user.setPassword(encoder.encode(request.getPassword()));
        return repository.save(user);
    }
    
    public Optional<User> findById(long id) {
        return repository.findById(id);
    }
}

// Authentication
@Service
public class AuthenticationService {
    private final UserRepository userRepository;
    private final TokenService tokenService;
    
    public AuthResult authenticate(String email, String password) { ... }
    public void logout(String token) { ... }
}

// Token management
@Service
public class TokenService {
    public String generate(User user) { ... }
    public Optional<User> validate(String token) { ... }
}

// Authorization
@Service
public class AuthorizationService {
    public boolean hasPermission(User user, Permission permission) { ... }
    public void grantRole(User user, Role role) { ... }
}

// Notifications
@Service
public class NotificationService {
    private final EmailSender emailSender;
    
    public void sendWelcomeEmail(User user) { ... }
    public void sendPasswordReset(User user) { ... }
}

// Reporting
@Service
public class UserReportingService {
    public UserStatistics getStatistics() { ... }
    public Report generateReport(ReportCriteria criteria) { ... }
}
```

## 上帝类的特点
- 引用或依赖外部类过多（超过 10~15 个）
- 方法过多（超过10个）
- 代码行数过多（超过 1000 行）
- 将无关的方法归在一起
- 难以用简洁的名称概括

```java
// 1. Too many fields (> 10-15)
public class OrderProcessor {
    private UserService userService;
    private ProductService productService;
    private InventoryService inventoryService;
    private PaymentService paymentService;
    private ShippingService shippingService;
    private NotificationService notificationService;
    private ReportingService reportingService;
    private AuditService auditService;
    // ... many more
}

```

## 单一职责原则 （Single Responsibility Principle）

```java
// 每个类只有一个变更原因

// Bad: 修改 电子邮件格式、用户校验规则 或 持久化，都需要修改 统一个类，例如：UserManager

// Good: 
// - Email 格式变更？只修改 EmailService
// - 校验规则变更？只修改 UserCreateCmd
// - 数据库模式变更？只修改 UserRepository

@Service
public class UserService {
    // 仅处理与用户相关的业务逻辑
    // 将其他关注点委托给协作者
}
```




