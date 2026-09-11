// ❌ 错误——贫血模型、没有不变式约束、没有强类型 ID，也没有领域事件

@Entity
@Data                                        // 生成公共 setter——任何代码都能修改状态
@NoArgsConstructor                           // 公共无参构造器允许创建无效对象
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                         // Long ID——不是强类型值对象

    private Long customerId;                 // Long——应使用 CustomerId 值对象

    private String customerEmail;            // 原始 String——应使用 EmailAddress 值对象

    @Enumerated(EnumType.ORDINAL)            // 调整枚举顺序会破坏 ORDINAL 映射
    private OrderStatus status;

    private BigDecimal total;                // 原始 BigDecimal——应使用 Money 值对象

    @OneToMany(mappedBy = "order")           // 缺少 cascade 和 orphanRemoval
    private List<OrderItem> items;           // 未初始化——存在 NPE 风险

    // 没有行为方法——所有逻辑都会放入服务：
    // - 没有 addItem() 方法
    // - 没有 place() 方法
    // - 没有状态转换校验
    // - 没有领域事件
    // - 总金额由外部手动设置，而不是自行计算

    // 外部代码会这样操作：
    // order.getItems().add(new OrderItem(...));  // 绕过聚合边界
    // order.setStatus(OrderStatus.PLACED);       // 没有不变式检查
    // order.setTotal(calculateTotal(items));      // 逻辑位于领域之外
}
