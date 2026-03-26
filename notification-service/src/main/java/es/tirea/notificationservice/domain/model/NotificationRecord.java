package es.tirea.notificationservice.domain.model;

import java.time.Instant;

public record NotificationRecord(
        String id,
        String sourceOrderId,
        String source,
        String channel,
        String title,
        String body,
        double confidence,
        String modelUsed,
        long latencyMs,
        String traceId,
        boolean fallback,
        Instant createdAt
) {
}
