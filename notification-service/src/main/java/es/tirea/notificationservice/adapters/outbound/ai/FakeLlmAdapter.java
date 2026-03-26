package es.tirea.notificationservice.adapters.outbound.ai;

import es.tirea.notificationservice.domain.port.out.LlmPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "fake", matchIfMissing = true)
public class FakeLlmAdapter implements LlmPort {

    @Override
    public String generateNotificationJson(String systemPrompt, String userPrompt, String retrievedContext) {
        String title = "Order confirmed";
        String body = "Your order has been received and is being prepared.";
        if (retrievedContext.toLowerCase().contains("polite tone")) {
            body = "Thank you for your purchase. Your order has been received and is being prepared.";
        }
        return """
                {
                  "channel": "email",
                  "title": "%s",
                  "body": "%s",
                  "confidence": 0.93
                }
                """.formatted(title, body);
    }

    @Override
    public String modelName() {
        return "fake-llm-v1";
    }
}
