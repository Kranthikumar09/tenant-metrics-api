package com.tenantmetrics.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared representation of a tenant in the multi-tenant B2B platform.
 *
 * <p>Implemented as a Java record so it is immutable and safe to share across
 * service boundaries. Bean Validation constraints allow the consuming services
 * to validate inbound payloads without redefining the contract.
 */
public record TenantDto(
		@NotNull UUID id,
		@NotBlank String name,
		@NotBlank String slug,
		@NotNull TenantStatus status,
		Instant createdAt
) {

	/** Lifecycle state of a tenant account. */
	public enum TenantStatus {
		ACTIVE,
		SUSPENDED,
		PENDING
	}
}
