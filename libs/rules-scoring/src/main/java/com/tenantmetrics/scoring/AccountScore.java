package com.tenantmetrics.scoring;

import java.time.Instant;

public record AccountScore(
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
