package es.tirea.notificationservice.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.tirea.notificationservice.domain.model.GeneratedNotification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class NotificationGuardrails {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\s-]?){7,15}(?!\\d)");

    private final ObjectMapper objectMapper;
    private final Set<String> allowedChannels;
    private final int maxBodyLength;

    public NotificationGuardrails(ObjectMapper objectMapper,
                                  @Value("${app.ai.allowed-channels}") String allowedChannels,
                                  @Value("${app.ai.max-body-length}") int maxBodyLength) {
        this.objectMapper = objectMapper;
        this.allowedChannels = Arrays.stream(allowedChannels.split(","))
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
        this.maxBodyLength = maxBodyLength;
    }

    public GeneratedNotification validateAndSanitize(String rawJson, String modelName) {
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (!root.isObject()) {
                throw new IllegalArgumentException("LLM output must be a JSON object");
            }

            String channel = text(root, "channel");
            String title = text(root, "title");
            String body = text(root, "body");
            double confidence = root.path("confidence").asDouble(-1.0);

            if (!allowedChannels.contains(channel)) {
                throw new IllegalArgumentException("Channel is not allowed");
            }
            if (title.isBlank() || body.isBlank()) {
                throw new IllegalArgumentException("Title and body are mandatory");
            }
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("Confidence must be between 0 and 1");
            }

            String sanitizedBody = sanitizePii(body);
            if (sanitizedBody.length() > maxBodyLength) {
                sanitizedBody = sanitizedBody.substring(0, maxBodyLength);
            }

            return new GeneratedNotification(channel, title.trim(), sanitizedBody.trim(), confidence, modelName, false);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Guardrails rejected model output", exception);
        }
    }

    public GeneratedNotification fallbackNotification(String body, String modelName) {
        String sanitized = sanitizePii(body);
        if (sanitized.length() > maxBodyLength) {
            sanitized = sanitized.substring(0, maxBodyLength);
        }
        return new GeneratedNotification(
                "email",
                "Order received",
                sanitized,
                0.25,
                modelName,
                true
        );
    }

    private String sanitizePii(String input) {
        String noEmails = EMAIL_PATTERN.matcher(input).replaceAll("[REDACTED_EMAIL]");
        return PHONE_PATTERN.matcher(noEmails).replaceAll("[REDACTED_PHONE]");
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode node = root.path(fieldName);
        if (!node.isTextual()) {
            throw new IllegalArgumentException(fieldName + " must be a string");
        }
        return node.asText();
    }
}
