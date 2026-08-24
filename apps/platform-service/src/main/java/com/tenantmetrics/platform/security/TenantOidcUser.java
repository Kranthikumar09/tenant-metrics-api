package com.tenantmetrics.platform.security;

import java.io.Serial;
import java.util.UUID;

import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public final class TenantOidcUser extends DefaultOidcUser {

	@Serial
	private static final long serialVersionUID = 1L;

	private final TenantSessionPrincipal membership;

	TenantOidcUser(TenantSessionPrincipal membership, OidcUser oidcUser) {
		super(oidcUser.getAuthorities(), oidcUser.getIdToken(), oidcUser.getUserInfo(), IdTokenClaimNames.SUB);
		this.membership = membership;
	}

	public UUID userId() {
		return membership.userId();
	}

	public String tenantId() {
		return membership.tenantId();
	}

	@Override
	public String getName() {
		return membership.getName();
	}
}
