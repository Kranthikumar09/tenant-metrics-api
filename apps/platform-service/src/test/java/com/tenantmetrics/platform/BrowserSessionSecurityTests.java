package com.tenantmetrics.platform;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tenantmetrics.platform.security.TenantMembershipResolver;
import com.tenantmetrics.platform.security.TenantSessionPrincipal;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import({BrowserSessionSecurityTests.SessionProbeConfiguration.class,
		BrowserSessionSecurityTests.AbsoluteLifetimeClockConfiguration.class})
class BrowserSessionSecurityTests extends AbstractPlatformPostgresTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TenantMembershipResolver membershipResolver;

	@Autowired
	private AdjustableClock absoluteSessionClock;

	@BeforeEach
	void resetAbsoluteSessionClock() {
		absoluteSessionClock.setInstant(Instant.EPOCH);
	}

	@Test
	void sessionIsPersistedInPostgresWithProductionCookieAttributes() throws Exception {
		int sessionsBefore = sessionCount();

		MvcResult result = mockMvc.perform(get("/test/session-probe"))
				.andExpect(status().isOk())
				.andReturn();

		String sessionCookie = cookieHeader(result, "__Host-tm_session");
		assertThat(sessionCookie)
				.contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax")
				.doesNotContain("Domain=");
		assertThat(sessionCount()).isEqualTo(sessionsBefore + 1);
	}

	@Test
	void sessionPrincipalResolvesTenantAndIgnoresForgedTenantClaims() throws Exception {
		mockMvc.perform(get("/v1/tenant-context")
					.with(tenantSession("user-a", "tenant-a"))
					.header("X-Tenant-ID", "tenant-b")
					.queryParam("tenantId", "tenant-b"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value("tenant-a"))
				.andExpect(jsonPath("$.tenantId").value(org.hamcrest.Matchers.not("tenant-b")));
	}

	@Test
	void browserWriteWithoutCsrfTokenReturnsProblemDetails() throws Exception {
		mockMvc.perform(post("/v1/events:batch")
					.with(tenantSession("user-a", "tenant-a"))
					.contentType(MediaType.APPLICATION_JSON)
					.header("Idempotency-Key", "session-csrf-missing")
					.content(batchJson("evt-session-csrf-missing")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void browserWriteWithCsrfCookieAndHeaderIsAccepted() throws Exception {
		MvcResult session = mockMvc.perform(get("/test/session-probe"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie sessionCookie = requiredCookie(session, "__Host-tm_session");
		MvcResult csrf = mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie csrfCookie = requiredCookie(csrf, "XSRF-TOKEN");

		mockMvc.perform(post("/v1/events:batch")
					.with(tenantSession("user-a", "tenant-a"))
					.cookie(sessionCookie, csrfCookie)
					.header("X-XSRF-TOKEN", csrfCookie.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.header("Idempotency-Key", "session-csrf-valid")
					.content(batchJson("evt-session-csrf-valid")))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.accepted").value(1));
	}

	@Test
	void invalidApiKeyCannotFallBackToAValidBrowserPrincipal() throws Exception {
		mockMvc.perform(get("/v1/tenant-context")
					.with(tenantSession("user-a", "tenant-a"))
					.header("X-Api-Key", "invalid-key"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void missingSessionReturnsJsonUnauthorizedWithoutRedirect() throws Exception {
		mockMvc.perform(get("/v1/tenant-context"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull());
	}

	@Test
	void browserActivityCannotExtendTheEightHourAbsoluteLifetime() throws Exception {
		int sessionsBefore = sessionCount();
		PersistedSession session = createPersistedSession();

		absoluteSessionClock.setInstant(session.createdAt().plus(Duration.ofHours(8)).minusSeconds(1));
		mockMvc.perform(get("/v1/tenant-context")
					.cookie(session.cookie())
					.with(tenantSession("user-a", "tenant-a")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value("tenant-a"));

		absoluteSessionClock.setInstant(session.createdAt().plus(Duration.ofHours(8)));
		mockMvc.perform(get("/v1/tenant-context")
					.cookie(session.cookie())
					.with(tenantSession("user-a", "tenant-a")))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull());

		assertThat(sessionCount()).isEqualTo(sessionsBefore);
	}

	@Test
	void expiredBrowserSessionDoesNotChangeApiKeyAuthentication() throws Exception {
		int sessionsBefore = sessionCount();
		PersistedSession session = createPersistedSession();
		absoluteSessionClock.setInstant(session.createdAt().plus(Duration.ofHours(8)));

		mockMvc.perform(get("/v1/tenant-context")
					.cookie(session.cookie())
					.header("X-Api-Key", "tenant-a-test-key"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value("tenant-a"));

		assertThat(sessionCount()).isEqualTo(sessionsBefore);
	}

	@Test
	void browserLogoutRequiresPostAndCsrfWithoutInvalidatingRejectedSessions() throws Exception {
		int sessionsBefore = sessionCount();
		PersistedSession session = createPersistedSession();

		mockMvc.perform(get("/logout")
					.cookie(session.cookie())
					.with(tenantSession("user-a", "tenant-a")))
				.andExpect(status().isForbidden());
		assertThat(sessionCount()).isEqualTo(sessionsBefore + 1);

		mockMvc.perform(post("/logout")
					.cookie(session.cookie())
					.with(tenantSession("user-a", "tenant-a")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403));
		assertThat(sessionCount()).isEqualTo(sessionsBefore + 1);
	}

	@Test
	void apiKeyHeaderCannotBypassBrowserLogoutCsrfProtection() throws Exception {
		int sessionsBefore = sessionCount();
		PersistedSession session = createPersistedSession();

		mockMvc.perform(post("/logout")
					.cookie(session.cookie())
					.header("X-Api-Key", "tenant-a-test-key")
					.with(tenantSession("user-a", "tenant-a")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403));

		assertThat(sessionCount()).isEqualTo(sessionsBefore + 1);
	}

	@Test
	void csrfProtectedPostLogoutInvalidatesSessionClearsCookieAndIssuesFreshCsrf() throws Exception {
		int sessionsBefore = sessionCount();
		PersistedSession session = createPersistedSession();
		MvcResult csrf = mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andReturn();
		Cookie csrfCookie = requiredCookie(csrf, "XSRF-TOKEN");

		MvcResult logout = mockMvc.perform(post("/logout")
					.cookie(session.cookie(), csrfCookie)
					.header("X-XSRF-TOKEN", csrfCookie.getValue())
					.with(tenantSession("user-a", "tenant-a")))
				.andExpect(status().isNoContent())
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull())
				.andReturn();

		assertThat(sessionCount()).isEqualTo(sessionsBefore);
		assertThat(cookieHeaders(logout, "__Host-tm_session"))
				.anySatisfy(value -> assertThat(value)
						.startsWith("__Host-tm_session=;")
						.contains("Path=/", "Secure", "HttpOnly", "SameSite=Lax", "Max-Age=0"));
		assertThat(cookieHeaders(logout, "XSRF-TOKEN"))
				.anySatisfy(value -> assertThat(value)
						.doesNotStartWith("XSRF-TOKEN=;")
						.contains("Path=/", "Secure", "SameSite=Lax")
						.doesNotContain("HttpOnly"));
	}

	private int sessionCount() {
		return jdbcTemplate.queryForObject("select count(*) from spring_session", Integer.class);
	}

	private PersistedSession createPersistedSession() throws Exception {
		MvcResult result = mockMvc.perform(get("/test/session-probe"))
				.andExpect(status().isOk())
				.andReturn();
		return new PersistedSession(
				requiredCookie(result, "__Host-tm_session"),
				Instant.ofEpochMilli(Long.parseLong(result.getResponse().getContentAsString())));
	}

	private record PersistedSession(Cookie cookie, Instant createdAt) {
	}

	private static String cookieHeader(MvcResult result, String name) {
		return cookieHeaders(result, name).stream()
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing cookie " + name));
	}

	private static List<String> cookieHeaders(MvcResult result, String name) {
		return result.getResponse().getHeaders("Set-Cookie").stream()
				.filter(value -> value.startsWith(name + "="))
				.toList();
	}

	private static Cookie requiredCookie(MvcResult result, String name) {
		Cookie cookie = result.getResponse().getCookie(name);
		if (cookie == null) {
			throw new AssertionError("Missing cookie " + name);
		}
		return cookie;
	}

	private RequestPostProcessor tenantSession(String subject, String tenantId) {
		String issuer = "https://issuer.test/browser-session";
		UUID userId = UUID.nameUUIDFromBytes((issuer + "|" + subject).getBytes(StandardCharsets.UTF_8));
		jdbcTemplate.update(
				"""
						INSERT INTO tenants (tenant_id, display_name, enabled)
						VALUES (?, ?, TRUE)
						ON CONFLICT (tenant_id) DO UPDATE SET enabled = TRUE
						""",
				tenantId,
				tenantId);
		jdbcTemplate.update(
				"""
						INSERT INTO platform_users (user_id, oidc_issuer, oidc_subject, enabled)
						VALUES (?, ?, ?, TRUE)
						ON CONFLICT (oidc_issuer, oidc_subject) DO UPDATE SET enabled = TRUE
						""",
				userId,
				issuer,
				subject);
		jdbcTemplate.update(
				"""
						INSERT INTO tenant_memberships (user_id, tenant_id, role, enabled)
						VALUES (?, ?, 'MEMBER', TRUE)
						ON CONFLICT (user_id, tenant_id) DO UPDATE SET enabled = TRUE
						""",
				userId,
				tenantId);
		TenantSessionPrincipal principal = membershipResolver.resolve(issuer, subject).orElseThrow();
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				principal, "N/A", List.of());
		return authentication(authentication);
	}

	private static String batchJson(String eventId) {
		return """
				{
				  "events": [
				    {
				      "event_id": "%s",
				      "account_external_id": "acct-session",
				      "event_type": "billing.invoice_paid",
				      "occurred_at": "2026-08-23T12:00:00Z",
				      "schema_version": 1
				    }
				  ]
				}
				""".formatted(eventId);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class SessionProbeConfiguration {

		@Bean
		@Order(0)
		SecurityFilterChain sessionProbeSecurityFilterChain(HttpSecurity http) throws Exception {
			http
					.securityMatcher("/test/session-probe")
					.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
					.csrf(AbstractHttpConfigurer::disable)
					.requestCache(cache -> cache.disable());
			return http.build();
		}

		@Bean
		SessionProbeController sessionProbeController() {
			return new SessionProbeController();
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class AbsoluteLifetimeClockConfiguration {

		@Bean
		AdjustableClock absoluteSessionClock() {
			return new AdjustableClock();
		}
	}

	static final class AdjustableClock extends Clock {

		private Instant instant = Instant.EPOCH;

		void setInstant(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}

	@RestController
	static class SessionProbeController {

		@GetMapping("/test/session-probe")
		long createSession(HttpSession session) {
			session.setAttribute("probe", "persisted");
			return session.getCreationTime();
		}
	}
}
