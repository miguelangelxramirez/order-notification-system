package es.tirea.notificationservice.application.usecase;

import es.tirea.notificationservice.domain.model.NotificationRecord;

import java.util.List;

public interface ListNotificationsUseCase {

    List<NotificationRecord> listNotifications();
}
