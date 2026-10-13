# code-early-return

> 使用提前 return 减少嵌套层级(nesting)

## 为什么重要

深层嵌套的代码难以阅读、理解和维护。提前返回（守卫子句）会优先处理边界情况和无效条件，使主逻辑保持在较低的缩进层级。这能形成一条易于理解的“正常路径”。

## Bad

```java
public Result processOrder(Order order) {
    if (order != null) {
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            if (order.getCustomer() != null) {
                if (order.getCustomer().isActive()) {
                    if (order.getPayment() != null) {
                        if (order.getPayment().isValid()) {
                            // 最终才进入实际逻辑——嵌套层级很深！
                            BigDecimal total = calculateTotal(order);
                            processPayment(order.getPayment(), total);
                            shipItems(order.getItems());
                            return Result.success();
                        } else {
                            return Result.failure("Invalid payment");
                        }
                    } else {
                        return Result.failure("No payment");
                    }
                } else {
                    return Result.failure("Customer inactive");
                }
            } else {
                return Result.failure("No customer");
            }
        } else {
            return Result.failure("No items");
        }
    } else {
        return Result.failure("No order");
    }
}
```

## Good

```java
public Result processOrder(Order order) {
     // 守卫子句——优先处理无效情况
    if (order == null) {
        return Result.failure("No order");
    }
    if (order.getItems() == null || order.getItems().isEmpty()) {
        return Result.failure("No items");
    }
    if (order.getCustomer() == null) {
        return Result.failure("No customer");
    }
    if (!order.getCustomer().isActive()) {
        return Result.failure("Customer inactive");
    }
    if (order.getPayment() == null) {
        return Result.failure("No payment");
    }
    if (!order.getPayment().isValid()) {
        return Result.failure("Invalid payment");
    }
    
    // 主逻辑——没有嵌套，易于理解
    BigDecimal total = calculateTotal(order);
    processPayment(order.getPayment(), total);
    shipItems(order.getItems());
    return Result.success();
}
```

## With Validation Methods

```java
public Result processOrder(Order order) {
    // 提取校验逻辑后更清晰
    Optional<String> validationError = validateOrder(order);
    if (validationError.isPresent()) {
        return Result.failure(validationError.get());
    }
    
    // 主逻辑
    BigDecimal total = calculateTotal(order);
    processPayment(order.getPayment(), total);
    shipItems(order.getItems());
    return Result.success();
}

private Optional<String> validateOrder(Order order) {
    if (order == null) return Optional.of("No order");
    if (order.getItems() == null || order.getItems().isEmpty()) {
        return Optional.of("No items");
    }
    if (order.getCustomer() == null) return Optional.of("No customer");
    if (!order.getCustomer().isActive()) return Optional.of("Customer inactive");
    if (order.getPayment() == null) return Optional.of("No payment");
    if (!order.getPayment().isValid()) return Optional.of("Invalid payment");
    return Optional.empty();
}
```

## For Loops

```java
// Bad - 循环中存在嵌套条件
public void processUsers(List<User> users) {
    for (User user : users) {
        if (user != null) {
            if (user.isActive()) {
                if (user.hasPermission("process")) {
                    // 处理用户——嵌套层级很深
                    doSomething(user);
                }
            }
        }
    }
}

// Good - 使用 continue 提前跳过
public void processUsers(List<User> users) {
    for (User user : users) {
        if (user == null) continue;
        if (!user.isActive()) continue;
        if (!user.hasPermission("process")) continue;
        
        // 处理用户 - 没有嵌套
        doSomething(user);
    }
}

// Or with streams
public void processUsers(List<User> users) {
    users.stream()
        .filter(Objects::nonNull)
        .filter(User::isActive)
        .filter(u -> u.hasPermission("process"))
        .forEach(this::doSomething);
}
```

## Void Methods

```java
// Bad
public void sendNotification(User user, String message) {
    if (user != null) {
        if (user.isActive()) {
            if (user.hasEmail()) {
                // Send notification
                emailService.send(user.getEmail(), message);
            }
        }
    }
}

// Good - early return from void
public void sendNotification(User user, String message) {
    if (user == null) return;
    if (!user.isActive()) return;
    if (!user.hasEmail()) return;
    
    emailService.send(user.getEmail(), message);
}

// Or with logging
public void sendNotification(User user, String message) {
    if (user == null) {
        log.debug("Cannot send notification: user is null");
        return;
    }
    if (!user.isActive()) {
        log.debug("Skipping notification for inactive user: {}", user.getId());
        return;
    }
    if (!user.hasEmail()) {
        log.debug("User {} has no email", user.getId());
        return;
    }
    
    emailService.send(user.getEmail(), message);
}
```

## With Exceptions

```java
// Bad
public User findUser(long id) {
    User user = repository.findById(id);
    if (user != null) {
        if (user.isActive()) {
            return user;
        } else {
            throw new UserInactiveException(id);
        }
    } else {
        throw new UserNotFoundException(id);
    }
}

// Good
public User findUser(long id) {
    User user = repository.findById(id);
    if (user == null) {
        throw new UserNotFoundException(id);
    }
    if (!user.isActive()) {
        throw new UserInactiveException(id);
    }
    return user;
}
```

## Optional Pattern

```java
// Combine with Optional for cleaner flow
public Optional<ProcessedOrder> processOrder(Order order) {
    return Optional.ofNullable(order)
        .filter(o -> o.getItems() != null && !o.getItems().isEmpty())
        .filter(o -> o.getCustomer() != null && o.getCustomer().isActive())
        .filter(o -> o.getPayment() != null && o.getPayment().isValid())
        .map(this::executeProcessing);
}
```
