package com.aegisflow.api.application.identity;

import java.time.Instant;
import java.util.UUID;

public record AuthAuditEvent(
        UUID eventId,
        String username,
        String providerId,
        String eventType,
        String message,
        Instant occurredAt
) {
}
