CREATE TABLE account_score_history (
	history_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	cursor_id UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
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
	recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX account_score_history_tenant_account_cursor_idx
	ON account_score_history (tenant_id, account_external_id, history_id DESC);

INSERT INTO account_score_history (
	tenant_id, account_external_id, eligibility, health_score, risk_band,
	risk_probability, score_version, feature_version, drivers, scored_at, freshness_seconds
)
SELECT tenant_id, account_external_id, eligibility, health_score, risk_band,
	risk_probability, score_version, feature_version, drivers, scored_at, freshness_seconds
FROM account_scores;

CREATE FUNCTION append_account_score_history()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
	INSERT INTO account_score_history (
		tenant_id, account_external_id, eligibility, health_score, risk_band,
		risk_probability, score_version, feature_version, drivers, scored_at, freshness_seconds
	) VALUES (
		NEW.tenant_id, NEW.account_external_id, NEW.eligibility, NEW.health_score, NEW.risk_band,
		NEW.risk_probability, NEW.score_version, NEW.feature_version, NEW.drivers, NEW.scored_at,
		NEW.freshness_seconds
	);
	RETURN NEW;
END;
$$;

CREATE TRIGGER account_scores_append_history
	AFTER INSERT OR UPDATE ON account_scores
	FOR EACH ROW
	EXECUTE FUNCTION append_account_score_history();

CREATE FUNCTION reject_account_score_history_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
	RAISE EXCEPTION 'account_score_history is append-only';
END;
$$;

CREATE TRIGGER account_score_history_reject_update_delete
	BEFORE UPDATE OR DELETE ON account_score_history
	FOR EACH ROW
	EXECUTE FUNCTION reject_account_score_history_mutation();

CREATE TRIGGER account_score_history_reject_truncate
	BEFORE TRUNCATE ON account_score_history
	FOR EACH STATEMENT
	EXECUTE FUNCTION reject_account_score_history_mutation();
