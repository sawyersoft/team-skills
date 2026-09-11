// ✅ GOOD — 充血领域模型、强类型 ID、行为方法、领域事件，并且没有公共 setter

public class Order {

    private final OrderId id;
    private final CustomerId customerId;
    private final EmailAddress customerEmail;
    private OrderStatus status;
    private Money total;
    private final List<OrderItem> items = new ArrayList<>();
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    // 静态工厂——确保初始状态有效
    public static Order create(CustomerId customerId, EmailAddress email) {
        Order order = new Order(OrderId.generate(), customerId, email, OrderStatus.DRAFT, Money.ZERO);
        return order;
    }

    // 行为方法负责维护不变式
    public void addItem(ProductId productId, String name, int quantity, Money unitPrice) {
        if (status != OrderStatus.DRAFT)
            throw new OrderNotModifiableException(id);
        if (quantity <= 0)
            throw new InvalidQuantityException(quantity);
        items.add(new OrderItem(productId, name, quantity, unitPrice));
        recalculateTotal();
    }

    public void place() {
        if (items.isEmpty())
            throw new EmptyOrderException(id);
        if (status != OrderStatus.DRAFT)
            throw new IllegalOrderStateTransitionException(status, OrderStatus.PLACED);
        this.status = OrderStatus.PLACED;
        domainEvents.add(OrderPlaced.of(this));
    }

    public void cancel(String reason) {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED)
            throw new IllegalOrderStateTransitionException(status, OrderStatus.CANCELLED);
        this.status = OrderStatus.CANCELLED;
        domainEvents.add(new OrderCancelled(id, customerId, reason, Instant.now()));
    }

    public List<Object> pullDomainEvents() {
        var events = List.copyOf(domainEvents);
        domainEvents.clear();
        return events;
    }

    private void recalculateTotal() {
        this.total = items.stream()
            .map(OrderItem::lineTotal)
            .reduce(Money.ZERO, Money::add);
    }

    // 不提供公共 setter——只能通过行为方法改变状态
    public OrderId getId() { return id; }
    public OrderStatus getStatus() { return status; }
    public Money getTotal() { return total; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
}

// 强类型 ID——值对象
public record OrderId(UUID value) {
    public static OrderId generate() { return new OrderId(UUID.randomUUID()); }
    public static OrderId of(UUID value) { return new OrderId(Objects.requireNonNull(value)); }
}

public abstract class AggregateRoot<T> {
    private final T id;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    // Constructor
    protected AggregateRoot(T id) {
        this.id = id;
    }

    // Getter for the aggregate's domain events
    public List<DomainEvent> getDomainEvents() {
        return domainEvents;
    }

    // Apply a domain event to the aggregate
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    // Clear domain events after they have been handled
    public void clearDomainEvents() {
        domainEvents.clear();
    }

    // Getter for the aggregate root's identifier
    public T getId() {
        return id;
    }
}

