package com.tenantmetrics.platform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TenantIsolationTests extends AbstractPlatformPostgresTest {

	private static final String TENANT_A_KEY = "tenant-a-test-key";
	private static final String TENANT_B_KEY = "tenant-b-test-key";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void missingCredentialIsUnauthorized() throws Exception {
		mockMvc.perform(get("/v1/tenant-context"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tenantHeaderAloneDoesNotAuthenticate() throws Exception {
		mockMvc.perform(get("/v1/tenant-context").header("X-Tenant-ID", "tenant-a"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void verifiedCredentialResolvesTenantA() throws Exception {
		mockMvc.perform(get("/v1/tenant-context").header("X-Api-Key", TENANT_A_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value("tenant-a"));
	}

	@Test
	void forgedTenantHeaderCannotOverrideCredential() throws Exception {
		mockMvc.perform(get("/v1/tenant-context")
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b")
						.queryParam("tenantId", "tenant-b"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value("tenant-a"));
	}

	@Test
	void tenantACredentialCannotObtainTenantBContext() throws Exception {
		mockMvc.perform(get("/v1/tenant-context")
						.header("X-Api-Key", TENANT_A_KEY)
						.header("X-Tenant-ID", "tenant-b"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value("tenant-a"))
				.andExpect(jsonPath("$.tenantId").value(org.hamcrest.Matchers.not("tenant-b")));
	}

	@Test
	void verifiedCredentialResolvesTenantB() throws Exception {
		mockMvc.perform(get("/v1/tenant-context").header("X-Api-Key", TENANT_B_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value("tenant-b"));
	}
}
