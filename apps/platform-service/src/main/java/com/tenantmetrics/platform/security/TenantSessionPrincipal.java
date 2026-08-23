package com.tenantmetrics.platform.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

public final class TenantSessionPrincipal implements Principal, Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private final UUID userId;
	private final String issuer;
	private final String subject;
	private final String tenantId;

	private TenantSessionPrincipal(UUID userId, String issuer, String subject, String tenantId) {
		this.userId = Objects.requireNonNull(userId, "userId is required");
		this.issuer = requireText(issuer, "issuer");
		this.subject = requireText(subject, "subject");
		this.tenantId = requireText(tenantId, "tenantId");
	}

	static TenantSessionPrincipal fromVerifiedMembership(
			UUID userId,
			String issuer,
			String subject,
			String tenantId) {
		return new TenantSessionPrincipal(userId, issuer, subject, tenantId);
	}

	public UUID userId() {
		return userId;
	}

	public String issuer() {
		return issuer;
	}

	public String subject() {
		return subject;
	}

	public String tenantId() {
		return tenantId;
	}

	@Override
	public String getName() {
		return userId.toString();
	}

	private static String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " is required");
		}
		return value;
	}
}
