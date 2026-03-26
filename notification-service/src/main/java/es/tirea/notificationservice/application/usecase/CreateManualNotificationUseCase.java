package es.tirea.notificationservice.application.usecase;

import es.tirea.notificationservice.domain.model.NotificationRecord;

public interface CreateManualNotificationUseCase {

    NotificationRecord createManualNotification(String message);
}
