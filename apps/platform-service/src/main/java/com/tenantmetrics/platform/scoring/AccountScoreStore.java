package com.tenantmetrics.platform.scoring;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.tenantmetrics.scoring.AccountEvent;
import com.tenantmetrics.scoring.AccountScore;

@Repository
class AccountScoreStore {

	private final JdbcTemplate jdbcTemplate;

	AccountScoreStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
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

	Optional<AccountScore> find(String tenantId, String accountExternalId) {
		return jdbcTemplate.query(
				"""
						SELECT tenant_id, account_external_id, eligibility, health_score, risk_band,
							risk_probability, score_version, feature_version, drivers, scored_at, freshness_seconds
						FROM account_scores
						WHERE tenant_id = ? AND account_external_id = ?
						""",
				this::mapScore,
				tenantId,
				accountExternalId)
				.stream()
				.findFirst();
	}

	List<AccountScore> listByTenant(String tenantId, String afterAccountId, int limit) {
		if (afterAccountId == null) {
			return jdbcTemplate.query(
					"""
							SELECT tenant_id, account_external_id, eligibility, health_score, risk_band,
								risk_probability, score_version, feature_version, drivers, scored_at, freshness_seconds
							FROM account_scores
							WHERE tenant_id = ?
							ORDER BY account_external_id
							LIMIT ?
							""",
					this::mapScore,
					tenantId,
					limit);
		}
		return jdbcTemplate.query(
				"""
						SELECT tenant_id, account_external_id, eligibility, health_score, risk_band,
							risk_probability, score_version, feature_version, drivers, scored_at, freshness_seconds
						FROM account_scores
						WHERE tenant_id = ? AND account_external_id > ?
						ORDER BY account_external_id
						LIMIT ?
						""",
				this::mapScore,
				tenantId,
				afterAccountId,
				limit);
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

	private AccountScore mapScore(ResultSet rs, int rowNum) throws SQLException {
		return new AccountScore(
				rs.getString("tenant_id"),
				rs.getString("account_external_id"),
				rs.getString("eligibility"),
				rs.getObject("health_score", Integer.class),
				rs.getString("risk_band"),
				rs.getObject("risk_probability", Double.class),
				rs.getString("score_version"),
				rs.getString("feature_version"),
				rs.getString("drivers"),
				rs.getTimestamp("scored_at").toInstant(),
				rs.getObject("freshness_seconds", Integer.class));
	}
}
