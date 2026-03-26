package es.tirea.notificationservice.adapters.inbound.messaging;

import es.tirea.notificationservice.application.usecase.ProcessOrderCreatedUseCase;
import es.tirea.notificationservice.domain.model.OrderCreatedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsKafkaListener {

    private final ProcessOrderCreatedUseCase processOrderCreatedUseCase;

    public OrderEventsKafkaListener(ProcessOrderCreatedUseCase processOrderCreatedUseCase) {
        this.processOrderCreatedUseCase = processOrderCreatedUseCase;
    }

    @KafkaListener(topics = "${app.kafka.topics.order-events}", groupId = "notification-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        processOrderCreatedUseCase.process(event);
    }
}
