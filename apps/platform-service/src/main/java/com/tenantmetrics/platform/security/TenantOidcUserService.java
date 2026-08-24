package com.tenantmetrics.platform.security;

import java.net.URL;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class TenantOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	private static final String ACCESS_DENIED = "access_denied";

	private final TenantMembershipResolver membershipResolver;
	private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

	@Autowired
	public TenantOidcUserService(TenantMembershipResolver membershipResolver) {
		this(membershipResolver, new OidcUserService());
	}

	public TenantOidcUserService(
			TenantMembershipResolver membershipResolver,
			OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
		this.membershipResolver = Objects.requireNonNull(membershipResolver);
		this.delegate = Objects.requireNonNull(delegate);
	}

	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
		OidcUser oidcUser = delegate.loadUser(userRequest);
		URL issuer = oidcUser.getIssuer();
		String subject = oidcUser.getSubject();
		if (issuer == null || subject == null || subject.isBlank()) {
			throw unauthorizedIdentity();
		}

		TenantSessionPrincipal membership = membershipResolver
				.resolve(issuer.toExternalForm(), subject)
				.orElseThrow(TenantOidcUserService::unauthorizedIdentity);
		return new TenantOidcUser(membership, oidcUser);
	}

	private static OAuth2AuthenticationException unauthorizedIdentity() {
		return new OAuth2AuthenticationException(
				new OAuth2Error(ACCESS_DENIED),
				"OIDC identity is not authorized");
	}
}
