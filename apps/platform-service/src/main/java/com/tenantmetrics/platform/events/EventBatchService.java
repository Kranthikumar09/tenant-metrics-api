package com.tenantmetrics.platform.events;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tenantmetrics.platform.tenancy.TenantContext;

@Service
class EventBatchService {

	static final int MAX_BATCH_SIZE = 500;

	private static final Pattern EVENT_TYPE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_.]*$");

	private final EventBatchStore eventBatchStore;
	private final AcceptedEventPublisher acceptedEventPublisher;

	EventBatchService(EventBatchStore eventBatchStore, AcceptedEventPublisher acceptedEventPublisher) {
		this.eventBatchStore = eventBatchStore;
		this.acceptedEventPublisher = acceptedEventPublisher;
	}

	@Transactional
	EventBatchResponse ingest(TenantContext tenant, String idempotencyKey, EventBatchRequest request) {
		return eventBatchStore.findReceipt(tenant.tenantId(), idempotencyKey)
				.orElseGet(() -> persist(tenant, idempotencyKey, request));
	}

	private EventBatchResponse persist(TenantContext tenant, String idempotencyKey, EventBatchRequest request) {
		String requestId = UUID.randomUUID().toString();
		int accepted = 0;
		int rejected = 0;
		int duplicates = 0;
		for (IngestEvent event : request.events()) {
			if (!isValid(event)) {
				rejected++;
				continue;
			}
			if (eventBatchStore.insertEvent(
					tenant.tenantId(),
					requestId,
					event,
					Instant.parse(event.occurredAt()),
					writeProperties(event.properties()))) {
				acceptedEventPublisher.publish(tenant.tenantId(), event.eventId(), requestId);
				accepted++;
			}
			else {
				duplicates++;
			}
		}
		EventBatchResponse response = new EventBatchResponse(requestId, accepted, rejected, duplicates);
		eventBatchStore.insertReceipt(tenant.tenantId(), idempotencyKey, response);
		return response;
	}

	private static String writeProperties(Map<String, Object> properties) {
		if (properties == null || properties.isEmpty()) {
			return null;
		}
		StringBuilder json = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, Object> entry : properties.entrySet()) {
			if (!first) {
				json.append(',');
			}
			first = false;
			json.append('"').append(escapeJson(entry.getKey())).append("\":").append(jsonLiteral(entry.getValue()));
		}
		return json.append('}').toString();
	}

	private static String jsonLiteral(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof Boolean || value instanceof Number) {
			return value.toString();
		}
		return "\"" + escapeJson(String.valueOf(value)) + "\"";
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static boolean isValid(IngestEvent event) {
		if (event == null) {
			return false;
		}
		if (blankOrTooLong(event.eventId()) || blankOrTooLong(event.accountExternalId())) {
			return false;
		}
		if (event.eventType() == null || !EVENT_TYPE.matcher(event.eventType()).matches()
				|| event.eventType().length() > 128) {
			return false;
		}
		if (event.schemaVersion() == null || event.schemaVersion() != 1) {
			return false;
		}
		if (!occurredAtIsAcceptable(event.occurredAt())) {
			return false;
		}
		return propertiesAreBounded(event.properties());
	}

	private static boolean blankOrTooLong(String value) {
		return value == null || value.isBlank() || value.length() > 128;
	}

	private static boolean occurredAtIsAcceptable(String occurredAt) {
		if (occurredAt == null || occurredAt.isBlank()) {
			return false;
		}
		try {
			Instant instant = Instant.parse(occurredAt);
			Instant now = Instant.now();
			return !instant.isAfter(now.plus(1, ChronoUnit.HOURS))
					&& !instant.isBefore(now.minus(3650, ChronoUnit.DAYS));
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

	private static boolean propertiesAreBounded(Map<String, Object> properties) {
		if (properties == null || properties.isEmpty()) {
			return true;
		}
		if (properties.size() > 32) {
			return false;
		}
		for (Object value : properties.values()) {
			if (value instanceof Map<?, ?> || value instanceof List<?>) {
				return false;
			}
		}
		return true;
	}
}
