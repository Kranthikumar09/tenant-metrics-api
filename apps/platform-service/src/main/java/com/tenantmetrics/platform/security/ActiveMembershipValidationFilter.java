package com.tenantmetrics.platform.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

final class ActiveMembershipValidationFilter extends OncePerRequestFilter {

	private static final String API_KEY_HEADER = "X-Api-Key";

	private final TenantMembershipResolver membershipResolver;

	ActiveMembershipValidationFilter(TenantMembershipResolver membershipResolver) {
		this.membershipResolver = membershipResolver;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !path.startsWith("/v1/") || StringUtils.hasText(request.getHeader(API_KEY_HEADER));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		TenantSessionPrincipal sessionPrincipal = authentication == null || !authentication.isAuthenticated()
				? null : sessionPrincipal(authentication.getPrincipal());
		if (sessionPrincipal == null || isStillAuthorized(sessionPrincipal)) {
			filterChain.doFilter(request, response);
			return;
		}

		HttpSession session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		PlatformSecurityConfiguration.writeProblem(
				response, HttpStatus.UNAUTHORIZED, "Authentication is required");
	}

	private boolean isStillAuthorized(TenantSessionPrincipal sessionPrincipal) {
		return membershipResolver.resolve(sessionPrincipal.issuer(), sessionPrincipal.subject())
				.filter(current -> current.userId().equals(sessionPrincipal.userId()))
				.filter(current -> current.tenantId().equals(sessionPrincipal.tenantId()))
				.isPresent();
	}

	private static TenantSessionPrincipal sessionPrincipal(Object principal) {
		if (principal instanceof TenantSessionPrincipal sessionPrincipal) {
			return sessionPrincipal;
		}
		if (principal instanceof TenantOidcUser oidcUser) {
			return oidcUser.membership();
		}
		return null;
	}
}
