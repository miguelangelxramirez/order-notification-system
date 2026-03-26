package es.tirea.notificationservice.adapters.outbound.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.tirea.notificationservice.domain.port.out.EmbeddingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "bedrock")
public class BedrockEmbeddingAdapter implements EmbeddingPort {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final ObjectMapper objectMapper;
    private final String modelId;

    public BedrockEmbeddingAdapter(BedrockRuntimeClient bedrockRuntimeClient,
                                   ObjectMapper objectMapper,
                                   @Value("${app.ai.bedrock.embedding-model-id}") String modelId) {
        this.bedrockRuntimeClient = bedrockRuntimeClient;
        this.objectMapper = objectMapper;
        this.modelId = modelId;
    }

    @Override
    public double[] embed(String input) {
        try {
            String payload = objectMapper.writeValueAsString(java.util.Map.of("inputText", input));
            InvokeModelResponse response = bedrockRuntimeClient.invokeModel(InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(payload))
                    .build());
            JsonNode root = objectMapper.readTree(response.body().asUtf8String());
            JsonNode embeddingNode = root.path("embedding");
            double[] vector = new double[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = embeddingNode.get(i).asDouble();
            }
            return vector;
        } catch (Exception exception) {
            throw new IllegalStateException("Bedrock embedding call failed", exception);
        }
    }

    @Override
    public String modelName() {
        return modelId;
    }
}
