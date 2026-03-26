package es.tirea.notificationservice.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataNotificationJpaRepository extends JpaRepository<NotificationJpaEntity, String> {

    boolean existsBySourceOrderIdAndChannel(String sourceOrderId, String channel);
}
