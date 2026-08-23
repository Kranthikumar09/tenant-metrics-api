package com.tenantmetrics.platform;

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
class EventPersistenceTests extends AbstractPlatformPostgresTest {

	private static final String TENANT_A_KEY = "tenant-a-test-key";
	private static final String TENANT_B_KEY = "tenant-b-test-key";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void tenantAEventIsStoredWithTenantA() throws Exception {
		ingest(TENANT_A_KEY, "idem-persist-a", "evt-persist-a", "acct-a");

		assertThat(countEvents("tenant-a", "evt-persist-a")).isEqualTo(1);
		assertThat(countEvents("tenant-b", "evt-persist-a")).isZero();
		assertThat(countReceipts("tenant-a", "idem-persist-a")).isEqualTo(1);
	}

	@Test
	void forgedTenantClaimPersistsAsCredentialTenant() throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b")
						.header("Idempotency-Key", "idem-persist-forged")
						.content("""
								{
								  "tenant_id": "tenant-b",
								  "events": [
								    {
								      "event_id": "evt-persist-forged",
								      "account_external_id": "acct-b",
								      "event_type": "billing.invoice_paid",
								      "occurred_at": "2026-08-22T12:00:00Z",
								      "schema_version": 1
								    }
								  ]
								}
								"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));

		assertThat(countEvents("tenant-a", "evt-persist-forged")).isEqualTo(1);
		assertThat(countEvents("tenant-b", "evt-persist-forged")).isZero();
	}

	@Test
	void sameEventIdCanExistForEachTenant() throws Exception {
		ingest(TENANT_A_KEY, "idem-persist-shared-a", "evt-persist-shared", "acct-a");
		ingest(TENANT_B_KEY, "idem-persist-shared-b", "evt-persist-shared", "acct-b");

		assertThat(countEvents("tenant-a", "evt-persist-shared")).isEqualTo(1);
		assertThat(countEvents("tenant-b", "evt-persist-shared")).isEqualTo(1);
	}

	@Test
	void duplicateEventIdLeavesOneRow() throws Exception {
		ingest(TENANT_A_KEY, "idem-persist-dup-1", "evt-persist-dup", "acct-a");
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "idem-persist-dup-2")
						.content(batchJson("evt-persist-dup", "acct-a")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.duplicates").value(1));

		assertThat(countEvents("tenant-a", "evt-persist-dup")).isEqualTo(1);
		assertThat(countReceipts("tenant-a", "idem-persist-dup-1")).isEqualTo(1);
		assertThat(countReceipts("tenant-a", "idem-persist-dup-2")).isEqualTo(1);
	}

	private void ingest(String apiKey, String idempotencyKey, String eventId, String accountId) throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", apiKey)
						.header("Idempotency-Key", idempotencyKey)
						.content(batchJson(eventId, accountId)))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));
	}

	private int countEvents(String tenantId, String eventId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ingested_events WHERE tenant_id = ? AND event_id = ?",
				Integer.class,
				tenantId,
				eventId);
		return count == null ? 0 : count;
	}

	private int countReceipts(String tenantId, String idempotencyKey) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM ingest_receipts WHERE tenant_id = ? AND idempotency_key = ?",
				Integer.class,
				tenantId,
				idempotencyKey);
		return count == null ? 0 : count;
	}

	private static String batchJson(String eventId, String accountExternalId) {
		return """
				{
				  "events": [
				    {
				      "event_id": "%s",
				      "account_external_id": "%s",
				      "event_type": "billing.invoice_paid",
				      "occurred_at": "2026-08-22T12:00:00Z",
				      "schema_version": 1
				    }
				  ]
				}
				""".formatted(eventId, accountExternalId);
	}
}
