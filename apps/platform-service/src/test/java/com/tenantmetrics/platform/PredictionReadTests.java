package com.tenantmetrics.platform;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PredictionReadTests extends AbstractPlatformPostgresTest {

	private static final String TENANT_A_KEY = "tenant-a-test-key";
	private static final String TENANT_B_KEY = "tenant-b-test-key";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void missingCredentialIsUnauthorized() throws Exception {
		mockMvc.perform(get("/v1/accounts/acct-pred-missing/prediction"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/v1/predictions"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tenantACanReadOwnRulesScore() throws Exception {
		ingest(TENANT_A_KEY, "idem-pred-a", "evt-pred-a", "acct-pred-a", "auth.login");

		mockMvc.perform(get("/v1/accounts/acct-pred-a/prediction")
						.header("X-Api-Key", TENANT_A_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.account_external_id").value("acct-pred-a"))
				.andExpect(jsonPath("$.eligibility").value("SCORED"))
				.andExpect(jsonPath("$.health_score").value(70))
				.andExpect(jsonPath("$.risk_band").value("LOW"))
				.andExpect(jsonPath("$.risk_probability").doesNotExist())
				.andExpect(jsonPath("$.score_version").value("RULES_BASELINE"))
				.andExpect(jsonPath("$.feature_version").value("rules-features-v1"))
				.andExpect(jsonPath("$.drivers").isArray())
				.andExpect(jsonPath("$.scored_at").isNotEmpty())
				.andExpect(jsonPath("$.explanation_status").value("none"))
				.andExpect(jsonPath("$.tenant_id").doesNotExist());
	}

	@Test
	void tenantBCannotReadTenantAAccountByGuessingId() throws Exception {
		ingest(TENANT_A_KEY, "idem-pred-hidden", "evt-pred-hidden", "acct-pred-hidden", "auth.login");

		mockMvc.perform(get("/v1/accounts/acct-pred-hidden/prediction")
						.header("X-Api-Key", TENANT_B_KEY)
						.header("X-Tenant-ID", "tenant-a"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void sameAccountExternalIdReturnsEachTenantOwnScore() throws Exception {
		ingest(TENANT_A_KEY, "idem-pred-shared-a", "evt-pred-shared-a", "acct-pred-shared", "billing.payment_failed");
		ingest(TENANT_B_KEY, "idem-pred-shared-b", "evt-pred-shared-b", "acct-pred-shared", "auth.login");

		mockMvc.perform(get("/v1/accounts/acct-pred-shared/prediction")
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.health_score").value(45))
				.andExpect(jsonPath("$.risk_band").value("MEDIUM"))
				.andExpect(jsonPath("$.risk_probability").doesNotExist());

		mockMvc.perform(get("/v1/accounts/acct-pred-shared/prediction")
						.header("X-Api-Key", TENANT_B_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.health_score").value(70))
				.andExpect(jsonPath("$.risk_band").value("LOW"));
	}

	@Test
	void tenantListDoesNotIncludeOtherTenantScores() throws Exception {
		ingest(TENANT_A_KEY, "idem-pred-list-a", "evt-pred-list-a", "acct-pred-list-a", "auth.login");
		ingest(TENANT_B_KEY, "idem-pred-list-b", "evt-pred-list-b", "acct-pred-list-b", "auth.login");

		mockMvc.perform(get("/v1/predictions")
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.account_external_id=='acct-pred-list-a')]").exists())
				.andExpect(jsonPath("$.items[?(@.account_external_id=='acct-pred-list-b')]").doesNotExist());
	}

	@Test
	void unknownAccountIsNotFound() throws Exception {
		mockMvc.perform(get("/v1/accounts/acct-pred-unknown/prediction")
						.header("X-Api-Key", TENANT_A_KEY))
				.andExpect(status().isNotFound());
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

	private static String recentOccurredAt() {
		return Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
	}
}
