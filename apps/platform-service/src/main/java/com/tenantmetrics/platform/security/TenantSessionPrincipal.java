package com.tenantmetrics.platform.security;

import java.io.Serializable;
import java.security.Principal;

public record TenantSessionPrincipal(String subject, String tenantId) implements Principal, Serializable {

	public TenantSessionPrincipal {
		if (subject == null || subject.isBlank()) {
			throw new IllegalArgumentException("subject is required");
		}
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("tenantId is required");
		}
	}

	@Override
	public String getName() {
		return subject;
	}
}
