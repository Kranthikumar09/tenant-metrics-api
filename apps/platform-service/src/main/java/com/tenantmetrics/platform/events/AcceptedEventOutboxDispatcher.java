package com.tenantmetrics.platform.events;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tenantmetrics.platform.events.EventBatchStore.PendingAcceptedEvent;

@Component
public class AcceptedEventOutboxDispatcher {

	private static final int BATCH_SIZE = 100;

	private final EventBatchStore eventBatchStore;
	private final AcceptedEventPublisher acceptedEventPublisher;

	AcceptedEventOutboxDispatcher(
			EventBatchStore eventBatchStore,
			AcceptedEventPublisher acceptedEventPublisher) {
		this.eventBatchStore = eventBatchStore;
		this.acceptedEventPublisher = acceptedEventPublisher;
	}

	@Scheduled(
			fixedDelayString = "${platform.events.outbox.dispatch-interval-ms:1000}",
			initialDelayString = "${platform.events.outbox.initial-delay-ms:1000}")
	@Transactional
	public void scheduledDispatch() {
		dispatchPending();
	}

	@Transactional
	public int dispatchOnce() {
		return dispatchPending();
	}

	private int dispatchPending() {
		List<PendingAcceptedEvent> pending = eventBatchStore.lockPendingOutbox(BATCH_SIZE);
		for (PendingAcceptedEvent event : pending) {
			acceptedEventPublisher.publish(event.tenantId(), event.eventId(), event.requestId());
			eventBatchStore.markOutboxPublished(event.id());
		}
		return pending.size();
	}
}
