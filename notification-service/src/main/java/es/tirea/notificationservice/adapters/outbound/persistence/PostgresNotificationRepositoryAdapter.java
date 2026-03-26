package es.tirea.notificationservice.adapters.outbound.persistence;

import es.tirea.notificationservice.domain.model.NotificationRecord;
import es.tirea.notificationservice.domain.port.out.NotificationRepositoryPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PostgresNotificationRepositoryAdapter implements NotificationRepositoryPort {

    private final SpringDataNotificationJpaRepository repository;

    public PostgresNotificationRepositoryAdapter(SpringDataNotificationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsBySourceOrderIdAndChannel(String sourceOrderId, String channel) {
        return repository.existsBySourceOrderIdAndChannel(sourceOrderId, channel);
    }

    @Override
    public NotificationRecord save(NotificationRecord notificationRecord) {
        try {
            NotificationJpaEntity saved = repository.save(toEntity(notificationRecord));
            return toDomain(saved);
        } catch (DataIntegrityViolationException duplicate) {
            return notificationRecord;
        }
    }

    @Override
    public List<NotificationRecord> findAll() {
        return repository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    private NotificationJpaEntity toEntity(NotificationRecord record) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.setId(record.id());
        entity.setSourceOrderId(record.sourceOrderId());
        entity.setSource(record.source());
        entity.setChannel(record.channel());
        entity.setTitle(record.title());
        entity.setBody(record.body());
        entity.setConfidence(record.confidence());
        entity.setModelUsed(record.modelUsed());
        entity.setLatencyMs(record.latencyMs());
        entity.setTraceId(record.traceId());
        entity.setFallback(record.fallback());
        entity.setCreatedAt(record.createdAt());
        return entity;
    }

    private NotificationRecord toDomain(NotificationJpaEntity entity) {
        return new NotificationRecord(
                entity.getId(),
                entity.getSourceOrderId(),
                entity.getSource(),
                entity.getChannel(),
                entity.getTitle(),
                entity.getBody(),
                entity.getConfidence(),
                entity.getModelUsed(),
                entity.getLatencyMs(),
                entity.getTraceId(),
                entity.isFallback(),
                entity.getCreatedAt()
        );
    }
}
