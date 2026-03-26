package es.tirea.orderservice.application.usecase;

import es.tirea.orderservice.domain.model.Order;

import java.util.List;

public interface ListOrdersUseCase {

    List<Order> listOrders();
}
