package es.tirea.notificationservice.domain.port.out;

import es.tirea.notificationservice.domain.model.NotificationRecord;

import java.util.List;

public interface NotificationRepositoryPort {

    boolean existsBySourceOrderIdAndChannel(String sourceOrderId, String channel);

    NotificationRecord save(NotificationRecord notificationRecord);

    List<NotificationRecord> findAll();
}
