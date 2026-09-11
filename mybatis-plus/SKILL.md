---
name: springboot-db-design
description: Spring Boot + MyBatis-Plus 项目的数据库设计与数据访问层规范。当用户创建新表、编写 SQL、设计 schema、编写 MyBatis Mapper、添加索引、处理数据库迁移时触发。也适用于用户讨论表结构设计、字段命名、关联关系设计、MyBatis XML 编写等场景。即使用户只是简单说"加个表"或"写个查询"，也应该使用此技能确保符合规范。
---

# Spring Boot 数据库设计与数据访问规范

本技能定义了 Spring Boot + MyBatis-Plus 项目中数据库设计、表结构、Mapper 编写的完整规范。目标是让所有数据库相关代码保持一致、可维护、高性能。

## 表设计规范

### 命名规则

表名和字段一律使用 **snake_case**（小写下划线），这样 MyBatis-Plus 的 `map-underscore-to-camel-case` 配置可以自动映射到 Java 驼峰字段，不需要额外的 `@TableField` 注解。

```sql
-- 正确
CREATE TABLE user_orders (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单编号',
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '订单总额',
    dt_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    dt_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户订单表';

-- 错误：驼峰命名、缺少注释、缺少时间戳
CREATE TABLE UserOrders (
    ID int PRIMARY KEY,
    userId int,
    orderNo varchar(50)
);
```

### 必备字段

每张业务表必须包含以下字段，这是保持数据可追溯性的基础：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | `BIGINT UNSIGNED AUTO_INCREMENT` | 主键，统一使用自增大整数 |
| `dt_created` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` | 创建时间 |
| `dt_updated` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` | 更新时间 |

如果业务需要软删除，添加：

| 字段 | 类型 | 说明 |
|------|------|------|
| `deleted` | `TINYINT NOT NULL DEFAULT 0` | 0=正常，1=已删除 |

### 字段设计原则

- **每个字段必须有 COMMENT**，这是给后续维护者最重要的文档
- **字符集统一 utf8mb4**，支持 emoji 和多语言
- **金额用 DECIMAL(12,2)**，绝不用 FLOAT/DOUBLE（浮点精度问题会导致资金计算错误）
- **状态字段用 TINYINT 或 VARCHAR**，配合 COMMENT 说明每个值的含义
- **布尔字段用 TINYINT**，命名用 `is_` 前缀（如 `is_enabled`）
- **VARCHAR 长度要合理估算**，不要无脑写 VARCHAR(255)

### 主键设计

- 单体应用默认使用 `INT UNSIGNED AUTO_INCREMENT`
- 分布式场景使用雪花算法（Snowflake ID），主键类型改为 `BIGINT`，配合 `@TableId(type = IdType.INPUT)`
- 不用 UUID 作为主键（索引性能差，占用空间大）

### 关联关系

```sql
-- 索引命名：idx_{字段名}
-- 联合唯一：uk_{字段1}_{字段2}
-- 不使用外键约束，关联关系由代码逻辑维护

CREATE TABLE model_group_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_id BIGINT UNSIGNED NOT NULL COMMENT '分组ID',
    provider_id BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
    model_name VARCHAR(100) NOT NULL COMMENT '模型名称',
    weight INT NOT NULL DEFAULT 1 COMMENT '权重',
    dt_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dt_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_provider_model (group_id, provider_id, model_name),
    KEY idx_group_id (group_id),
    KEY idx_provider_id (provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分组项目关联表';
```

### 不使用外键约束

数据库层面不建外键（`FOREIGN KEY`），表之间的关联关系由代码逻辑保证。原因：

- **性能**：外键约束在每次写入时都要检查关联表，高并发场景下成为瓶颈
- **灵活性**：删除/更新数据时不受外键级联限制，业务逻辑更灵活可控
- **运维**：大表加外键会导致 DDL 锁表，分库分表后外键也无法跨库生效
- **数据迁移**：外键增加了导入导出数据的复杂度

关联字段仍然要加索引（如 `idx_group_id`），保证查询性能。数据一致性由 BizManageService 层的事务保证。

### 索引设计原则

- **高频查询条件必须有索引**，但不要盲目加索引（写入性能会下降）
- **联合索引遵循最左前缀原则**，把区分度高的字段放前面
- **覆盖索引优先**：如果查询只需要索引中的字段，数据库可以直接从索引返回，不需要回表
- **不在低基数字段上单独建索引**（如 `status` 只有几个值，单独索引意义不大）
- 索引命名：普通索引 `idx_字段名`，唯一索引 `uk_字段名`

