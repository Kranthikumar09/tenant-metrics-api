package com.tenantmetrics.platform.security;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TenantMembershipResolver {

	private final JdbcTemplate jdbcTemplate;

	TenantMembershipResolver(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<TenantSessionPrincipal> resolve(String issuer, String subject) {
		if (issuer == null || issuer.isBlank() || issuer.length() > 512
				|| subject == null || subject.isBlank() || subject.length() > 255) {
			return Optional.empty();
		}
		var memberships = jdbcTemplate.query(
				"""
						SELECT u.user_id, u.oidc_issuer, u.oidc_subject, m.tenant_id
						FROM platform_users u
						JOIN tenant_memberships m ON m.user_id = u.user_id
						JOIN tenants t ON t.tenant_id = m.tenant_id
						WHERE u.oidc_issuer = ?
						  AND u.oidc_subject = ?
						  AND u.enabled
						  AND m.enabled
						  AND t.enabled
						ORDER BY m.tenant_id
						LIMIT 2
						""",
				(rs, rowNum) -> TenantSessionPrincipal.fromVerifiedMembership(
						rs.getObject("user_id", java.util.UUID.class),
						rs.getString("oidc_issuer"),
						rs.getString("oidc_subject"),
						rs.getString("tenant_id")),
				issuer,
				subject);
		return memberships.size() == 1 ? Optional.of(memberships.getFirst()) : Optional.empty();
	}
}
