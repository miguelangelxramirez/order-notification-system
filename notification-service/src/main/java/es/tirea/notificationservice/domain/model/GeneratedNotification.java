package es.tirea.notificationservice.domain.model;

public record GeneratedNotification(
        String channel,
        String title,
        String body,
        double confidence,
        String modelUsed,
        boolean fallback
) {
}
