package es.tirea.orderservice.domain.port.out;

import es.tirea.orderservice.domain.model.OrderCreatedEvent;

public interface OrderEventPublisherPort {

    void publish(OrderCreatedEvent event);
}
