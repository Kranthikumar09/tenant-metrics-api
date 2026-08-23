package com.tenantmetrics.platform.events;

interface AcceptedEventPublisher {

	void publish(String tenantId, String eventId, String requestId);
}
