package com.tenantmetrics.platform;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PredictionHistoryTests extends AbstractPlatformPostgresTest {

	private static final String TENANT_A_KEY = "tenant-a-test-key";
	private static final String TENANT_B_KEY = "tenant-b-test-key";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void eachAcceptedEventAppendsAnImmutableHistoryRevision() throws Exception {
		ingest(TENANT_A_KEY, "idem-hist-write-1", "evt-hist-write-1", "acct-hist-write", "auth.login");
		ingest(TENANT_A_KEY, "idem-hist-write-2", "evt-hist-write-2", "acct-hist-write", "billing.payment_failed");

		List<Map<String, Object>> history = historyRows("tenant-a", "acct-hist-write");
		assertThat(history).hasSize(2);
		assertThat(history).extracting(row -> row.get("score_version"))
				.containsOnly("RULES_BASELINE");
		assertThat(history).extracting(row -> ((Number) row.get("health_score")).intValue())
				.containsExactly(45, 70);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE account_score_history SET health_score = 0 WHERE tenant_id = ? AND account_external_id = ?",
				"tenant-a",
				"acct-hist-write"))
				.hasMessageContaining("account_score_history is append-only");
		assertThatThrownBy(() -> jdbcTemplate.update(
				"DELETE FROM account_score_history WHERE tenant_id = ? AND account_external_id = ?",
				"tenant-a",
				"acct-hist-write"))
				.hasMessageContaining("account_score_history is append-only");
		assertThat(historyRows("tenant-a", "acct-hist-write")).hasSize(2);
	}

	@Test
	void historyPagesNewestFirstWithoutOverlap() throws Exception {
		ingest(TENANT_A_KEY, "idem-hist-page-1", "evt-hist-page-1", "acct-hist-page", "auth.login");
		ingest(TENANT_A_KEY, "idem-hist-page-2", "evt-hist-page-2", "acct-hist-page", "billing.payment_failed");
		ingest(TENANT_A_KEY, "idem-hist-page-3", "evt-hist-page-3", "acct-hist-page", "auth.login");

		MvcResult firstResult = mockMvc.perform(get("/v1/accounts/acct-hist-page/prediction-history")
						.param("limit", "2")
						.header("X-Api-Key", TENANT_A_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].account_external_id").value("acct-hist-page"))
				.andExpect(jsonPath("$.next_cursor").isNotEmpty())
				.andReturn();
		String firstBody = firstResult.getResponse().getContentAsString();
		String cursor = JsonPath.read(firstBody, "$.next_cursor");
		String decodedCursor = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
		assertThat(decodedCursor).matches("[1-9][0-9]*");
		assertThat(decodedCursor).doesNotContain("tenant", "acct-hist-page");

		MvcResult secondResult = mockMvc.perform(get("/v1/accounts/acct-hist-page/prediction-history")
						.param("limit", "2")
						.param("cursor", cursor)
						.header("X-Api-Key", TENANT_A_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.next_cursor").doesNotExist())
				.andReturn();

		List<String> scoredAt = new ArrayList<>();
		scoredAt.addAll(jsonStrings(firstBody, "$.items[*].scored_at"));
		scoredAt.addAll(jsonStrings(secondResult.getResponse().getContentAsString(), "$.items[*].scored_at"));
		assertThat(scoredAt).hasSize(3).doesNotHaveDuplicates();
	}

	@Test
	void sameAccountHistoryIsIsolatedByVerifiedTenant() throws Exception {
		ingest(TENANT_A_KEY, "idem-hist-tenant-a", "evt-hist-tenant-a", "acct-hist-shared", "billing.payment_failed");
		ingest(TENANT_B_KEY, "idem-hist-tenant-b", "evt-hist-tenant-b", "acct-hist-shared", "auth.login");

		mockMvc.perform(get("/v1/accounts/acct-hist-shared/prediction-history")
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].health_score").value(45))
				.andExpect(jsonPath("$.items[0].tenant_id").doesNotExist());

		mockMvc.perform(get("/v1/accounts/acct-hist-shared/prediction-history")
						.header("X-Api-Key", TENANT_B_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].health_score").value(70));
	}

	@Test
	void guessedOtherTenantAccountHistoryIsNotFound() throws Exception {
		ingest(TENANT_A_KEY, "idem-hist-hidden", "evt-hist-hidden", "acct-hist-hidden", "auth.login");

		mockMvc.perform(get("/v1/accounts/acct-hist-hidden/prediction-history")
						.header("X-Api-Key", TENANT_B_KEY)
						.header("X-Tenant-ID", "tenant-a"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void historyRejectsMissingCredentialsAndInvalidPagination() throws Exception {
		mockMvc.perform(get("/v1/accounts/acct-hist-invalid/prediction-history"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/v1/accounts/acct-hist-invalid/prediction-history")
						.param("cursor", "not-a-history-cursor")
						.header("X-Api-Key", TENANT_A_KEY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));

		mockMvc.perform(get("/v1/accounts/acct-hist-invalid/prediction-history")
						.param("limit", "501")
						.header("X-Api-Key", TENANT_A_KEY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	private void ingest(String apiKey, String idempotencyKey, String eventId, String accountId, String eventType)
			throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", apiKey)
						.header("Idempotency-Key", idempotencyKey)
						.content("""
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
								""".formatted(eventId, accountId, eventType, recentOccurredAt())))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));
	}

	private List<Map<String, Object>> historyRows(String tenantId, String accountExternalId) {
		return jdbcTemplate.queryForList(
				"""
						SELECT health_score, risk_band, score_version, scored_at
						FROM account_score_history
						WHERE tenant_id = ? AND account_external_id = ?
						ORDER BY history_id DESC
						""",
				tenantId,
				accountExternalId);
	}

	private static List<String> jsonStrings(String body, String path) {
		Configuration config = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
		return JsonPath.using(config).parse(body).read(path);
	}

	private static String recentOccurredAt() {
		return Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
	}
}
