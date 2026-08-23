package com.tenantmetrics.platform.scoring;

import java.time.Instant;

record AccountEvent(String tenantId, String accountExternalId, String eventType, Instant occurredAt) {
}

record AccountScore(
		String tenantId,
		String accountExternalId,
		String eligibility,
		Integer healthScore,
		String riskBand,
		Double riskProbability,
		String scoreVersion,
		String featureVersion,
		String driversJson,
		Instant scoredAt,
		Integer freshnessSeconds) {
}
