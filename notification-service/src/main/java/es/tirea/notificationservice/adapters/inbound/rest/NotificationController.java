package es.tirea.notificationservice.adapters.inbound.rest;

import es.tirea.notificationservice.adapters.inbound.rest.dto.NotificationRequest;
import es.tirea.notificationservice.application.usecase.CreateManualNotificationUseCase;
import es.tirea.notificationservice.application.usecase.ListNotificationsUseCase;
import es.tirea.notificationservice.domain.model.NotificationRecord;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final CreateManualNotificationUseCase createManualNotificationUseCase;
    private final ListNotificationsUseCase listNotificationsUseCase;

    public NotificationController(CreateManualNotificationUseCase createManualNotificationUseCase,
                                  ListNotificationsUseCase listNotificationsUseCase) {
        this.createManualNotificationUseCase = createManualNotificationUseCase;
        this.listNotificationsUseCase = listNotificationsUseCase;
    }

    @PostMapping
    public ResponseEntity<NotificationRecord> createManualNotification(@Valid @RequestBody NotificationRequest request) {
        return new ResponseEntity<>(createManualNotificationUseCase.createManualNotification(request.message()), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<NotificationRecord>> listNotifications() {
        return ResponseEntity.ok(listNotificationsUseCase.listNotifications());
    }
}
