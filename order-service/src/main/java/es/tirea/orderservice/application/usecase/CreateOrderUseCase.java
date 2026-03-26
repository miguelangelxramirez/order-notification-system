package es.tirea.orderservice.application.usecase;

import es.tirea.orderservice.application.command.CreateOrderCommand;
import es.tirea.orderservice.domain.model.Order;

public interface CreateOrderUseCase {

    Order createOrder(CreateOrderCommand command);
}
