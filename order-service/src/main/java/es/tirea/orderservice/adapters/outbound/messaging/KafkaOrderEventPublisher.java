package es.tirea.orderservice.adapters.outbound.messaging;

import es.tirea.orderservice.domain.model.OrderCreatedEvent;
import es.tirea.orderservice.domain.port.out.OrderEventPublisherPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderEventPublisher implements OrderEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaOrderEventPublisher.class);

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final String orderEventsTopic;
    private final Counter publishedCounter;

    public KafkaOrderEventPublisher(KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate,
                                    @Value("${app.kafka.topics.order-events}") String orderEventsTopic,
                                    MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderEventsTopic = orderEventsTopic;
        this.publishedCounter = meterRegistry.counter("orders.events.published.total");
    }

    @Override
    public void publish(OrderCreatedEvent event) {
        kafkaTemplate.send(orderEventsTopic, event.orderId(), event)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Failed to publish order event {}", event.orderId(), error);
                        return;
                    }
                    publishedCounter.increment();
                    log.info("Published order event {} to topic {}", event.orderId(), orderEventsTopic);
                });
    }
}
