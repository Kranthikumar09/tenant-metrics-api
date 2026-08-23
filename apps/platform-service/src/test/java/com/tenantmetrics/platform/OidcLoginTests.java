package com.tenantmetrics.platform;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.tenantmetrics.platform.security.TenantMembershipResolver;
import com.tenantmetrics.platform.security.TenantOidcUser;
import com.tenantmetrics.platform.security.TenantOidcUserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(OidcLoginTests.OidcTestConfiguration.class)
class OidcLoginTests extends AbstractPlatformPostgresTest {

	private static final String ISSUER = "https://issuer.test";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TenantMembershipResolver membershipResolver;

	@Test
	void loginInitiationUsesAuthorizationCodeOidcStateNonceAndS256Pkce() throws Exception {
		MvcResult result = mockMvc.perform(get("/oauth2/authorization/test-oidc"))
				.andExpect(status().is3xxRedirection())
				.andReturn();

		URI redirect = URI.create(result.getResponse().getRedirectedUrl());
		MultiValueMap<String, String> query = UriComponentsBuilder.fromUri(redirect).build().getQueryParams();
		assertThat(redirect.getHost()).isEqualTo("issuer.test");
		assertThat(query.getFirst("response_type")).isEqualTo("code");
		assertThat(query.getFirst("scope")).contains("openid");
		assertThat(query.getFirst("state")).isNotBlank();
		assertThat(query.getFirst("nonce")).isNotBlank();
		assertThat(query.getFirst("code_challenge")).isNotBlank();
		assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
		assertThat(query).doesNotContainKey("client_secret");
		assertThat(result.getRequest().getSession(false)).isNotNull();
	}

	@Test
	void invalidStateFailsWithJsonUnauthorizedBeforeAnyCodeExchange() throws Exception {
		MvcResult initiated = mockMvc.perform(get("/oauth2/authorization/test-oidc"))
				.andExpect(status().is3xxRedirection())
				.andReturn();
		MockHttpSession session = (MockHttpSession) initiated.getRequest().getSession(false);

		mockMvc.perform(get("/login/oauth2/code/test-oidc")
					.session(session)
					.queryParam("code", "must-not-be-exchanged")
					.queryParam("state", "invalid-state"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(result -> assertThat(result.getResponse().getRedirectedUrl()).isNull());
	}

	@Test
	void verifiedOidcIdentityMapsToMembershipAndIgnoresTenantClaim() throws Exception {
		String subject = "mapped-user";
		String tenantId = "tenant-membership-authority";
		UUID userId = seedMembership(subject, tenantId);
		TenantOidcUser user = loadUser(subject, "tenant-forged-claim");

		assertThat(user.getName()).isEqualTo(userId.toString());
		assertThat(user.tenantId()).isEqualTo(tenantId);
		assertThat(user.getClaimAsString("tenant_id")).isEqualTo("tenant-forged-claim");

		Authentication authenticated = new OAuth2AuthenticationToken(
				user, user.getAuthorities(), "test-oidc");
		mockMvc.perform(get("/v1/tenant-context")
					.with(authentication(authenticated))
					.header("X-Tenant-ID", "tenant-forged-header")
					.queryParam("tenantId", "tenant-forged-query"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value(tenantId));
	}

	@Test
	void unmappedOidcIdentityFailsClosedWithoutLeakingTheSubject() {
		String subject = "unmapped-sensitive-subject";

		assertThatExceptionOfType(OAuth2AuthenticationException.class)
				.isThrownBy(() -> loadUser(subject, "tenant-forged-claim"))
				.withMessageNotContaining(subject);
	}

	private TenantOidcUser loadUser(String subject, String tenantClaim) {
		OidcIdToken idToken = idToken(subject, tenantClaim);
		OidcUser delegateUser = new DefaultOidcUser(List.of(), idToken);
		OAuth2UserService<OidcUserRequest, OidcUser> delegate = ignored -> delegateUser;
		TenantOidcUserService service = new TenantOidcUserService(membershipResolver, delegate);
		return (TenantOidcUser) service.loadUser(userRequest(idToken));
	}

	private UUID seedMembership(String subject, String tenantId) {
		UUID userId = UUID.nameUUIDFromBytes((ISSUER + "|" + subject).getBytes(StandardCharsets.UTF_8));
		jdbcTemplate.update(
				"INSERT INTO tenants (tenant_id, display_name, enabled) VALUES (?, ?, TRUE)",
				tenantId,
				tenantId);
		jdbcTemplate.update(
				"INSERT INTO platform_users (user_id, oidc_issuer, oidc_subject, enabled) VALUES (?, ?, ?, TRUE)",
				userId,
				ISSUER,
				subject);
		jdbcTemplate.update(
				"INSERT INTO tenant_memberships (user_id, tenant_id, role, enabled) VALUES (?, ?, 'MEMBER', TRUE)",
				userId,
				tenantId);
		return userId;
	}

	private static OidcUserRequest userRequest(OidcIdToken idToken) {
		Instant now = Instant.now();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(
				OAuth2AccessToken.TokenType.BEARER,
				"server-side-test-access-token",
				now,
				now.plusSeconds(300),
				Set.of("openid", "profile"));
		return new OidcUserRequest(clientRegistration(), accessToken, idToken);
	}

	private static OidcIdToken idToken(String subject, String tenantClaim) {
		Instant now = Instant.now();
		return new OidcIdToken(
				"server-side-test-id-token",
				now,
				now.plusSeconds(300),
				Map.of(
						"iss", ISSUER,
						"sub", subject,
						"aud", List.of("test-client"),
						"nonce", "test-nonce",
						"tenant_id", tenantClaim));
	}

	private static ClientRegistration clientRegistration() {
		return ClientRegistration.withRegistrationId("test-oidc")
				.clientId("test-client")
				.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("openid", "profile")
				.authorizationUri(ISSUER + "/oauth2/authorize")
				.tokenUri(ISSUER + "/oauth2/token")
				.jwkSetUri(ISSUER + "/oauth2/jwks")
				.issuerUri(ISSUER)
				.userNameAttributeName("sub")
				.clientName("Test OIDC")
				.build();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class OidcTestConfiguration {

		@Bean
		ClientRegistrationRepository testClientRegistrationRepository() {
			return new InMemoryClientRegistrationRepository(clientRegistration());
		}
	}
}
