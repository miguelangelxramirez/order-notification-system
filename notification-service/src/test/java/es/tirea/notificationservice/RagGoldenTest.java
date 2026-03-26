package es.tirea.notificationservice;

import es.tirea.notificationservice.domain.model.KnowledgeChunk;
import es.tirea.notificationservice.domain.port.out.EmbeddingPort;
import es.tirea.notificationservice.domain.port.out.VectorStorePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class RagGoldenTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withInitScript("sql/notification-service-test-init.sql");

    @Autowired
    VectorStorePort vectorStorePort;

    @Autowired
    EmbeddingPort embeddingPort;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("app.ai.provider", () -> "fake");
    }

    @Test
    void retrievesSecurityDocumentForPiiQuery() {
        double[] embedding = embeddingPort.embed("phone numbers and email addresses must not leak");
        List<KnowledgeChunk> chunks = vectorStorePort.searchSimilar(embedding, 3);

        assertThat(chunks).extracting(KnowledgeChunk::externalId)
                .contains("security.md");
    }
}