---

## Entity 实体规范

```java
@Data
@TableName("user_orders")
public class UserOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotEmpty(message = "订单编号不能为空")
    private String orderNo;

    private BigDecimal totalAmount;

    private LocalDateTime dtUpdated;
    private LocalDateTime dtUpdated;

    // 非数据库字段用 @TableField(exist = false) 标记
    @TableField(exist = false)
    private List<OrderItem> items;
}
```

关键规则：
- `@TableName` 必须显式指定表名
- `@TableId` 必须显式指定 ID 策略
- 时间字段统一用 `LocalDateTime`，不用 `Date`
- 验证注解直接写在 Entity 上（`@NotNull`, `@NotEmpty`, `@Size` 等）
- 非表字段用 `@TableField(exist = false)`
- 使用 Lombok `@Data` 简化 getter/setter

---

## Mapper 规范

### 简单 CRUD 直接继承 BaseMapper
BaseMapper 已提供 insert, selectById, updateById, deleteById 等，只在需要自定义查询时添加方法。

```java
@Mapper
public interface UserOrderMapper extends BaseMapper<UserOrder> {
    // BaseMapper 已提供 insert, selectById, updateById, deleteById 等
    // 只在需要自定义查询时添加方法
}
```

### 复杂查询用 XML Mapper

XML Mapper 适用于多表关联、嵌套结果集、动态 SQL 等场景。保持 XML 和接口方法的一一对应关系。

```xml
<mapper namespace="com.example.mapper.UserOrderMapper">

    <!-- 嵌套结果集映射 -->
    <resultMap id="orderWithItemsMap" type="com.example.entity.UserOrder">
        <id column="id" property="id"/>
        <result column="order_no" property="orderNo"/>
        <result column="total_amount" property="totalAmount"/>
        <collection property="items" ofType="com.example.entity.OrderItem">
            <id column="item_id" property="id"/>
            <result column="product_name" property="productName"/>
            <result column="quantity" property="quantity"/>
        </collection>
    </resultMap>

    <!-- 用 JOIN 一次性查出，不要在循环里单独查 items -->
    <select id="getOrderWithItems" resultMap="orderWithItemsMap">
        SELECT o.id, o.order_no, o.total_amount,
               oi.id AS item_id, oi.product_name, oi.quantity
        FROM user_orders o
        LEFT JOIN order_items oi ON o.id = oi.order_id
        WHERE o.id = #{orderId}
    </select>

</mapper>
```

### 查询编写规则

- **参数占位符统一用 `#{}`**，这会使用 PreparedStatement 预编译，防止 SQL 注入
- **绝不用 `${}`**，除非是动态表名/列名且已做白名单校验
- **多数据库兼容**：如果要同时支持 MySQL 和 SQLite，使用 `databaseId` 属性区分
- **LambdaQueryWrapper 优先**：简单的条件查询用 LambdaQueryWrapper，类型安全、重构友好

```java
// 推荐：LambdaQueryWrapper，编译期检查字段名
List<UserOrder> orders = orderMapper.selectList(
    new LambdaQueryWrapper<UserOrder>()
        .eq(UserOrder::getUserId, userId)
        .ge(UserOrder::getDtCreated, startDate)
        .orderByDesc(UserOrder::getDtCreated)
);

// 不推荐：字符串字段名，重构时容易漏改
QueryWrapper<UserOrder> wrapper = new QueryWrapper<>();
wrapper.eq("user_id", userId);
```

### 批量操作

```java
// 批量插入用 saveBatch，MyBatis Plus 会分批执行，避免单条 SQL 过长
groupItemService.saveBatch(items);

// 批量更新用 updateBatchById
groupItemService.updateBatchById(items);

// 先删再插的模式（适用于关联表更新）
groupItemService.remove(new LambdaQueryWrapper<GroupItem>()
    .eq(GroupItem::getGroupId, groupId));
groupItemService.saveBatch(newItems);
```

---

## 数据库迁移规范

- 每次 schema 变更必须写迁移脚本，放在 `src/main/resources/db/migration/` 目录
- 脚本文件名格式：`V{版本号}_{描述}.sql`（如 `V2_add_user_avatar.sql`）
- 迁移脚本只能追加，不能修改已执行的脚本
- 每个迁移脚本开头加注释说明变更内容和原因

```sql
-- V3: 添加用户头像字段
-- 原因：新增用户个人资料功能需要存储头像URL
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500) DEFAULT NULL COMMENT '头像URL' AFTER password;
```
