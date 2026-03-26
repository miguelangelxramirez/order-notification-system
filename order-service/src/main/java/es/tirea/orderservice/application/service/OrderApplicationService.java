package es.tirea.orderservice.application.service;

import es.tirea.orderservice.application.command.CreateOrderCommand;
import es.tirea.orderservice.application.usecase.CreateOrderUseCase;
import es.tirea.orderservice.application.usecase.ListOrdersUseCase;
import es.tirea.orderservice.domain.model.Order;
import es.tirea.orderservice.domain.model.OrderCreatedEvent;
import es.tirea.orderservice.domain.port.out.OrderEventPublisherPort;
import es.tirea.orderservice.domain.port.out.OrderRepositoryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderApplicationService implements CreateOrderUseCase, ListOrdersUseCase {

    private final OrderRepositoryPort orderRepositoryPort;
    private final OrderEventPublisherPort orderEventPublisherPort;
    private final Counter orderCreatedCounter;

    public OrderApplicationService(OrderRepositoryPort orderRepositoryPort,
                                   OrderEventPublisherPort orderEventPublisherPort,
                                   MeterRegistry meterRegistry) {
        this.orderRepositoryPort = orderRepositoryPort;
        this.orderEventPublisherPort = orderEventPublisherPort;
        this.orderCreatedCounter = meterRegistry.counter("orders.created.total");
    }

    @Override
    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        Order order = new Order(
                UUID.randomUUID().toString(),
                command.product(),
                command.quantity(),
                command.price(),
                Instant.now()
        );

        Order saved = orderRepositoryPort.save(order);
        orderEventPublisherPort.publish(new OrderCreatedEvent(
                saved.id(),
                saved.product(),
                saved.quantity(),
                saved.price(),
                saved.createdAt()
        ));
        orderCreatedCounter.increment();
        return saved;
    }

    @Override
    public List<Order> listOrders() {
        return orderRepositoryPort.findAll();
    }
}
