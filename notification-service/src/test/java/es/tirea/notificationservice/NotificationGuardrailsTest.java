package es.tirea.notificationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.tirea.notificationservice.application.service.NotificationGuardrails;
import es.tirea.notificationservice.domain.model.GeneratedNotification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationGuardrailsTest {

    private final NotificationGuardrails guardrails = new NotificationGuardrails(new ObjectMapper(), "email,sms,push", 120);

    @Test
    void sanitizesPiiAndKeepsValidJson() {
        GeneratedNotification notification = guardrails.validateAndSanitize("""
                {"channel":"email","title":"Hi","body":"Contact me at john@example.com or +34 600 123 123","confidence":0.8}
                """, "fake");

        assertThat(notification.body()).contains("[REDACTED_EMAIL]");
        assertThat(notification.body()).contains("[REDACTED_PHONE]");
    }

    @Test
    void rejectsInvalidChannel() {
        assertThatThrownBy(() -> guardrails.validateAndSanitize("""
                {"channel":"fax","title":"Hi","body":"Body","confidence":0.8}
                """, "fake"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
