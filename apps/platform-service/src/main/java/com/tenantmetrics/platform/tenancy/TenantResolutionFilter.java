package com.tenantmetrics.platform.tenancy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
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
		if (rawKey == null || rawKey.isBlank()) {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			return;
		}
		String tenantId = apiKeyProperties.getApiKeyHashes().get(sha256(rawKey));
		if (tenantId == null || tenantId.isBlank()) {
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			return;
		}
		stripped.setAttribute(TENANT_CONTEXT_ATTRIBUTE, new TenantContext(tenantId));
		filterChain.doFilter(stripped, response);
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
