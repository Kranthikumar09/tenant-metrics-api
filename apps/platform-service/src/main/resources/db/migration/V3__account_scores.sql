CREATE TABLE account_scores (
	tenant_id VARCHAR(128) NOT NULL,
	account_external_id VARCHAR(128) NOT NULL,
	eligibility VARCHAR(32) NOT NULL,
	health_score INTEGER,
	risk_band VARCHAR(16),
	risk_probability DOUBLE PRECISION,
	score_version VARCHAR(64) NOT NULL,
	feature_version VARCHAR(64) NOT NULL,
	drivers JSONB,
	scored_at TIMESTAMPTZ NOT NULL,
	freshness_seconds INTEGER,
	PRIMARY KEY (tenant_id, account_external_id)
);
