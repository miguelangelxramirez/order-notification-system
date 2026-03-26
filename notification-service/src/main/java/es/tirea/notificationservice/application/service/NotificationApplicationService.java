package es.tirea.notificationservice.application.service;

import es.tirea.notificationservice.application.usecase.CreateManualNotificationUseCase;
import es.tirea.notificationservice.application.usecase.ListNotificationsUseCase;
import es.tirea.notificationservice.application.usecase.ProcessOrderCreatedUseCase;
import es.tirea.notificationservice.domain.model.GeneratedNotification;
import es.tirea.notificationservice.domain.model.KnowledgeChunk;
import es.tirea.notificationservice.domain.model.NotificationRecord;
import es.tirea.notificationservice.domain.model.OrderCreatedEvent;
import es.tirea.notificationservice.domain.port.out.EmbeddingPort;
import es.tirea.notificationservice.domain.port.out.LlmPort;
import es.tirea.notificationservice.domain.port.out.NotificationRepositoryPort;
import es.tirea.notificationservice.domain.port.out.VectorStorePort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationApplicationService implements ProcessOrderCreatedUseCase, ListNotificationsUseCase, CreateManualNotificationUseCase {

    private final NotificationRepositoryPort notificationRepositoryPort;
    private final VectorStorePort vectorStorePort;
    private final EmbeddingPort embeddingPort;
    private final LlmPort llmPort;
    private final NotificationPromptFactory promptFactory;
    private final NotificationGuardrails guardrails;
    private final Tracer tracer;
    private final int topK;
    private final Counter consumedCounter;
    private final Counter errorsCounter;
    private final Timer llmTimer;
    private final Timer embeddingTimer;

    public NotificationApplicationService(NotificationRepositoryPort notificationRepositoryPort,
                                          VectorStorePort vectorStorePort,
                                          EmbeddingPort embeddingPort,
                                          LlmPort llmPort,
                                          NotificationPromptFactory promptFactory,
                                          NotificationGuardrails guardrails,
                                          Tracer tracer,
                                          MeterRegistry meterRegistry,
                                          @Value("${app.ai.top-k}") int topK) {
        this.notificationRepositoryPort = notificationRepositoryPort;
        this.vectorStorePort = vectorStorePort;
        this.embeddingPort = embeddingPort;
        this.llmPort = llmPort;
        this.promptFactory = promptFactory;
        this.guardrails = guardrails;
        this.tracer = tracer;
        this.topK = topK;
        this.consumedCounter = meterRegistry.counter("notifications.events.consumed.total");
        this.errorsCounter = meterRegistry.counter("notifications.events.errors.total");
        this.llmTimer = meterRegistry.timer("notifications.llm.latency");
        this.embeddingTimer = meterRegistry.timer("notifications.embedding.latency");
    }

    @Override
    @Transactional
    public void process(OrderCreatedEvent event) {
        if (notificationRepositoryPort.existsBySourceOrderIdAndChannel(event.orderId(), "email")) {
            return;
        }

        Instant startedAt = Instant.now();
        try {
            double[] queryEmbedding = embeddingTimer.record(() -> embeddingPort.embed(
                    event.product() + " " + event.quantity() + " " + event.price()
            ));
            List<KnowledgeChunk> chunks = vectorStorePort.searchSimilar(queryEmbedding, topK);
            String rawJson = llmTimer.record(() -> llmPort.generateNotificationJson(
                    promptFactory.systemPrompt(),
                    promptFactory.userPrompt(event),
                    promptFactory.retrievedContext(chunks.stream().map(KnowledgeChunk::content).toList())
            ));
            GeneratedNotification generatedNotification;
            try {
                generatedNotification = guardrails.validateAndSanitize(rawJson, llmPort.modelName());
            } catch (IllegalArgumentException guardrailError) {
                errorsCounter.increment();
                generatedNotification = guardrails.fallbackNotification(
                        "We received your order for %s x%d.".formatted(event.product(), event.quantity()),
                        llmPort.modelName()
                );
            }

            NotificationRecord saved = notificationRepositoryPort.save(new NotificationRecord(
                    UUID.randomUUID().toString(),
                    event.orderId(),
                    "kafka",
                    generatedNotification.channel(),
                    generatedNotification.title(),
                    generatedNotification.body(),
                    generatedNotification.confidence(),
                    generatedNotification.modelUsed(),
                    java.time.Duration.between(startedAt, Instant.now()).toMillis(),
                    currentTraceId(),
                    generatedNotification.fallback(),
                    Instant.now()
            ));
            consumedCounter.increment();
        } catch (RuntimeException exception) {
            errorsCounter.increment();
            throw exception;
        }
    }

    @Override
    public List<NotificationRecord> listNotifications() {
        return notificationRepositoryPort.findAll();
    }

    @Override
    public NotificationRecord createManualNotification(String message) {
        GeneratedNotification safeNotification = guardrails.fallbackNotification(message, "manual");
        return notificationRepositoryPort.save(new NotificationRecord(
                UUID.randomUUID().toString(),
                null,
                "http",
                safeNotification.channel(),
                safeNotification.title(),
                safeNotification.body(),
                safeNotification.confidence(),
                safeNotification.modelUsed(),
                0,
                currentTraceId(),
                true,
                Instant.now()
        ));
    }

    private String currentTraceId() {
        return tracer.currentSpan() != null ? tracer.currentSpan().context().traceId() : "n/a";
    }
}
