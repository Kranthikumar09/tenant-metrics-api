CREATE TABLE ingested_events (
	tenant_id VARCHAR(128) NOT NULL,
	event_id VARCHAR(128) NOT NULL,
	account_external_id VARCHAR(128) NOT NULL,
	event_type VARCHAR(128) NOT NULL,
	occurred_at TIMESTAMPTZ NOT NULL,
	received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	schema_version INTEGER NOT NULL,
	properties JSONB,
	request_id VARCHAR(64) NOT NULL,
	PRIMARY KEY (tenant_id, event_id)
);

CREATE TABLE ingest_receipts (
	tenant_id VARCHAR(128) NOT NULL,
	idempotency_key VARCHAR(128) NOT NULL,
	request_id VARCHAR(64) NOT NULL,
	accepted INTEGER NOT NULL,
	rejected INTEGER NOT NULL,
	duplicates INTEGER NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
	PRIMARY KEY (tenant_id, idempotency_key)
);
