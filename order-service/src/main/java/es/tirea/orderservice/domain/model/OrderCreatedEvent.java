package es.tirea.orderservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(
        String orderId,
        String product,
        Integer quantity,
        BigDecimal price,
        Instant createdAt
) {
}
