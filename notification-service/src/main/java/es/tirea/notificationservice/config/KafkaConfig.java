package es.tirea.notificationservice.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic orderEventsTopic(@Value("${app.kafka.topics.order-events}") String topicName) {
        return TopicBuilder.name(topicName).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderEventsDlqTopic(@Value("${app.kafka.topics.order-events-dlq}") String topicName) {
        return TopicBuilder.name(topicName).partitions(3).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations,
                                                 MeterRegistry meterRegistry,
                                                 @Value("${app.kafka.topics.order-events-dlq}") String dlqTopic,
                                                 @Value("${app.kafka.retry.max-attempts}") long maxAttempts,
                                                 @Value("${app.kafka.retry.backoff-ms}") long backoffMs) {
        Counter dlqCounter = meterRegistry.counter("notifications.events.dlq.total");
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (ConsumerRecord<?, ?> record, Exception exception) -> {
                    dlqCounter.increment();
                    return new org.apache.kafka.common.TopicPartition(dlqTopic, record.partition());
                });
        return new DefaultErrorHandler(recoverer, new FixedBackOff(backoffMs, maxAttempts - 1));
    }
}
