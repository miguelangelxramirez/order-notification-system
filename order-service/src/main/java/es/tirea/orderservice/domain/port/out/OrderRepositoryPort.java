package es.tirea.orderservice.domain.port.out;

import es.tirea.orderservice.domain.model.Order;

import java.util.List;

public interface OrderRepositoryPort {

    Order save(Order order);

    List<Order> findAll();
}
