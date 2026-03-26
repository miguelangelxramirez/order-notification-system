package es.tirea.notificationservice.adapters.outbound.persistence;

import es.tirea.notificationservice.domain.model.KnowledgeChunk;
import es.tirea.notificationservice.domain.port.out.VectorStorePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class PostgresVectorStoreAdapter implements VectorStorePort {

    private final JdbcTemplate jdbcTemplate;

    public PostgresVectorStoreAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(KnowledgeChunk chunk, double[] embedding) {
        jdbcTemplate.update("""
                INSERT INTO notifications.knowledge_chunks (id, external_id, title, content, embedding, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS vector), ?)
                ON CONFLICT (external_id) DO UPDATE
                SET title = EXCLUDED.title,
                    content = EXCLUDED.content,
                    embedding = EXCLUDED.embedding
                """,
                UUID.nameUUIDFromBytes(chunk.externalId().getBytes()),
                chunk.externalId(),
                chunk.title(),
                chunk.content(),
                toVectorLiteral(embedding),
                Timestamp.from(Instant.now()));
    }

    @Override
    public List<KnowledgeChunk> searchSimilar(double[] embedding, int topK) {
        return jdbcTemplate.query("""
                SELECT external_id, title, content
                FROM notifications.knowledge_chunks
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """,
                this::mapChunk,
                toVectorLiteral(embedding),
                topK);
    }

    private KnowledgeChunk mapChunk(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeChunk(
                rs.getString("external_id"),
                rs.getString("title"),
                rs.getString("content")
        );
    }

    private String toVectorLiteral(double[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(embedding[i]);
        }
        builder.append(']');
        return builder.toString();
    }
}
