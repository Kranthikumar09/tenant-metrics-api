package com.tenantmetrics.platform;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
@Import(BrowserSessionSecurityTests.SessionProbeConfiguration.class)
class BrowserSessionSecurityTests extends AbstractPlatformPostgresTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
		Cookie csrfCookie = requiredCookie(session, "XSRF-TOKEN");

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

	private int sessionCount() {
		return jdbcTemplate.queryForObject("select count(*) from spring_session", Integer.class);
	}

	private static String cookieHeader(MvcResult result, String name) {
		return result.getResponse().getHeaders("Set-Cookie").stream()
				.filter(value -> value.startsWith(name + "="))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Missing cookie " + name));
	}

	private static Cookie requiredCookie(MvcResult result, String name) {
		Cookie cookie = result.getResponse().getCookie(name);
		if (cookie == null) {
			throw new AssertionError("Missing cookie " + name);
		}
		return cookie;
	}

	private static RequestPostProcessor tenantSession(String subject, String tenantId) {
		Authentication authentication = new UsernamePasswordAuthenticationToken(
				new TenantSessionPrincipal(subject, tenantId), "N/A", List.of());
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
		SessionProbeController sessionProbeController() {
			return new SessionProbeController();
		}
	}

	@RestController
	static class SessionProbeController {

		@GetMapping("/test/session-probe")
		String createSession(HttpSession session, CsrfToken csrfToken) {
			session.setAttribute("probe", "persisted");
			csrfToken.getToken();
			return "ok";
		}
	}
}
