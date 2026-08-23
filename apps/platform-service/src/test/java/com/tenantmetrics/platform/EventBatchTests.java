package com.tenantmetrics.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EventBatchTests extends AbstractPlatformPostgresTest {

	private static final String TENANT_A_KEY = "tenant-a-test-key";
	private static final String TENANT_B_KEY = "tenant-b-test-key";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void missingCredentialIsUnauthorized() throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("Idempotency-Key", "missing-cred")
						.content(batchJson("evt-missing", "acct-1")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void acceptedBatchReturns202ForTenantA() throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "idem-a-accept")
						.content(batchJson("evt-a-1", "acct-a")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.request_id").isNotEmpty())
				.andExpect(jsonPath("$.accepted").value(1))
				.andExpect(jsonPath("$.rejected").value(0))
				.andExpect(jsonPath("$.duplicates").value(0));
	}

	@Test
	void replayWithSameIdempotencyKeyReturnsSameRequestId() throws Exception {
		String body = batchJson("evt-a-replay", "acct-a");
		MvcResult first = mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "idem-a-replay")
						.content(body))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1))
				.andReturn();
		String requestId = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.request_id");

		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "idem-a-replay")
						.content(body))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.request_id").value(requestId))
				.andExpect(jsonPath("$.accepted").value(1))
				.andExpect(jsonPath("$.duplicates").value(0));
	}

	@Test
	void duplicateEventIdWithinTenantIsOneEffect() throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "idem-a-dup-1")
						.content(batchJson("evt-a-dup", "acct-a")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));

		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "idem-a-dup-2")
						.content(batchJson("evt-a-dup", "acct-a")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(0))
				.andExpect(jsonPath("$.duplicates").value(1));
	}

	@Test
	void forgedTenantClaimCannotIngestAsAnotherTenant() throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b")
						.header("Idempotency-Key", "idem-a-forged")
						.content("""
								{
								  "tenant_id": "tenant-b",
								  "events": [
								    {
								      "event_id": "evt-forged",
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

		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_B_KEY)
						.header("Idempotency-Key", "idem-b-same-event")
						.content(batchJson("evt-forged", "acct-b")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1))
				.andExpect(jsonPath("$.duplicates").value(0));
	}

	@Test
	void oversizedBatchIsRejected() throws Exception {
		StringBuilder events = new StringBuilder();
		events.append("{\"events\":[");
		for (int i = 0; i < 501; i++) {
			if (i > 0) {
				events.append(',');
			}
			events.append("{\"event_id\":\"evt-over-")
					.append(i)
					.append("\",\"account_external_id\":\"acct-a\",\"event_type\":\"billing.invoice_paid\",")
					.append("\"occurred_at\":\"2026-08-22T12:00:00Z\",\"schema_version\":1}");
		}
		events.append("]}");

		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "idem-a-oversize")
						.content(events.toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void missingRequiredFieldIsRejected() throws Exception {
		mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "idem-a-invalid")
						.content("""
								{"events":[{"event_id":"evt-invalid","account_external_id":"acct-a"}]}
								"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(0))
				.andExpect(jsonPath("$.rejected").value(1));
	}

	@Test
	void tenantACannotConsumeTenantBIdempotencyReceipt() throws Exception {
		MvcResult tenantB = mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_B_KEY)
						.header("Idempotency-Key", "shared-key")
						.content(batchJson("evt-b-shared", "acct-b")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1))
				.andReturn();
		String tenantBRequestId = com.jayway.jsonpath.JsonPath.read(
				tenantB.getResponse().getContentAsString(), "$.request_id");

		MvcResult tenantA = mockMvc.perform(post("/v1/events:batch")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("Idempotency-Key", "shared-key")
						.content(batchJson("evt-a-shared", "acct-a")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1))
				.andReturn();
		String tenantARequestId = com.jayway.jsonpath.JsonPath.read(
				tenantA.getResponse().getContentAsString(), "$.request_id");

		assertThat(tenantARequestId).isNotEqualTo(tenantBRequestId);
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
