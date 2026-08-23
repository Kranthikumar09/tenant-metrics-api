package com.tenantmetrics.platform.events;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class EventBatchStore {

	private final JdbcTemplate jdbcTemplate;

	EventBatchStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<EventBatchResponse> findReceipt(String tenantId, String idempotencyKey) {
		return jdbcTemplate.query(
				"""
						SELECT request_id, accepted, rejected, duplicates
						FROM ingest_receipts
						WHERE tenant_id = ? AND idempotency_key = ?
						""",
				(rs, rowNum) -> new EventBatchResponse(
						rs.getString("request_id"),
						rs.getInt("accepted"),
						rs.getInt("rejected"),
						rs.getInt("duplicates")),
				tenantId,
				idempotencyKey)
				.stream()
				.findFirst();
	}

	void insertReceipt(String tenantId, String idempotencyKey, EventBatchResponse response) {
		jdbcTemplate.update(
				"""
						INSERT INTO ingest_receipts
							(tenant_id, idempotency_key, request_id, accepted, rejected, duplicates)
						VALUES (?, ?, ?, ?, ?, ?)
						""",
				tenantId,
				idempotencyKey,
				response.requestId(),
				response.accepted(),
				response.rejected(),
				response.duplicates());
	}

	boolean insertEvent(
			String tenantId,
			String requestId,
			IngestEvent event,
			Instant occurredAt,
			String propertiesJson) {
		int inserted = jdbcTemplate.update(
				"""
						INSERT INTO ingested_events (
							tenant_id, event_id, account_external_id, event_type,
							occurred_at, schema_version, properties, request_id
						) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
						ON CONFLICT (tenant_id, event_id) DO NOTHING
						""",
				tenantId,
				event.eventId(),
				event.accountExternalId(),
				event.eventType(),
				Timestamp.from(occurredAt),
				event.schemaVersion(),
				propertiesJson,
				requestId);
		return inserted == 1;
	}

	void insertOutbox(String tenantId, String eventId, String requestId) {
		jdbcTemplate.update(
				"""
						INSERT INTO accepted_event_outbox (tenant_id, event_id, request_id)
						VALUES (?, ?, ?)
						""",
				tenantId,
				eventId,
				requestId);
	}

	List<PendingAcceptedEvent> lockPendingOutbox(int limit) {
		return jdbcTemplate.query(
				"""
						SELECT id, tenant_id, event_id, request_id
						FROM accepted_event_outbox
						WHERE published_at IS NULL
						ORDER BY id
						LIMIT ?
						FOR UPDATE SKIP LOCKED
						""",
				(rs, rowNum) -> new PendingAcceptedEvent(
						rs.getLong("id"),
						rs.getString("tenant_id"),
						rs.getString("event_id"),
						rs.getString("request_id")),
				limit);
	}

	void markOutboxPublished(long id) {
		int updated = jdbcTemplate.update(
				"""
						UPDATE accepted_event_outbox
						SET published_at = NOW()
						WHERE id = ? AND published_at IS NULL
						""",
				id);
		if (updated != 1) {
			throw new IllegalStateException("pending outbox row was not updated: " + id);
		}
	}

	record PendingAcceptedEvent(long id, String tenantId, String eventId, String requestId) {
	}
}
