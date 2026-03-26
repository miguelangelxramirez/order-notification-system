package es.tirea.notificationservice.adapters.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record NotificationRequest(@NotBlank String message) {
}
