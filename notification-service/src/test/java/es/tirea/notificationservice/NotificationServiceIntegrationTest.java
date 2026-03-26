package es.tirea.notificationservice;

import es.tirea.notificationservice.domain.model.NotificationRecord;
import es.tirea.notificationservice.domain.model.OrderCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withInitScript("sql/notification-service-test-init.sql");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Autowired
    TestRestTemplate restTemplate;

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("app.ai.provider", () -> "fake");
    }

    @Test
    void consumesKafkaEventAndPersistsNotification() throws Exception {
        kafkaTemplate.send("order-events", "order-1", new OrderCreatedEvent(
                "order-1",
                "Laptop Stand",
                1,
                new BigDecimal("39.99"),
                Instant.now()
        )).get();

        Thread.sleep(3000L);

        NotificationRecord[] notifications = restTemplate.getForObject("http://localhost:" + port + "/notifications", NotificationRecord[].class);

        assertThat(notifications).isNotNull();
        assertThat(notifications).isNotEmpty();
        assertThat(notifications[0].source()).isEqualTo("kafka");
        assertThat(notifications[0].modelUsed()).isEqualTo("fake-llm-v1");
    }
}
