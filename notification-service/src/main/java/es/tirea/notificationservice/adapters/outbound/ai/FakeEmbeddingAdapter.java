package es.tirea.notificationservice.adapters.outbound.ai;

import es.tirea.notificationservice.domain.port.out.EmbeddingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "fake", matchIfMissing = true)
public class FakeEmbeddingAdapter implements EmbeddingPort {

    private final int dimension;

    public FakeEmbeddingAdapter(@Value("${app.ai.embedding-dimension}") int dimension) {
        this.dimension = dimension;
    }

    @Override
    public double[] embed(String input) {
        double[] vector = new double[dimension];
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int slot = i % dimension;
            vector[slot] += (bytes[i] & 0xFF) / 255.0;
        }
        return vector;
    }

    @Override
    public String modelName() {
        return "fake-embedding-v1";
    }
}
