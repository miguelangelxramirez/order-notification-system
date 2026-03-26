package es.tirea.orderservice;

import es.tirea.orderservice.adapters.inbound.rest.dto.OrderRequest;
import es.tirea.orderservice.domain.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withInitScript("sql/order-service-test-init.sql");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void createsOrderAndPersistsIt() {
        OrderRequest request = new OrderRequest("Mechanical Keyboard", 2, new java.math.BigDecimal("89.99"));
        Order response = restTemplate.postForObject("http://localhost:" + port + "/orders", request, Order.class);

        Order[] orders = restTemplate.getForObject("http://localhost:" + port + "/orders", Order[].class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isNotBlank();
        assertThat(orders).hasSize(1);
        assertThat(orders[0].product()).isEqualTo("Mechanical Keyboard");
    }
}
