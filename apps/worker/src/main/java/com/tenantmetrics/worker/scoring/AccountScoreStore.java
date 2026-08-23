package com.tenantmetrics.worker.scoring;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

import com.tenantmetrics.scoring.AccountEvent;
import com.tenantmetrics.scoring.AccountScore;

class AccountScoreStore {

	private final JdbcTemplate jdbcTemplate;

	AccountScoreStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<AccountEvent> findEvent(String tenantId, String eventId) {
		return jdbcTemplate.query(
				"""
						SELECT tenant_id, account_external_id, event_type, occurred_at
						FROM ingested_events
						WHERE tenant_id = ? AND event_id = ?
						""",
				(rs, rowNum) -> new AccountEvent(
						rs.getString("tenant_id"),
						rs.getString("account_external_id"),
						rs.getString("event_type"),
						rs.getTimestamp("occurred_at").toInstant()),
				tenantId,
				eventId)
				.stream()
				.findFirst();
	}

	List<AccountEvent> loadEvents(String tenantId, String accountExternalId) {
		return jdbcTemplate.query(
				"""
						SELECT tenant_id, account_external_id, event_type, occurred_at
						FROM ingested_events
						WHERE tenant_id = ? AND account_external_id = ?
						""",
				(rs, rowNum) -> new AccountEvent(
						rs.getString("tenant_id"),
						rs.getString("account_external_id"),
						rs.getString("event_type"),
						rs.getTimestamp("occurred_at").toInstant()),
				tenantId,
				accountExternalId);
	}

	void upsert(AccountScore score) {
		jdbcTemplate.update(
				"""
						INSERT INTO account_scores (
							tenant_id, account_external_id, eligibility, health_score, risk_band,
							risk_probability, score_version, feature_version, drivers, scored_at, freshness_seconds
						) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?)
						ON CONFLICT (tenant_id, account_external_id) DO UPDATE SET
							eligibility = EXCLUDED.eligibility,
							health_score = EXCLUDED.health_score,
							risk_band = EXCLUDED.risk_band,
							risk_probability = EXCLUDED.risk_probability,
							score_version = EXCLUDED.score_version,
							feature_version = EXCLUDED.feature_version,
							drivers = EXCLUDED.drivers,
							scored_at = EXCLUDED.scored_at,
							freshness_seconds = EXCLUDED.freshness_seconds
						""",
				score.tenantId(),
				score.accountExternalId(),
				score.eligibility(),
				score.healthScore(),
				score.riskBand(),
				score.riskProbability(),
				score.scoreVersion(),
				score.featureVersion(),
				score.driversJson(),
				Timestamp.from(score.scoredAt()),
				score.freshnessSeconds());
	}
}
