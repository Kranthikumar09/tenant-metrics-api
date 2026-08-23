package com.tenantmetrics.platform.security;

import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class TenantMembershipResolver {

	public Optional<TenantSessionPrincipal> resolve(String issuer, String subject) {
		return Optional.empty();
	}
}
