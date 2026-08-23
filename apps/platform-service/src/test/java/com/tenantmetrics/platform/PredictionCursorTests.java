package com.tenantmetrics.platform;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PredictionCursorTests extends AbstractPlatformPostgresTest {

	private static final String TENANT_A_KEY = "tenant-a-test-key";
	private static final String TENANT_B_KEY = "tenant-b-test-key";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void firstPageHonorsLimitAndReturnsNextCursor() throws Exception {
		ingest(TENANT_A_KEY, "idem-cur-1", "evt-cur-1", "zzz-cur-1", "auth.login");
		ingest(TENANT_A_KEY, "idem-cur-2", "evt-cur-2", "zzz-cur-2", "auth.login");
		ingest(TENANT_A_KEY, "idem-cur-3", "evt-cur-3", "zzz-cur-3", "auth.login");

		MvcResult result = mockMvc.perform(get("/v1/predictions")
						.param("limit", "1")
						.header("X-Api-Key", TENANT_A_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.next_cursor").isNotEmpty())
				.andReturn();
		String cursor = JsonPath.read(result.getResponse().getContentAsString(), "$.next_cursor");
		String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
		assertThat(decoded).doesNotContain("tenant-");
		assertThat(decoded).isEqualTo(JsonPath.read(result.getResponse().getContentAsString(), "$.items[0].account_external_id"));
	}

	@Test
	void pagesAreStableAndDoNotOverlapOrLeak() throws Exception {
		ingest(TENANT_A_KEY, "idem-cur-p1", "evt-cur-p1", "zzz-page-1", "auth.login");
		ingest(TENANT_A_KEY, "idem-cur-p2", "evt-cur-p2", "zzz-page-2", "auth.login");
		ingest(TENANT_A_KEY, "idem-cur-p3", "evt-cur-p3", "zzz-page-3", "auth.login");
		ingest(TENANT_B_KEY, "idem-cur-pb", "evt-cur-pb", "zzz-page-1", "billing.payment_failed");

		List<String> collected = new ArrayList<>();
		String cursor = null;
		for (int i = 0; i < 50; i++) {
			var request = get("/v1/predictions")
					.param("limit", "2")
					.header("X-Api-Key", TENANT_A_KEY)
					.header("X-Tenant-ID", "tenant-b");
			if (cursor != null) {
				request.param("cursor", cursor);
			}
			MvcResult result = mockMvc.perform(request)
					.andExpect(status().isOk())
					.andReturn();
			String body = result.getResponse().getContentAsString();
			Configuration jsonConfig = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL);
			List<String> page = JsonPath.using(jsonConfig).parse(body).read("$.items[*].account_external_id");
			collected.addAll(page);
			cursor = JsonPath.using(jsonConfig).parse(body).read("$.next_cursor");
			if (cursor == null) {
				break;
			}
		}

		assertThat(collected).doesNotHaveDuplicates();
		assertThat(collected).contains("zzz-page-1", "zzz-page-2", "zzz-page-3");
		assertThat(index(collected, "zzz-page-1")).isLessThan(index(collected, "zzz-page-2"));
		assertThat(index(collected, "zzz-page-2")).isLessThan(index(collected, "zzz-page-3"));

		mockMvc.perform(get("/v1/predictions")
						.param("limit", "50")
						.header("X-Api-Key", TENANT_B_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[?(@.account_external_id=='zzz-page-2')]").doesNotExist())
				.andExpect(jsonPath("$.items[?(@.account_external_id=='zzz-page-1')]").exists())
				.andExpect(jsonPath("$.items[?(@.health_score==45)]").exists());
	}

	@Test
	void invalidCursorIsRejected() throws Exception {
		mockMvc.perform(get("/v1/predictions")
						.param("cursor", "not-a-cursor")
						.header("X-Api-Key", TENANT_A_KEY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void oversizedLimitIsRejected() throws Exception {
		mockMvc.perform(get("/v1/predictions")
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

	private static int index(List<String> values, String item) {
		return values.indexOf(item);
	}

	private static String recentOccurredAt() {
		return Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS).toString();
	}
}
