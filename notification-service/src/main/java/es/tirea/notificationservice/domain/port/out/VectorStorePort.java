package es.tirea.notificationservice.domain.port.out;

import es.tirea.notificationservice.domain.model.KnowledgeChunk;

import java.util.List;

public interface VectorStorePort {

    void upsert(KnowledgeChunk chunk, double[] embedding);

    List<KnowledgeChunk> searchSimilar(double[] embedding, int topK);
}
