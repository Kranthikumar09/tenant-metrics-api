package com.tenantmetrics.worker;

import org.springframework.stereotype.Component;

@Component
class TenantTaggedEventHandler {

	ConsumeResult handle(String body, String attributeTenantId) {
		String bodyTenantId = jsonField(body, "tenant_id");
		String eventId = jsonField(body, "event_id");
		if (isBlank(bodyTenantId) || isBlank(attributeTenantId)) {
			return new ConsumeResult(false, bodyTenantId, eventId, "missing_tenant");
		}
		if (!bodyTenantId.equals(attributeTenantId)) {
			return new ConsumeResult(false, bodyTenantId, eventId, "mismatched_tenant");
		}
		if (isBlank(eventId)) {
			return new ConsumeResult(false, bodyTenantId, eventId, "missing_event");
		}
		return new ConsumeResult(true, bodyTenantId, eventId, null);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String jsonField(String json, String name) {
		if (json == null) {
			return null;
		}
		String needle = "\"" + name + "\":\"";
		int start = json.indexOf(needle);
		if (start < 0) {
			return null;
		}
		start += needle.length();
		int end = json.indexOf('"', start);
		if (end < 0) {
			return null;
		}
		return json.substring(start, end);
	}
}
