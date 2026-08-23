package com.tenantmetrics.platform.events;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

record EventBatchRequest(@JsonProperty("events") List<IngestEvent> events) {
}

record IngestEvent(
		@JsonProperty("event_id") String eventId,
		@JsonProperty("account_external_id") String accountExternalId,
		@JsonProperty("event_type") String eventType,
		@JsonProperty("occurred_at") String occurredAt,
		@JsonProperty("schema_version") Integer schemaVersion,
		@JsonProperty("properties") Map<String, Object> properties) {
}

record EventBatchResponse(
		@JsonProperty("request_id") String requestId,
		int accepted,
		int rejected,
		int duplicates) {
}
