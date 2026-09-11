package com.example.common.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 所有领域事件的基础接口。
 * 事件是领域中已发生事项的不可变事实。
 */
public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();
}

// --- 使用 record 定义具体领域事件 ---

package com.example.order.domain.event;

import com.example.common.domain.DomainEvent;
import com.example.order.domain.model.CustomerId;
import com.example.order.domain.model.Money;
import com.example.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

public record OrderPlaced(
    UUID eventId,
    Instant occurredAt,
    OrderId orderId,
    CustomerId customerId,
    Money total
) implements DomainEvent {

    public static OrderPlaced of(Order order) {
        return new OrderPlaced(
            UUID.randomUUID(),
            Instant.now(),
            order.getId(),
            order.getCustomerId(),
            order.getTotal()
        );
    }
}

public record OrderCancelled(
    UUID eventId,
    Instant occurredAt,
    OrderId orderId,
    CustomerId customerId,
    String reason
) implements DomainEvent {

    public OrderCancelled(OrderId orderId, CustomerId customerId, String reason, Instant occurredAt) {
        this(UUID.randomUUID(), occurredAt, orderId, customerId, reason);
    }
}
