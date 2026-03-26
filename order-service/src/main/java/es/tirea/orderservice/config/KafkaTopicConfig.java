package es.tirea.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderEventsTopic(@Value("${app.kafka.topics.order-events}") String orderEventsTopicName) {
        return TopicBuilder.name(orderEventsTopicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
