package com.tenantmetrics.platform.scoring;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

@JsonInclude(JsonInclude.Include.NON_NULL)
record PredictionResponse(
		@JsonProperty("account_external_id") String accountExternalId,
		String eligibility,
		@JsonProperty("health_score") Integer healthScore,
		@JsonProperty("risk_band") String riskBand,
		@JsonProperty("risk_probability") Double riskProbability,
		@JsonProperty("score_version") String scoreVersion,
		@JsonProperty("feature_version") String featureVersion,
		@JsonRawValue @JsonProperty("drivers") String drivers,
		@JsonProperty("scored_at") Instant scoredAt,
		@JsonProperty("freshness_seconds") Integer freshnessSeconds,
		@JsonProperty("explanation_status") String explanationStatus) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record PredictionListResponse(
		List<PredictionResponse> items,
		@JsonProperty("next_cursor") String nextCursor) {
}
