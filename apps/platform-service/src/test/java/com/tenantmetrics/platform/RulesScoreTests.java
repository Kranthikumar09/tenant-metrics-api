package com.tenantmetrics.platform;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RulesScoreTests extends AbstractPlatformPostgresTest {

	private static final String TENANT_A_KEY = "tenant-a-test-key";
	private static final String TENANT_B_KEY = "tenant-b-test-key";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void tenantAEventProducesTenantARulesScore() throws Exception {
		ingest(TENANT_A_KEY, "idem-score-a", "evt-score-a", "acct-score-a", "auth.login");

		Map<String, Object> score = findScore("tenant-a", "acct-score-a");
		assertThat(score).isNotNull();
		assertThat(score.get("score_version")).isEqualTo("RULES_BASELINE");
		assertThat(score.get("eligibility")).isEqualTo("SCORED");
		assertThat(((Number) score.get("health_score")).intValue()).isEqualTo(70);
		assertThat(score.get("risk_band")).isEqualTo("LOW");
		assertThat(score.get("risk_probability")).isNull();
		assertThat(findScore("tenant-b", "acct-score-a")).isNull();
	}

	@Test
	void sameAccountExternalIdIsIsolatedPerTenant() throws Exception {
		ingest(TENANT_A_KEY, "idem-score-shared-a", "evt-score-shared-a", "acct-shared", "billing.payment_failed");
		ingest(TENANT_B_KEY, "idem-score-shared-b", "evt-score-shared-b", "acct-shared", "auth.login");

		Map<String, Object> tenantA = findScore("tenant-a", "acct-shared");
		Map<String, Object> tenantB = findScore("tenant-b", "acct-shared");
		assertThat(((Number) tenantA.get("health_score")).intValue()).isEqualTo(45);
		assertThat(tenantA.get("risk_band")).isEqualTo("MEDIUM");
		assertThat(((Number) tenantB.get("health_score")).intValue()).isEqualTo(70);
		assertThat(tenantB.get("risk_band")).isEqualTo("LOW");
		assertThat(tenantA.get("risk_probability")).isNull();
		assertThat(tenantB.get("risk_probability")).isNull();
	}

	@Test
	void forgedTenantClaimScoresCredentialTenant() throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b")
						.header("Idempotency-Key", "idem-score-forged")
						.content("""
								{
								  "tenant_id": "tenant-b",
								  "events": [
								    {
								      "event_id": "evt-score-forged",
								      "account_external_id": "acct-forged",
								      "event_type": "auth.login",
								      "occurred_at": "%s",
								      "schema_version": 1
								    }
								  ]
								}
								""".formatted(recentOccurredAt())))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));

		assertThat(findScore("tenant-a", "acct-forged")).isNotNull();
		assertThat(findScore("tenant-b", "acct-forged")).isNull();
	}

	@Test
	void replayDoesNotRescore() throws Exception {
		ingest(TENANT_A_KEY, "idem-score-replay", "evt-score-replay", "acct-replay", "auth.login");
		Object firstScoredAt = findScore("tenant-a", "acct-replay").get("scored_at");

		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "idem-score-replay")
						.content(batchJson("evt-score-replay", "acct-replay", "auth.login")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));

		Map<String, Object> replayed = findScore("tenant-a", "acct-replay");
		assertThat(replayed.get("scored_at")).isEqualTo(firstScoredAt);
		assertThat(countScores("tenant-a", "acct-replay")).isEqualTo(1);
	}

	private void ingest(String apiKey, String idempotencyKey, String eventId, String accountId, String eventType)
			throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", apiKey)
						.header("Idempotency-Key", idempotencyKey)
						.content(batchJson(eventId, accountId, eventType)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));
	}

	private Map<String, Object> findScore(String tenantId, String accountExternalId) {
		var rows = jdbcTemplate.queryForList(
				"""
						SELECT tenant_id, account_external_id, eligibility, health_score, risk_band,
							risk_probability, score_version, scored_at
						FROM account_scores
						WHERE tenant_id = ? AND account_external_id = ?
						""",
				tenantId,
				accountExternalId);
		if (rows.isEmpty()) {
			return null;
		}
		return rows.getFirst();
	}

	private int countScores(String tenantId, String accountExternalId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM account_scores WHERE tenant_id = ? AND account_external_id = ?",
				Integer.class,
				tenantId,
				accountExternalId);
		return count == null ? 0 : count;
	}

	private static String batchJson(String eventId, String accountExternalId, String eventType) {
		return """
				{
				  "events": [
				    {
				      "event_id": "%s",
				      "account_external_id": "%s",
				      "event_type": "%s",
				      "occurred_at": "%s",
				      "schema_version": 1
				    }
				  ]
				}
				""".formatted(eventId, accountExternalId, eventType, recentOccurredAt());
	}

	private static String recentOccurredAt() {
		return Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
	}
}
