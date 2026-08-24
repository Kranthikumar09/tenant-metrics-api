package com.tenantmetrics.platform.security;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

final class AbsoluteSessionLifetimeFilter extends OncePerRequestFilter {

	private static final String API_KEY_HEADER = "X-Api-Key";

	private final Duration absoluteLifetime;
	private final Clock clock;

	AbsoluteSessionLifetimeFilter(Duration absoluteLifetime, Clock clock) {
		this.absoluteLifetime = absoluteLifetime;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session == null || !isExpired(session)) {
			filterChain.doFilter(request, response);
			return;
		}

		session.invalidate();
		if (isBrowserApiRequest(request)) {
			PlatformSecurityConfiguration.writeProblem(
					response, HttpStatus.UNAUTHORIZED, "Browser session has expired");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private boolean isExpired(HttpSession session) {
		Instant createdAt = Instant.ofEpochMilli(session.getCreationTime());
		return !clock.instant().isBefore(createdAt.plus(absoluteLifetime));
	}

	private static boolean isBrowserApiRequest(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return path.startsWith("/v1/")
				&& !StringUtils.hasText(request.getHeader(API_KEY_HEADER));
	}
}
