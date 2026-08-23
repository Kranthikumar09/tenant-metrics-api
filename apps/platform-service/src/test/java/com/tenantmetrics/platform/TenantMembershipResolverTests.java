package com.tenantmetrics.platform;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.tenantmetrics.platform.security.TenantMembershipResolver;
import com.tenantmetrics.platform.security.TenantSessionPrincipal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TenantMembershipResolverTests extends AbstractPlatformPostgresTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TenantMembershipResolver resolver;

	@Test
	void oneEnabledMembershipCreatesTheExpectedTenantPrincipal() {
		String issuer = "https://issuer.test/single";
		String subject = "subject-single";
		seedIdentity(issuer, subject, true, "tenant-membership-a", true, true);

		TenantSessionPrincipal principal = resolver.resolve(issuer, subject).orElseThrow();

		assertThat(principal.subject()).isEqualTo(subject);
		assertThat(principal.tenantId()).isEqualTo("tenant-membership-a");
		assertThat(principal.getName()).isNotBlank();
	}

	@Test
	void missingIdentityFailsClosed() {
		assertThat(resolver.resolve("https://issuer.test/missing", "unknown-subject")).isEmpty();
	}

	@Test
	void disabledUserTenantOrMembershipFailsClosed() {
		seedIdentity("https://issuer.test/disabled-user", "subject-disabled-user", false,
				"tenant-disabled-user", true, true);
		seedIdentity("https://issuer.test/disabled-tenant", "subject-disabled-tenant", true,
				"tenant-disabled", false, true);
		seedIdentity("https://issuer.test/disabled-membership", "subject-disabled-membership", true,
				"tenant-disabled-membership", true, false);

		assertThat(resolver.resolve("https://issuer.test/disabled-user", "subject-disabled-user")).isEmpty();
		assertThat(resolver.resolve("https://issuer.test/disabled-tenant", "subject-disabled-tenant")).isEmpty();
		assertThat(resolver.resolve("https://issuer.test/disabled-membership", "subject-disabled-membership")).isEmpty();
	}

	@Test
	void multipleEnabledMembershipsFailClosed() {
		String issuer = "https://issuer.test/ambiguous";
		String subject = "subject-ambiguous";
		UUID userId = seedIdentity(issuer, subject, true, "tenant-ambiguous-a", true, true);
		insertTenant("tenant-ambiguous-b", true);
		insertMembership(userId, "tenant-ambiguous-b", true);

		assertThat(resolver.resolve(issuer, subject)).isEmpty();
	}

	@Test
	void forgedTenantClaimsCannotOverrideResolvedMembership() throws Exception {
		String issuer = "https://issuer.test/request";
		String subject = "subject-request";
		seedIdentity(issuer, subject, true, "tenant-request-a", true, true);

		mockMvc.perform(get("/v1/tenant-context")
					.with(verifiedSession(issuer, subject))
					.header("X-Tenant-ID", "tenant-request-b")
					.queryParam("tenantId", "tenant-request-b"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value("tenant-request-a"))
				.andExpect(jsonPath("$.tenantId").value(org.hamcrest.Matchers.not("tenant-request-b")));
	}

	@Test
	void authenticatedSessionCannotAccessAnUnlistedRoute() throws Exception {
		String issuer = "https://issuer.test/deny-default";
		String subject = "subject-deny-default";
		seedIdentity(issuer, subject, true, "tenant-deny-default", true, true);

		mockMvc.perform(get("/internal/not-an-approved-route")
					.with(verifiedSession(issuer, subject)))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403));
	}

	private UUID seedIdentity(
			String issuer,
			String subject,
			boolean userEnabled,
			String tenantId,
			boolean tenantEnabled,
			boolean membershipEnabled) {
		UUID userId = UUID.nameUUIDFromBytes((issuer + "|" + subject).getBytes(StandardCharsets.UTF_8));
		jdbcTemplate.update(
				"INSERT INTO platform_users (user_id, oidc_issuer, oidc_subject, enabled) VALUES (?, ?, ?, ?)",
				userId, issuer, subject, userEnabled);
		insertTenant(tenantId, tenantEnabled);
		insertMembership(userId, tenantId, membershipEnabled);
		return userId;
	}

	private void insertTenant(String tenantId, boolean enabled) {
		jdbcTemplate.update(
				"INSERT INTO tenants (tenant_id, display_name, enabled) VALUES (?, ?, ?)",
				tenantId, tenantId, enabled);
	}

	private void insertMembership(UUID userId, String tenantId, boolean enabled) {
		jdbcTemplate.update(
				"INSERT INTO tenant_memberships (user_id, tenant_id, role, enabled) VALUES (?, ?, ?, ?)",
				userId, tenantId, "MEMBER", enabled);
	}

	private RequestPostProcessor verifiedSession(String issuer, String subject) {
		TenantSessionPrincipal principal = resolver.resolve(issuer, subject).orElseThrow();
		Authentication authentication = new UsernamePasswordAuthenticationToken(principal, "N/A", List.of());
		return authentication(authentication);
	}
}
