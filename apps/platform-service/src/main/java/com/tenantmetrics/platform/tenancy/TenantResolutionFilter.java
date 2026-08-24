package com.tenantmetrics.platform.tenancy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tenantmetrics.platform.security.TenantSessionPrincipal;
import com.tenantmetrics.platform.security.TenantOidcUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TenantResolutionFilter extends OncePerRequestFilter {

	public static final String TENANT_CONTEXT_ATTRIBUTE = TenantContext.class.getName();

	private final ApiKeyProperties apiKeyProperties;

	public TenantResolutionFilter(ApiKeyProperties apiKeyProperties) {
		this.apiKeyProperties = apiKeyProperties;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		HttpServletRequest stripped = new TenantHeaderStrippingRequest(request);
		if (!stripped.getRequestURI().startsWith("/v1/")) {
			filterChain.doFilter(stripped, response);
			return;
		}
		String rawKey = stripped.getHeader("X-Api-Key");
		if (rawKey != null && !rawKey.isBlank()) {
			String tenantId = apiKeyProperties.getApiKeyHashes().get(sha256(rawKey));
			if (tenantId == null || tenantId.isBlank()) {
				writeUnauthorized(response);
				return;
			}
			continueWithTenant(stripped, response, filterChain, tenantId);
			return;
		}

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			writeUnauthorized(response);
			return;
		}
		String tenantId = resolvedTenantId(authentication.getPrincipal());
		if (tenantId == null) {
			writeUnauthorized(response);
			return;
		}
		continueWithTenant(stripped, response, filterChain, tenantId);
	}

	private static String resolvedTenantId(Object principal) {
		if (principal instanceof TenantSessionPrincipal sessionPrincipal) {
			return sessionPrincipal.tenantId();
		}
		if (principal instanceof TenantOidcUser oidcUser) {
			return oidcUser.tenantId();
		}
		return null;
	}

	private static void continueWithTenant(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain, String tenantId) throws ServletException, IOException {
		request.setAttribute(TENANT_CONTEXT_ATTRIBUTE, new TenantContext(tenantId));
		filterChain.doFilter(request, response);
	}

	private static void writeUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("""
				{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Authentication is required"}
				""");
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is required", ex);
		}
	}
}
