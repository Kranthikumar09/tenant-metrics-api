package com.tenantmetrics.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountUpsertTests extends AbstractPlatformPostgresTest {

	private static final String TENANT_A_KEY = "tenant-a-test-key";
	private static final String TENANT_B_KEY = "tenant-b-test-key";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void firstUpsertCreatesOneTenantOwnedAccount() throws Exception {
		mockMvc.perform(accountUpsert(TENANT_A_KEY, "acct-upsert-create"))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", aMapWithSize(1)))
				.andExpect(jsonPath("$.account_external_id").value("acct-upsert-create"));

		assertThat(countAccounts("tenant-a", "acct-upsert-create")).isEqualTo(1);
		assertThat(countAccounts("tenant-b", "acct-upsert-create")).isZero();
	}

	@Test
	void retryReturnsOkWithoutCreatingAnotherAccount() throws Exception {
		mockMvc.perform(accountUpsert(TENANT_A_KEY, "acct-upsert-retry"))
				.andExpect(status().isCreated());

		mockMvc.perform(accountUpsert(TENANT_A_KEY, "acct-upsert-retry"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", aMapWithSize(1)))
				.andExpect(jsonPath("$.account_external_id").value("acct-upsert-retry"));

		assertThat(countAccounts("tenant-a", "acct-upsert-retry")).isEqualTo(1);
	}

	@Test
	void concurrentRetriesHaveExactlyOneCreateEffect() throws Exception {
		int attempts = 8;
		CountDownLatch ready = new CountDownLatch(attempts);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> responses = new ArrayList<>();

		try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
			for (int i = 0; i < attempts; i++) {
				responses.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return mockMvc.perform(accountUpsert(TENANT_A_KEY, "acct-upsert-concurrent"))
							.andReturn()
							.getResponse()
							.getStatus();
				}));
			}
			ready.await();
			start.countDown();

			List<Integer> statuses = new ArrayList<>();
			for (Future<Integer> response : responses) {
				statuses.add(response.get());
			}
			assertThat(statuses).containsExactlyInAnyOrder(201, 200, 200, 200, 200, 200, 200, 200);
		}

		assertThat(countAccounts("tenant-a", "acct-upsert-concurrent")).isEqualTo(1);
	}

	@Test
	void sameExternalIdCanExistIndependentlyAcrossTenants() throws Exception {
		mockMvc.perform(accountUpsert(TENANT_A_KEY, "acct-upsert-shared"))
				.andExpect(status().isCreated());
		mockMvc.perform(accountUpsert(TENANT_B_KEY, "acct-upsert-shared"))
				.andExpect(status().isCreated());

		assertThat(countAccounts("tenant-a", "acct-upsert-shared")).isEqualTo(1);
		assertThat(countAccounts("tenant-b", "acct-upsert-shared")).isEqualTo(1);
	}

	@Test
	void clientTenantAndUnknownFieldsAreRejected() throws Exception {
		mockMvc.perform(post("/v1/accounts:upsert")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b")
						.content("""
								{
								  "account_external_id": "acct-upsert-forged",
								  "tenant_id": "tenant-b"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));

		assertThat(countAccounts("tenant-a", "acct-upsert-forged")).isZero();
		assertThat(countAccounts("tenant-b", "acct-upsert-forged")).isZero();
	}

	@Test
	void missingEmptyAndOversizedIdentifiersAreRejected() throws Exception {
		assertBadRequest("{}");
		assertBadRequest("{\"account_external_id\":\"\"}");
		assertBadRequest("{\"account_external_id\":\"%s\"}".formatted("a".repeat(129)));
	}

	@Test
	void malformedJsonIsRejectedWithProblemDetails() throws Exception {
		mockMvc.perform(post("/v1/accounts:upsert")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.content("{\"account_external_id\":"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void missingCredentialIsUnauthorizedAndHasNoAccountEffect() throws Exception {
		mockMvc.perform(post("/v1/accounts:upsert")
						.contentType(MediaType.APPLICATION_JSON)
						.content(accountJson("acct-upsert-unauthorized")))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(countAllAccounts("acct-upsert-unauthorized")).isZero();
	}

	private void assertBadRequest(String body) throws Exception {
		mockMvc.perform(post("/v1/accounts:upsert")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-Api-Key", TENANT_A_KEY)
						.content(body))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder accountUpsert(
			String apiKey,
			String accountExternalId) {
		return post("/v1/accounts:upsert")
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-Api-Key", apiKey)
				.content(accountJson(accountExternalId));
	}

	private static String accountJson(String accountExternalId) {
		return "{\"account_external_id\":\"%s\"}".formatted(accountExternalId);
	}

	private int countAccounts(String tenantId, String accountExternalId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM accounts WHERE tenant_id = ? AND account_external_id = ?",
				Integer.class,
				tenantId,
				accountExternalId);
		return count == null ? 0 : count;
	}

	private int countAllAccounts(String accountExternalId) {
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM accounts WHERE account_external_id = ?",
				Integer.class,
				accountExternalId);
		return count == null ? 0 : count;
	}
}
