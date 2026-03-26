package es.tirea.orderservice.application.command;

import java.math.BigDecimal;

public record CreateOrderCommand(
        String product,
        Integer quantity,
        BigDecimal price
) {
}
