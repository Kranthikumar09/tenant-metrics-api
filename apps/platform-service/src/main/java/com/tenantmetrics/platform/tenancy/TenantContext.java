package com.tenantmetrics.platform.tenancy;

public record TenantContext(String tenantId) {

	public TenantContext {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("tenantId is required");
		}
	}
}
