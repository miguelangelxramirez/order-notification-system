package es.tirea.notificationservice.adapters.outbound.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.tirea.notificationservice.domain.port.out.LlmPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "bedrock")
public class BedrockLlmAdapter implements LlmPort {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;
    private final String modelId;

    public BedrockLlmAdapter(BedrockRuntimeClient bedrockRuntimeClient,
                             ObjectMapper objectMapper,
                             @Value("${app.ai.bedrock.text-model-id}") String modelId) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.objectMapper = objectMapper;
        this.modelId = modelId;
    }

    @Override
    public String generateNotificationJson(String systemPrompt, String userPrompt, String retrievedContext) {
        try {
            Map<String, Object> payload = Map.of(
                    "anthropic_version", "bedrock-2023-05-31",
                    "system", systemPrompt,
                    "max_tokens", 512,
                    "messages", List.of(
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text", userPrompt + "\n\n" + retrievedContext)
                            ))
                    )
            );
            InvokeModelResponse response = bedrockRuntimeClient.invokeModel(InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(objectMapper.writeValueAsString(payload)))
                    .build());
            JsonNode root = objectMapper.readTree(response.body().asUtf8String());
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                return content.get(0).path("text").asText();
            }
            throw new IllegalStateException("Unexpected Bedrock LLM response");
        } catch (Exception exception) {
            throw new IllegalStateException("Bedrock generation call failed", exception);
        }
    }

    @Override
    public String modelName() {
        return modelId;
    }
}
