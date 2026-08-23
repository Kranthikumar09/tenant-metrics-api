package com.tenantmetrics.platform.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.util.StringUtils;

import com.tenantmetrics.platform.tenancy.ApiKeyProperties;
import com.tenantmetrics.platform.tenancy.TenantResolutionFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration(proxyBeanMethods = false)
public class PlatformSecurityConfiguration {

	@Bean
	SecurityFilterChain platformSecurityFilterChain(HttpSecurity http, ApiKeyProperties apiKeyProperties)
			throws Exception {
		TenantResolutionFilter tenantResolutionFilter = new TenantResolutionFilter(apiKeyProperties);
		CookieCsrfTokenRepository csrfRepository = new CookieCsrfTokenRepository();
		csrfRepository.setCookieName("XSRF-TOKEN");
		csrfRepository.setHeaderName("X-XSRF-TOKEN");
		csrfRepository.setCookieCustomizer(cookie -> cookie.path("/")
				.secure(true)
				.httpOnly(false)
				.sameSite("Lax"));

		RequestMatcher apiKeyAuthenticationSelected = request ->
				StringUtils.hasText(request.getHeader("X-Api-Key"));

		http
				.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfRepository)
						.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
						.ignoringRequestMatchers(apiKeyAuthenticationSelected))
				.requestCache(cache -> cache.disable())
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint((request, response, exception) ->
								writeProblem(response, HttpStatus.UNAUTHORIZED, "Authentication is required"))
						.accessDeniedHandler((request, response, exception) ->
								writeProblem(response, HttpStatus.FORBIDDEN, "Access is denied")))
				.addFilterBefore(tenantResolutionFilter, CsrfFilter.class);
		return http.build();
	}

	@Bean
	CookieSerializer sessionCookieSerializer() {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName("__Host-tm_session");
		serializer.setCookiePath("/");
		serializer.setUseSecureCookie(true);
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		return serializer;
	}

	private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("""
				{"type":"about:blank","title":"%s","status":%d,"detail":"%s"}
				""".formatted(status.getReasonPhrase(), status.value(), detail));
	}

	private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

		private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
		private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

		@Override
		public void handle(HttpServletRequest request, HttpServletResponse response,
				Supplier<CsrfToken> csrfToken) {
			xor.handle(request, response, csrfToken);
			csrfToken.get();
		}

		@Override
		public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
			CsrfTokenRequestHandler handler = StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))
					? plain : xor;
			return handler.resolveCsrfTokenValue(request, csrfToken);
		}
	}
}
