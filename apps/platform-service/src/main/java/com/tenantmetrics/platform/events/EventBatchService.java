package com.tenantmetrics.platform.events;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.tenantmetrics.platform.tenancy.TenantContext;

@Service
class EventBatchService {

	static final int MAX_BATCH_SIZE = 500;

	private static final Pattern EVENT_TYPE = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*\\.[A-Za-z][A-Za-z0-9_.]*$");

	private final ConcurrentHashMap<String, EventBatchResponse> receipts = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Boolean> acceptedEventIds = new ConcurrentHashMap<>();

	EventBatchResponse ingest(TenantContext tenant, String idempotencyKey, EventBatchRequest request) {
		String receiptKey = tenant.tenantId() + '\u0000' + idempotencyKey;
		return receipts.computeIfAbsent(receiptKey, ignored -> process(tenant, request));
	}

	private EventBatchResponse process(TenantContext tenant, EventBatchRequest request) {
		int accepted = 0;
		int rejected = 0;
		int duplicates = 0;
		for (IngestEvent event : request.events()) {
			if (!isValid(event)) {
				rejected++;
				continue;
			}
			String eventKey = tenant.tenantId() + '\u0000' + event.eventId();
			if (acceptedEventIds.putIfAbsent(eventKey, Boolean.TRUE) != null) {
				duplicates++;
			}
			else {
				accepted++;
			}
		}
		return new EventBatchResponse(UUID.randomUUID().toString(), accepted, rejected, duplicates);
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
