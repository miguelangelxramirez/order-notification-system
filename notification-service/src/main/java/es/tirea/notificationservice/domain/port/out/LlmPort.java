package es.tirea.notificationservice.domain.port.out;

public interface LlmPort {

    String generateNotificationJson(String systemPrompt, String userPrompt, String retrievedContext);

    String modelName();
}
