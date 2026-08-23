CREATE TABLE accepted_event_outbox (
	id BIGSERIAL PRIMARY KEY,
	tenant_id VARCHAR(128) NOT NULL,
	event_id VARCHAR(128) NOT NULL,
	request_id VARCHAR(64) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	published_at TIMESTAMPTZ,
	CONSTRAINT accepted_event_outbox_event_fk
		FOREIGN KEY (tenant_id, event_id)
		REFERENCES ingested_events (tenant_id, event_id)
		ON DELETE CASCADE,
	CONSTRAINT accepted_event_outbox_event_unique UNIQUE (tenant_id, event_id)
);

CREATE INDEX accepted_event_outbox_pending_idx
	ON accepted_event_outbox (id)
	WHERE published_at IS NULL;
