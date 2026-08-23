package com.tenantmetrics.scoring;

import java.time.Instant;

public record AccountEvent(String tenantId, String accountExternalId, String eventType, Instant occurredAt) {
}
