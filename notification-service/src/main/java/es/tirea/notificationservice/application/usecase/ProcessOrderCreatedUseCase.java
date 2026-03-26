package es.tirea.notificationservice.application.usecase;

import es.tirea.notificationservice.domain.model.OrderCreatedEvent;

public interface ProcessOrderCreatedUseCase {

    void process(OrderCreatedEvent event);
}
