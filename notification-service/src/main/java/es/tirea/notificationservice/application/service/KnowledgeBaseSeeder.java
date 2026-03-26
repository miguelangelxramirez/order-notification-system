package es.tirea.notificationservice.application.service;

import es.tirea.notificationservice.domain.model.KnowledgeChunk;
import es.tirea.notificationservice.domain.port.out.EmbeddingPort;
import es.tirea.notificationservice.domain.port.out.VectorStorePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class KnowledgeBaseSeeder implements ApplicationRunner {

    private final VectorStorePort vectorStorePort;
    private final EmbeddingPort embeddingPort;
    private final int dimension;

    public KnowledgeBaseSeeder(VectorStorePort vectorStorePort,
                               EmbeddingPort embeddingPort,
                               @Value("${app.ai.embedding-dimension}") int dimension) {
        this.vectorStorePort = vectorStorePort;
        this.embeddingPort = embeddingPort;
        this.dimension = dimension;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:seed-docs/*.md");
        for (Resource resource : resources) {
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String filename = resource.getFilename() == null ? "unknown.md" : resource.getFilename();
            double[] embedding = embeddingPort.embed(content);
            if (embedding.length != dimension) {
                throw new IllegalStateException("Embedding dimension mismatch while seeding knowledge base");
            }
            vectorStorePort.upsert(new KnowledgeChunk(filename, filename, content), embedding);
        }
    }
}
